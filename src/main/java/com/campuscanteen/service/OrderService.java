package com.campuscanteen.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.campuscanteen.model.Canteen;
import com.campuscanteen.model.DailyMenu;
import com.campuscanteen.model.MealSession;
import com.campuscanteen.model.Order;
import com.campuscanteen.model.OrderItem;
import com.campuscanteen.model.OrderStatus;
import com.campuscanteen.model.Payment;
import com.campuscanteen.model.PaymentMethod;
import com.campuscanteen.model.PaymentStatus;
import com.campuscanteen.model.StatusChange;
import com.campuscanteen.model.User;
import com.campuscanteen.repository.OrderRepository;
import com.campuscanteen.repository.PaymentRepository;
import com.campuscanteen.repository.UserRepository;

/**
 * Placing orders and reading a customer's own orders.
 *
 * Placing an order happens the moment the customer chooses Cash or QR Code on the checkout page:
 * 1. If this checkout was already submitted (double click, refresh, back button), the existing
 *    order is returned and nothing new is created.
 * 2. Checks that a meal session is open and its menu is published.
 * 3. Recalculates prices and the total from the menu on the server (the browser's total is never used).
 * 4. Reserves stock for every item atomically (see DailyMenuService.reserveStock). If any item
 *    runs out, the portions already reserved are given back and the order is refused.
 * 5. Takes the next daily token (atomic counter) and saves the order as QUEUED with its
 *    queue-entry time, so it is in the FIFO queue immediately, for BOTH payment methods.
 *    Payment status starts as CASH_DUE or QR_PAYMENT_PENDING; it never counts as paid until an
 *    administrator records the cash or verifies the QR payment.
 * 6. Saves the payment record.
 *
 * The local MongoDB server is a single server (not a replica set), so multi-document transactions
 * are not available. Every step is a single atomic document operation instead, and if a later step
 * fails the earlier ones are undone (stock returned, half-saved order marked cancelled).
 */
@Service
public class OrderService {

    /** A customer may have at most this many unpaid orders in the queue at the same time. */
    static final int MAX_UNPAID_ORDERS = 3;

