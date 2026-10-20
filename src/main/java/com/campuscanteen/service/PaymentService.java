package com.campuscanteen.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.campuscanteen.model.Order;
import com.campuscanteen.model.OrderItem;
import com.campuscanteen.model.OrderStatus;
import com.campuscanteen.model.Payment;
import com.campuscanteen.model.PaymentMethod;
import com.campuscanteen.model.PaymentStatus;
import com.campuscanteen.model.StatusChange;
import com.campuscanteen.repository.OrderRepository;
import com.campuscanteen.repository.PaymentRepository;

/**
 * Payment checks by a canteen administrator. Orders are already in the queue (with a token)
 * when they are placed, so nothing here adds an order to the queue or issues a token.
 *
 * - Record cash: CASH_DUE -> CASH_COLLECTED, when the customer has actually paid at the counter.
 * - Verify QR:   QR_PAYMENT_PENDING / QR_PROOF_SUBMITTED -> VERIFIED, after the administrator has
 *                checked that the money arrived in the canteen's UPI account.
 * - Reject QR:   the payment is marked REJECTED and the order leaves the queue as REJECTED (it keeps its
 *                token number on record; it is never deleted). Stock is returned if preparation had not started.
 *
 * Each change is ONE conditional update ("only if the payment is still unchecked"), so two
 * administrators clicking at the same moment cannot both succeed.
 * The order document is the source of truth. The payment document is updated right after; if the
 * application stopped in between, reconcilePaymentRecords() fixes the payment record at the next start.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final DailyMenuService dailyMenuService;
    private final MongoTemplate mongo;
    private final Clock clock;

    public PaymentService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                          DailyMenuService dailyMenuService, MongoTemplate mongo, Clock clock) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.dailyMenuService = dailyMenuService;
        this.mongo = mongo;
        this.clock = clock;
    }

    public Payment getForOrder(String orderId) {
        return paymentRepository.findByOrderId(orderId).orElseThrow(() -> new NotFoundException("Payment not found"));
    }

    public Optional<Payment> findForOrder(String orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    /** The customer paid in cash at the counter. */
    public void recordCash(String orderId, String adminCollegeId) {
        Order order = load(orderId);
        if (order.getPaymentMethod() != PaymentMethod.CASH) {
            throw new BusinessException("Order " + order.getOrderNumber() + " is not a cash order.");
        }
        confirm(order, PaymentStatus.CASH_DUE, PaymentStatus.CASH_COLLECTED, adminCollegeId,
                "Cash of " + order.getTotalAmount() + " received");
    }

    /** The administrator has checked the UPI account and the money has arrived. */
    public void verifyQr(String orderId, String adminCollegeId) {
        Order order = load(orderId);
        if (order.getPaymentMethod() != PaymentMethod.QR) {
            throw new BusinessException("Order " + order.getOrderNumber() + " is not a QR payment.");
        }
        confirm(order, null, PaymentStatus.VERIFIED, adminCollegeId, "QR payment verified");
    }

    private void confirm(Order order, PaymentStatus from, PaymentStatus to, String adminCollegeId, String note) {
        Instant now = Instant.now(clock);
        Query unchecked = new Query(Criteria.where("id").is(order.getId())
                .and("orderStatus").in(OrderStatus.ACTIVE_QUEUE)
                .and("paymentStatus").in(from != null ? List.of(from) : PaymentStatus.QR_UNCHECKED));
        Update update = new Update()
                .set("paymentStatus", to)
                .set("paymentConfirmedAt", now)
                .set("paymentConfirmedBy", adminCollegeId)
                .set("updatedAt", now)
                .push("statusHistory", new StatusChange(order.getOrderStatus(), now, adminCollegeId, note));
        if (mongo.updateFirst(unchecked, update, Order.class).getModifiedCount() == 0) {
            throw new BusinessException("The payment for token " + order.getTokenDisplay() + " has already been checked.");
        }
        markPayment(order.getId(), to, adminCollegeId, now, null);
    }

    /**
     * The QR payment could not be found. The order stays in the database (and in the administrator's
     * history) with status REJECTED and its token number; it leaves the active queue.
     */
    public void rejectQr(String orderId, String adminCollegeId, String reason) {
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isEmpty() || cleanReason.length() > 200) {
            throw new BusinessException("Enter a short reason for rejecting the payment (200 characters maximum).");
        }
        Order order = load(orderId);
        if (order.getPaymentMethod() != PaymentMethod.QR) {
            throw new BusinessException("Only QR payments can be rejected. Cancel a cash order instead.");
        }
        Instant now = Instant.now(clock);
        Query unchecked = new Query(Criteria.where("id").is(orderId)
                .and("orderStatus").in(OrderStatus.ACTIVE_QUEUE)
                .and("paymentStatus").in(PaymentStatus.QR_UNCHECKED));
        Update rejectUpdate = new Update()
                .set("orderStatus", OrderStatus.REJECTED)
                .set("paymentStatus", PaymentStatus.REJECTED)
                .set("paymentConfirmedAt", now)
                .set("paymentConfirmedBy", adminCollegeId)
                .set("statusNote", cleanReason)
                .set("updatedAt", now)
                .push("statusHistory", new StatusChange(OrderStatus.REJECTED, now, adminCollegeId,
                        "Payment rejected: " + cleanReason));
        // returnNew(false) gives back the order as it was BEFORE, so we know whether cooking had started.
        Order before = mongo.findAndModify(unchecked, rejectUpdate, FindAndModifyOptions.options().returnNew(false), Order.class);
        if (before == null) {
            throw new BusinessException("The payment for token " + order.getTokenDisplay() + " has already been checked.");
        }
        if (before.getOrderStatus() == OrderStatus.QUEUED) {
            for (OrderItem item : before.getItems()) {
                dailyMenuService.releaseStock(before.getDailyMenuId(), item.getMenuItemId(), item.getQuantity());
            }
        }
        markPayment(orderId, PaymentStatus.REJECTED, adminCollegeId, now, cleanReason);
    }

    private Order load(String orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new NotFoundException("Order not found"));
    }

    private void markPayment(String orderId, PaymentStatus status, String adminCollegeId, Instant when, String reason) {
        Update update = new Update()
                .set("status", status)
                .set("verifiedBy", adminCollegeId)
                .set("verifiedAt", when);
        if (reason != null) {
            update.set("rejectionReason", reason);
        }
        mongo.updateFirst(new Query(Criteria.where("orderId").is(orderId).and("status").in(PaymentStatus.UNPAID)),
                update, Payment.class);
    }

    /** Startup safety check: makes payment records agree with their orders (see class comment). */
    public void reconcilePaymentRecords() {
        Query unpaid = new Query(Criteria.where("status").in(PaymentStatus.UNPAID));
        for (Payment payment : mongo.find(unpaid, Payment.class)) {
            orderRepository.findById(payment.getOrderId())
                    .filter(order -> order.getPaymentStatus() != payment.getStatus()
                            && !PaymentStatus.UNPAID.contains(order.getPaymentStatus()))
                    .ifPresent(order -> {
                        markPayment(order.getId(), order.getPaymentStatus(), order.getPaymentConfirmedBy(),
                                order.getPaymentConfirmedAt() != null ? order.getPaymentConfirmedAt() : order.getUpdatedAt(),
                                order.getPaymentStatus() == PaymentStatus.REJECTED ? order.getStatusNote() : null);
                        log.info("Payment record for order {} updated to match the order.", order.getOrderNumber());
                    });
        }
    }
}
