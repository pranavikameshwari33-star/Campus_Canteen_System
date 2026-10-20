package com.campuscanteen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

import com.campuscanteen.model.DailyMenu;
import com.campuscanteen.model.MenuItem;
import com.campuscanteen.model.Order;
import com.campuscanteen.model.OrderStatus;
import com.campuscanteen.model.Payment;
import com.campuscanteen.model.PaymentMethod;
import com.campuscanteen.model.PaymentStatus;
import com.campuscanteen.model.Role;
import com.campuscanteen.model.StatusChange;
import com.campuscanteen.model.User;
import com.campuscanteen.service.BusinessException;
import com.campuscanteen.service.Cart;
import com.campuscanteen.service.DailyMenuService.SaveAction;
import com.campuscanteen.service.PaymentService;
import com.campuscanteen.service.QueueService;
import com.campuscanteen.service.ReportService;
import com.campuscanteen.service.TokenService;

/**
 * Orders, payment methods, tokens, the FIFO queue, stock and history.
 * Main rule: choosing Cash OR QR Code places the order and puts it in the queue immediately;
 * payment status is tracked separately and only becomes "paid" when an administrator confirms it.
 */
@EnabledIfEnvironmentVariable(named = "MONGODB_URI", matches = ".+")
class OrderQueueTest extends IntegrationTestBase {

    @Autowired PaymentService paymentService;
    @Autowired QueueService queueService;
    @Autowired TokenService tokenService;
    @Autowired ReportService reportService;

    private MenuItem dosa;
    private MenuItem tea;
    private DailyMenu menu;

    @BeforeEach
    void setUpMenu() {
        dosa = menuItem("Dosa", "40");
        tea = menuItem("Tea", "10");
        menu = publishMenu(lunch(), Map.of(dosa, 50, tea, 50));
        admin("ADMIN1");
    }

    private int stockOf(MenuItem item) {
        return dailyMenuService.get(menu.getId()).findItem(item.getId()).orElseThrow().getAvailableQuantity();
    }

    @Test
    void cashOrderEntersQueueImmediatelyWithTokenAndCashDue() {
        User student = customer(Role.STUDENT, "21CS001");
        Order order = placeOrder(student, menu, Map.of(dosa, 2, tea, 1), PaymentMethod.CASH);

        assertEquals(OrderStatus.QUEUED, order.getOrderStatus());
        assertEquals(PaymentMethod.CASH, order.getPaymentMethod());
        assertEquals(PaymentStatus.CASH_DUE, order.getPaymentStatus());
        assertFalse(order.getPaymentStatus().isPaid(), "Choosing Cash must not count as paid");
        assertEquals(0, new BigDecimal("90.00").compareTo(order.getTotalAmount()));   // calculated on the server
        assertEquals(1, order.getTokenNumber());
        assertNotNull(order.getQueueEnteredAt());
        assertEquals(List.of(order.getId()), queueService.activeQueue(canteen.getId()).stream().map(Order::getId).toList());

        Payment payment = paymentService.getForOrder(order.getId());
        assertEquals(PaymentMethod.CASH, payment.getPaymentMethod());
        assertEquals(PaymentStatus.CASH_DUE, payment.getStatus());
        assertEquals(0, new BigDecimal("90.00").compareTo(payment.getAmount()));
        assertEquals(48, stockOf(dosa));
        assertEquals(1, mongo.count(new org.springframework.data.mongodb.core.query.Query(), Order.class));
    }