    private static final String ORDER_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final CanteenService canteenService;
    private final MealSessionService mealSessionService;
    private final DailyMenuService dailyMenuService;
    private final TokenService tokenService;
    private final FileStorageService fileStorage;
    private final MongoTemplate mongo;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public OrderService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                        UserRepository userRepository, CanteenService canteenService,
                        MealSessionService mealSessionService, DailyMenuService dailyMenuService,
                        TokenService tokenService, FileStorageService fileStorage, MongoTemplate mongo, Clock clock) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.canteenService = canteenService;
        this.mealSessionService = mealSessionService;
        this.dailyMenuService = dailyMenuService;
        this.tokenService = tokenService;
        this.fileStorage = fileStorage;
        this.mongo = mongo;
        this.clock = clock;
    }

    /**
     * Places the order and puts it in the queue. checkoutKey is the random key from the checkout
     * page; submitting the same key again returns the same order instead of creating a new one.
     */
    public Order placeOrder(String userId, Cart cart, PaymentMethod method, String checkoutKey) {
        User user = userRepository.findById(userId)
                .filter(u -> u.isActive() && u.getRole().isCustomer())
                .orElseThrow(() -> new BusinessException("Your account cannot place orders."));
        if (checkoutKey == null || !checkoutKey.matches("[A-Za-z0-9-]{8,64}")) {
            throw new BusinessException("This checkout page has expired. Please open the checkout again.");
        }
        Optional<Order> alreadyPlaced = orderRepository.findByUserIdAndCheckoutKey(userId, checkoutKey);
        if (alreadyPlaced.isPresent()) {
            cart.clear();
            return alreadyPlaced.get();
        }
        if (method == null) {
            throw new BusinessException("Choose Cash or QR Code.");
        }
        if (cart.isEmpty()) {
            throw new BusinessException("Your cart is empty.");
        }
        if (countUnpaidOrdersInQueue(userId) >= MAX_UNPAID_ORDERS) {
            throw new BusinessException("You already have " + MAX_UNPAID_ORDERS
                    + " unpaid orders in the queue. Please pay for or collect them first.");
        }

        Canteen canteen = canteenService.getDefaultCanteen();
        if (method == PaymentMethod.QR && !canteen.isQrPaymentAvailable()) {
            throw new BusinessException("QR payment has not been set up by the canteen yet. Please choose Cash.");
        }
        MealSession session = mealSessionService.findOpenSession(canteen)
                .orElseThrow(() -> new BusinessException("Ordering is closed right now. No meal session is open."));
        DailyMenu menu = dailyMenuService.findPublishedMenu(canteen.getId(), session)
                .orElseThrow(() -> new BusinessException("The menu for this meal session has not been published yet."));
        if (!menu.getId().equals(cart.getDailyMenuId())) {
            // The meal session changed while the customer was filling the cart: check every item again
            // against the new menu and ask the customer to review the cart before ordering.
            Cart.Change change = cart.attachTo(menu);
            throw new BusinessException(change == null
                    ? "The menu has changed. Please check your cart again."
                    : change.message() + " Please check your cart and place the order again.");
        }
        CartView view = CartView.calculate(menu, cart);
        if (!view.isReadyForCheckout()) {
            throw new BusinessException(view.getProblems().isEmpty() ? "Your cart is empty." : view.getProblems().get(0));
        }

        // Reserve stock item by item; undo on the first failure.
        List<CartView.Line> reserved = new ArrayList<>();
        for (CartView.Line line : view.getLines()) {
            if (!dailyMenuService.reserveStock(menu.getId(), line.getMenuItemId(), line.getQuantity())) {
                releaseAll(menu.getId(), reserved);
                throw new BusinessException("Sorry, there is not enough " + line.getName()
                        + " left. Please update your cart.");
            }
            reserved.add(line);
        }

        Order saved = null;
        try {
            Instant now = Instant.now(clock);
            String today = LocalDate.now(clock).toString();
            int token = tokenService.nextToken(canteen.getId(), today);
            PaymentStatus initialPayment = method == PaymentMethod.CASH
                    ? PaymentStatus.CASH_DUE : PaymentStatus.QR_PAYMENT_PENDING;

            Order order = new Order();
            order.setId(new ObjectId().toHexString());
            order.setUserId(user.getId());
            order.setCustomerName(user.getName());
            order.setCollegeId(user.getCollegeId());
            order.setRole(user.getRole());
            order.setCanteenId(canteen.getId());
            order.setMealSessionId(session.getId());
            order.setMealSessionName(session.getName());
            order.setDailyMenuId(menu.getId());
            order.setOrderDate(today);
            for (CartView.Line line : view.getLines()) {
                order.getItems().add(new OrderItem(line.getMenuItemId(), line.getName(), line.getQuantity(), line.getUnitPrice()));
            }
            order.setTotalAmount(view.getTotal());
            order.setPaymentMethod(method);
            order.setPaymentStatus(initialPayment);
            order.setOrderStatus(OrderStatus.QUEUED);
            order.setTokenNumber(token);
            order.setTokenDate(today);
            order.setQueueEnteredAt(now);
            order.setCheckoutKey(checkoutKey);
            order.setCreatedAt(now);
            order.setUpdatedAt(now);
            order.getStatusHistory().add(new StatusChange(OrderStatus.QUEUED, now, user.getCollegeId(),
                    "Order placed with " + method.getLabel() + ". Token " + String.format("%03d", token)
                            + " issued and order added to the queue."));

            saved = insertWithUniqueNumber(order);
            if (saved == null) {
                // The same checkout was submitted twice at the same moment and the other request won.
                releaseAll(menu.getId(), reserved);
                cart.clear();
                return orderRepository.findByUserIdAndCheckoutKey(userId, checkoutKey).orElseThrow();
            }

            Payment payment = new Payment();
            payment.setOrderId(saved.getId());
            payment.setOrderNumber(saved.getOrderNumber());
            payment.setUserId(user.getId());
            payment.setAmount(view.getTotal());
            payment.setPaymentMethod(method);
            payment.setStatus(initialPayment);
            payment.setCreatedAt(now);
            paymentRepository.insert(payment);
            cart.clear();
            return saved;
        } catch (RuntimeException e) {
            // Something failed after the stock was reserved: give the stock back and tidy up.
            releaseAll(menu.getId(), reserved);
            if (saved != null) {
                // Orders are never deleted; mark the half-saved order as cancelled instead.
                Instant now = Instant.now(clock);
                mongo.updateFirst(new Query(Criteria.where("id").is(saved.getId())),
                        new Update()
                                .set("orderStatus", OrderStatus.CANCELLED)
                                .set("statusNote", "Could not save the payment details. Please place the order again.")
                                .set("updatedAt", now)
                                .push("statusHistory", new StatusChange(OrderStatus.CANCELLED, now, "SYSTEM",
                                        "Payment record could not be saved")),
                        Order.class);
            }
            throw e;
        }
    }

    private void releaseAll(String dailyMenuId, List<CartView.Line> reserved) {
        reserved.forEach(r -> dailyMenuService.releaseStock(dailyMenuId, r.getMenuItemId(), r.getQuantity()));
    }

    private long countUnpaidOrdersInQueue(String userId) {
        return mongo.count(new Query(Criteria.where("userId").is(userId)
                .and("orderStatus").in(OrderStatus.ACTIVE_QUEUE)
                .and("paymentStatus").in(PaymentStatus.UNPAID)), Order.class);
    }

    /**
     * Order numbers look like 261013-K7M2Q (date + random code). The unique index guarantees no repeats.
     * Returns null if an order with the same checkout key already exists (duplicate submission).
     */
    private Order insertWithUniqueNumber(Order order) {
        String datePart = LocalDate.now(clock).format(ORDER_DATE);
        for (int attempt = 0; ; attempt++) {
            StringBuilder code = new StringBuilder(datePart).append('-');
            for (int i = 0; i < 5; i++) {
                code.append(ORDER_CODE_CHARS.charAt(random.nextInt(ORDER_CODE_CHARS.length())));
            }
            order.setOrderNumber(code.toString());
            try {
                return orderRepository.insert(order);
            } catch (DuplicateKeyException e) {
                if (orderRepository.findByUserIdAndCheckoutKey(order.getUserId(), order.getCheckoutKey()).isPresent()) {
                    return null;
                }
                if (attempt >= 4) {
                    throw e;
                }
            }
        }
    }

    // ---------- QR payment proof ----------

    /**
     * The customer uploads a screenshot of their UPI payment for a QR order that is already in the queue.
     * This does NOT mark the order as paid: an administrator still has to verify it.
     * The screenshot can be replaced until the payment has been checked.
     */
    public void submitPaymentProof(String orderId, String userId, MultipartFile proof, String transactionRef) {
        Order order = getForCustomer(orderId, userId);
        if (order.getPaymentMethod() != PaymentMethod.QR) {
            throw new BusinessException("This order is paid in cash at pickup. No screenshot is needed.");
        }
        List<PaymentStatus> open = List.of(PaymentStatus.QR_PAYMENT_PENDING, PaymentStatus.QR_PROOF_SUBMITTED);
        if (!open.contains(order.getPaymentStatus()) || order.getOrderStatus().isFinished()) {
            throw new BusinessException("The payment for this order has already been checked.");
        }
        String reference = transactionRef == null ? "" : transactionRef.trim();
        if (reference.length() > 40 || !reference.matches("[A-Za-z0-9 -]*")) {
            throw new BusinessException("The transaction reference may only contain letters and digits (40 maximum).");
        }
        fileStorage.validateImage(proof);
        String proofFile = fileStorage.store(proof, FileStorageService.PAYMENT_PROOFS);

        Instant now = Instant.now(clock);
        Query stillOpen = new Query(Criteria.where("id").is(orderId).and("paymentStatus").in(open)
                .and("orderStatus").in(OrderStatus.ACTIVE_QUEUE));
        Update submitted = new Update().set("paymentStatus", PaymentStatus.QR_PROOF_SUBMITTED).set("updatedAt", now);
        if (mongo.updateFirst(stillOpen, submitted, Order.class).getModifiedCount() == 0) {
            fileStorage.delete(FileStorageService.PAYMENT_PROOFS, proofFile);
            throw new BusinessException("The payment for this order has already been checked.");
        }
        Update paymentUpdate = new Update()
                .set("status", PaymentStatus.QR_PROOF_SUBMITTED)
                .set("proofFileName", proofFile)
                .set("proofImageUrl", "/files/payment-proof/" + orderId)
                .set("proofSubmittedAt", now)
                .set("transactionRef", reference.isEmpty() ? null : reference);
        // returnNew(false): the record as it was before, so an older screenshot can be removed.
        Payment before = mongo.findAndModify(
                new Query(Criteria.where("orderId").is(orderId).and("status").in(open)),
                paymentUpdate, FindAndModifyOptions.options().returnNew(false), Payment.class);
        if (before == null) {
            // An administrator checked the payment in the meantime; keep their decision.
            fileStorage.delete(FileStorageService.PAYMENT_PROOFS, proofFile);
        } else if (before.getProofFileName() != null) {
            fileStorage.delete(FileStorageService.PAYMENT_PROOFS, before.getProofFileName());
        }
    }

    // ---------- Reading orders ----------

    /** Returns the order only if it belongs to this user; otherwise behaves as if it does not exist. */
    public Order getForCustomer(String orderId, String userId) {
        return orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
    }

    public Order get(String orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new NotFoundException("Order not found"));
    }

    public List<Order> listForCustomer(String userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** How many queued orders entered the queue before this one (0 means it is first). */
    public long countOrdersAhead(Order order) {
        if (!order.getOrderStatus().isInQueue() || order.getOrderStatus() == OrderStatus.READY) {
            return 0;
        }
        Query query = new Query(Criteria.where("canteenId").is(order.getCanteenId())
                .and("orderStatus").in(OrderStatus.QUEUED, OrderStatus.PREPARING)
                .and("queueEnteredAt").lt(order.getQueueEnteredAt()));
        return mongo.count(query, Order.class);
    }

    public List<Order> listForDateAndStatuses(String canteenId, String date, List<OrderStatus> statuses) {
        return orderRepository.findByCanteenIdAndOrderDateAndOrderStatusInOrderByUpdatedAtDesc(canteenId, date, statuses);
    }

    /** Orders still in the queue whose payment has one of these statuses, in queue order. */
    public List<Order> listInQueueWithPaymentStatus(String canteenId, List<PaymentStatus> statuses) {
        Query query = new Query(Criteria.where("canteenId").is(canteenId)
                .and("orderStatus").in(OrderStatus.ACTIVE_QUEUE)
                .and("paymentStatus").in(statuses))
                .with(Sort.by(Sort.Order.asc("queueEnteredAt"), Sort.Order.asc("tokenNumber"), Sort.Order.asc("id")));
        return mongo.find(query, Order.class);
    }

    /** Orders whose QR payment was rejected on this date, newest first. */
    public List<Order> listRejectedPayments(String canteenId, String date) {
        Query query = new Query(Criteria.where("canteenId").is(canteenId).and("orderDate").is(date)
                .and("paymentStatus").is(PaymentStatus.REJECTED))
                .with(Sort.by(Sort.Direction.DESC, "updatedAt"));
        return mongo.find(query, Order.class);
    }
}