    @Test
    void qrOrderEntersQueueBeforePaymentIsVerifiedAndKeepsItsToken() throws Exception {
        Order order = placeOrder(customer(Role.STUDENT, "21CS002"), menu, Map.of(dosa, 1), PaymentMethod.QR);

        // In the queue straight away, with a token, while the payment is still pending.
        assertEquals(OrderStatus.QUEUED, order.getOrderStatus());
        assertEquals(PaymentStatus.QR_PAYMENT_PENDING, order.getPaymentStatus());
        assertEquals(1, order.getTokenNumber());
        assertEquals(1, queueService.activeQueue(canteen.getId()).size());

        // Uploading a screenshot does not make it paid.
        submitProof(order);
        Order afterProof = orderService.get(order.getId());
        assertEquals(PaymentStatus.QR_PROOF_SUBMITTED, afterProof.getPaymentStatus());
        assertFalse(afterProof.getPaymentStatus().isPaid());
        assertEquals(1, afterProof.getTokenNumber());
        Payment payment = paymentService.getForOrder(order.getId());
        assertEquals("UTR123", payment.getTransactionRef());
        assertTrue(Files.exists(Path.of("target/test-uploads/payment-proofs", payment.getProofFileName())));

        // Only the administrator's verification marks it paid. Same order, same token, no new queue entry.
        paymentService.verifyQr(order.getId(), "ADMIN1");
        Order verified = orderService.get(order.getId());
        assertEquals(PaymentStatus.VERIFIED, verified.getPaymentStatus());
        assertEquals("ADMIN1", verified.getPaymentConfirmedBy());
        assertEquals(1, verified.getTokenNumber());
        // MongoDB stores times to the millisecond.
        assertEquals(order.getQueueEnteredAt().toEpochMilli(), verified.getQueueEnteredAt().toEpochMilli());
        assertEquals(PaymentStatus.VERIFIED, paymentService.getForOrder(order.getId()).getStatus());
        assertEquals(1, queueService.activeQueue(canteen.getId()).size());
        assertEquals(2, tokenService.nextToken(canteen.getId(), verified.getTokenDate()), "No extra token was used");

        // Verifying again, or uploading another screenshot, is refused.
        assertThrows(BusinessException.class, () -> paymentService.verifyQr(order.getId(), "ADMIN1"));
        assertThrows(BusinessException.class, () -> submitProof(order));
    }

    @Test
    void qrOptionNeedsQrOrUpiConfiguredButCashAlwaysWorks() {
        canteen = canteenService.getDefaultCanteen();
        canteen.setUpiId(null);
        canteenService.save(canteen);
        User student = customer(Role.STUDENT, "21CS003");

        assertThrows(BusinessException.class, () -> placeOrder(student, menu, Map.of(tea, 1), PaymentMethod.QR));
        assertTrue(queueService.activeQueue(canteen.getId()).isEmpty());
        assertEquals(50, stockOf(tea));
        assertEquals(OrderStatus.QUEUED, placeOrder(student, menu, Map.of(tea, 1), PaymentMethod.CASH).getOrderStatus());
    }

    @Test
    void cashRemainsDueUntilAdministratorRecordsIt() {
        Order order = placeOrder(customer(Role.FACULTY, "FAC-004"), menu, Map.of(tea, 2), PaymentMethod.CASH);
        queueService.updateStatus(order.getId(), OrderStatus.READY, "ADMIN1");

        // Food cannot be handed over while the cash is still due.
        assertThrows(BusinessException.class, () -> queueService.updateStatus(order.getId(), OrderStatus.COMPLETED, "ADMIN1"));
        assertEquals(PaymentStatus.CASH_DUE, orderService.get(order.getId()).getPaymentStatus());
        // A cash order cannot be "verified" as a QR payment.
        assertThrows(BusinessException.class, () -> paymentService.verifyQr(order.getId(), "ADMIN1"));

        paymentService.recordCash(order.getId(), "ADMIN1");
        assertEquals(PaymentStatus.CASH_COLLECTED, orderService.get(order.getId()).getPaymentStatus());
        assertEquals(PaymentStatus.CASH_COLLECTED, paymentService.getForOrder(order.getId()).getStatus());
        assertThrows(BusinessException.class, () -> paymentService.recordCash(order.getId(), "ADMIN1"));

        queueService.updateStatus(order.getId(), OrderStatus.COMPLETED, "ADMIN1");
        assertEquals(OrderStatus.COMPLETED, orderService.get(order.getId()).getOrderStatus());
    }

    @Test
    void resubmittingTheSameCheckoutReturnsTheSameOrder() {
        User student = customer(Role.STUDENT, "21CS005");
        String key = UUID.randomUUID().toString();
        Order first = orderService.placeOrder(student.getId(), cart(menu, Map.of(dosa, 2)), PaymentMethod.QR, key);
        // Refresh / back button / second click: same key, even with a full cart again.
        Order second = orderService.placeOrder(student.getId(), cart(menu, Map.of(dosa, 2)), PaymentMethod.QR, key);
        // Even if the second click says Cash, the first choice stands.
        Order third = orderService.placeOrder(student.getId(), cart(menu, Map.of(dosa, 2)), PaymentMethod.CASH, key);

        assertEquals(first.getId(), second.getId());
        assertEquals(first.getId(), third.getId());
        assertEquals(PaymentMethod.QR, third.getPaymentMethod());
        assertEquals(1, orderService.listForCustomer(student.getId()).size());
        assertEquals(48, stockOf(dosa));
        assertEquals(1, paymentService.findForOrder(first.getId()).stream().count());
    }

    @Test
    void simultaneousDoubleClicksCreateExactlyOneOrder() throws Exception {
        User student = customer(Role.STUDENT, "21CS006");
        String key = UUID.randomUUID().toString();
        int clicks = 8;
        ExecutorService pool = Executors.newFixedThreadPool(clicks);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < clicks; i++) {
            Cart cart = cart(menu, Map.of(dosa, 3));
            results.add(pool.submit(() -> {
                start.await();
                return orderService.placeOrder(student.getId(), cart, PaymentMethod.CASH, key).getId();
            }));
        }
        start.countDown();
        Set<String> ids = new HashSet<>();
        for (Future<String> f : results) {
            ids.add(f.get());
        }
        pool.shutdown();
        assertEquals(1, ids.size(), "Every click must get the same order");
        assertEquals(1, orderService.listForCustomer(student.getId()).size());
        assertEquals(47, stockOf(dosa), "Stock is taken only once");
        assertEquals(1, queueService.activeQueue(canteen.getId()).size());
    }

    @Test
    void rejectedQrPaymentLeavesQueueKeepsRecordAndReturnsStock() {
        Order order = placeOrder(customer(Role.STUDENT, "21CS007"), menu, Map.of(dosa, 5), PaymentMethod.QR);
        submitProof(order);
        paymentService.rejectQr(order.getId(), "ADMIN1", "Amount not received");

        Order rejected = orderService.get(order.getId());
        assertEquals(OrderStatus.REJECTED, rejected.getOrderStatus());
        assertEquals(PaymentStatus.REJECTED, rejected.getPaymentStatus());
        assertEquals("Amount not received", rejected.getStatusNote());
        assertEquals(1, rejected.getTokenNumber(), "The token stays on record; no new token is given");
        assertEquals(PaymentStatus.REJECTED, paymentService.getForOrder(order.getId()).getStatus());
        assertTrue(queueService.activeQueue(canteen.getId()).isEmpty());
        assertEquals(50, stockOf(dosa));
        assertEquals(1, orderService.listRejectedPayments(canteen.getId(), rejected.getOrderDate()).size());
        assertThrows(BusinessException.class, () -> paymentService.verifyQr(order.getId(), "ADMIN1"));
    }

    @Test
    void queueIsFifoByPlacementTimeWithEqualPriorityForFaculty() throws Exception {
        Order student1 = placeOrder(customer(Role.STUDENT, "21CS010"), menu, Map.of(tea, 1), PaymentMethod.QR);
        Thread.sleep(5);
        Order faculty = placeOrder(customer(Role.FACULTY, "FAC-010"), menu, Map.of(tea, 1), PaymentMethod.CASH);
        Thread.sleep(5);
        Order student2 = placeOrder(customer(Role.STUDENT, "21CS011"), menu, Map.of(tea, 1), PaymentMethod.QR);

        // Payments are checked in a different order; the queue position must not change.
        paymentService.verifyQr(student2.getId(), "ADMIN1");
        paymentService.recordCash(faculty.getId(), "ADMIN1");
        paymentService.verifyQr(student1.getId(), "ADMIN1");

        List<Order> queue = queueService.activeQueue(canteen.getId());
        assertEquals(List.of(student1.getId(), faculty.getId(), student2.getId()),
                queue.stream().map(Order::getId).toList(), "Queue follows the order customers placed their orders");
        assertEquals(List.of(1, 2, 3), queue.stream().map(Order::getTokenNumber).toList());
    }

    @Test
    void tokensAreNeverDuplicatedUnderConcurrency() throws Exception {
        int threads = 40;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return tokenService.nextToken(canteen.getId(), "2099-01-01");
            }));
        }
        start.countDown();
        Set<Integer> tokens = new HashSet<>();
        for (Future<Integer> f : results) {
            tokens.add(f.get());
        }
        pool.shutdown();
        assertEquals(threads, tokens.size(), "Every token must be unique");
        assertTrue(tokens.contains(1) && tokens.contains(threads));
        // Tokens reset for a new day.
        assertEquals(1, tokenService.nextToken(canteen.getId(), "2099-01-02"));
    }

    @Test
    void ordersPlacedAtTheSameMomentGetUniqueTokens() throws Exception {
        int buyers = 20;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            users.add(customer(i % 2 == 0 ? Role.STUDENT : Role.FACULTY, "TOKEN" + i));
        }
        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            User u = users.get(i);
            PaymentMethod method = i % 2 == 0 ? PaymentMethod.CASH : PaymentMethod.QR;
            results.add(pool.submit(() -> {
                start.await();
                return placeOrder(u, menu, Map.of(tea, 1), method).getTokenNumber();
            }));
        }
        start.countDown();
        Set<Integer> tokens = new HashSet<>();
        for (Future<Integer> f : results) {
            tokens.add(f.get());
        }
        pool.shutdown();
        assertEquals(buyers, tokens.size());
        assertEquals(buyers, queueService.activeQueue(canteen.getId()).size());
    }

    @Test
    void limitedStockCannotBeOversold() throws Exception {
        MenuItem biryani = menuItem("Biryani", "120");
        menu = publishMenu(mealSessionService.listForCanteen(canteen.getId()).get(0), Map.of(biryani, 5));
        int buyers = 12;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            users.add(customer(i % 2 == 0 ? Role.STUDENT : Role.FACULTY, "BUYER" + i));
        }
        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            User u = users.get(i);
            PaymentMethod method = i % 2 == 0 ? PaymentMethod.CASH : PaymentMethod.QR;
            Callable<Boolean> task = () -> {
                start.await();
                try {
                    placeOrder(u, menu, Map.of(biryani, 1), method);
                    return true;
                } catch (BusinessException e) {
                    return false;
                }
            };
            results.add(pool.submit(task));
        }
        start.countDown();
        int placed = 0;
        for (Future<Boolean> f : results) {
            placed += f.get() ? 1 : 0;
        }
        pool.shutdown();
        assertEquals(5, placed, "Exactly the available 5 portions can be sold");
        assertEquals(0, dailyMenuService.get(menu.getId()).findItem(biryani.getId()).orElseThrow().getAvailableQuantity());

        User late = customer(Role.STUDENT, "LATE1");
        assertThrows(BusinessException.class, () -> placeOrder(late, menu, Map.of(biryani, 1)));
    }

    @Test
    void customerCannotPileUpUnpaidOrders() {
        User student = customer(Role.STUDENT, "21CS020");
        for (int i = 0; i < 3; i++) {
            placeOrder(student, menu, Map.of(tea, 1), PaymentMethod.CASH);
        }
        assertThrows(BusinessException.class, () -> placeOrder(student, menu, Map.of(tea, 1), PaymentMethod.QR));
        assertEquals(3, orderService.listForCustomer(student.getId()).size());
    }

    @Test
    void orderTakenCompletesOrderRemovesItFromQueueAndKeepsHistory() {
        User student = customer(Role.STUDENT, "21CS030");
        Order order = placeOrder(student, menu, Map.of(dosa, 1), PaymentMethod.CASH);

        // Customers' food cannot be handed over before it is ready.
        assertThrows(BusinessException.class, () -> queueService.updateStatus(order.getId(), OrderStatus.COMPLETED, "ADMIN1"));

        queueService.updateStatus(order.getId(), OrderStatus.PREPARING, "ADMIN1");
        queueService.updateStatus(order.getId(), OrderStatus.READY, "ADMIN1");
        paymentService.recordCash(order.getId(), "ADMIN1");
        queueService.updateStatus(order.getId(), OrderStatus.COMPLETED, "ADMIN1");   // "Order taken"

        Order done = orderService.get(order.getId());
        assertEquals(OrderStatus.COMPLETED, done.getOrderStatus());
        assertEquals("ADMIN1", done.getCompletedBy());
        assertNotNull(done.getCompletedAt());
        assertTrue(queueService.activeQueue(canteen.getId()).isEmpty());
        assertEquals(List.of(OrderStatus.QUEUED, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.COMPLETED),
                done.getStatusHistory().stream().map(StatusChange::getStatus).distinct().toList());

        // Still stored for history and reports.
        assertEquals(1, orderService.listForCustomer(student.getId()).size());
        assertEquals(1, orderService.listForDateAndStatuses(canteen.getId(), done.getOrderDate(), ReportService.FINISHED).size());
        ReportService.DailyReport report = reportService.dailyReport(canteen.getId(), done.getOrderDate());
        assertEquals(1, report.getCompletedOrders());
        assertEquals(0, new BigDecimal("40.00").compareTo(report.getCompletedRevenue()));
        assertEquals(0, new BigDecimal("40.00").compareTo(report.getCashReceived()));
        assertEquals(1, report.getItemsSold().get("Dosa"));
    }

    @Test
    void orderIsStoredInMongoWithTheWholeLifecycle() {
        Order order = placeOrder(customer(Role.FACULTY, "FAC-035"), menu, Map.of(tea, 3), PaymentMethod.QR);
        // Read the raw document straight from MongoDB (what survives an application restart).
        Document stored = mongo.getCollection("orders").find(new Document("orderNumber", order.getOrderNumber())).first();
        assertNotNull(stored);
        assertEquals("QR", stored.getString("paymentMethod"));
        assertEquals("QR_PAYMENT_PENDING", stored.getString("paymentStatus"));
        assertEquals("QUEUED", stored.getString("orderStatus"));
        assertEquals(1, stored.getInteger("tokenNumber"));
        assertNotNull(stored.getDate("queueEnteredAt"));
        assertNotNull(stored.getString("checkoutKey"));
        assertEquals(1, stored.getList("items", Document.class).size());
        assertNull(stored.get("completedAt"));
    }

    @Test
    void cancellingAQueuedOrderRemovesItAndReturnsStock() {
        Order order = placeOrder(customer(Role.FACULTY, "FAC-040"), menu, Map.of(dosa, 4));
        queueService.cancel(order.getId(), "ADMIN1", "Customer request");

        assertEquals(OrderStatus.CANCELLED, orderService.get(order.getId()).getOrderStatus());
        assertTrue(queueService.activeQueue(canteen.getId()).isEmpty());
        assertEquals(50, stockOf(dosa));
    }

    @Test
    void menuPriceChangesDoNotAlterExistingOrders() {
        Order order = placeOrder(customer(Role.STUDENT, "21CS050"), menu, Map.of(dosa, 1));
        var form = dailyMenuService.buildForm(canteen.getId(), dailyMenuService.today(), menu.getMealSessionId());
        form.getRows().stream().filter(r -> r.getMenuItemId().equals(dosa.getId()))
                .forEach(r -> r.setPrice(new BigDecimal("99")));
        dailyMenuService.save(canteen.getId(), form, "ADMIN1", SaveAction.PUBLISH);

        Order stored = orderService.get(order.getId());
        assertEquals(0, new BigDecimal("40.00").compareTo(stored.getItems().get(0).getUnitPrice()));
        assertEquals(0, new BigDecimal("40.00").compareTo(stored.getTotalAmount()));
    }
}
