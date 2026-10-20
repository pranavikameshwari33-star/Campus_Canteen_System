package com.campuscanteen.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.campuscanteen.model.Order;
import com.campuscanteen.model.OrderItem;
import com.campuscanteen.model.OrderStatus;
import com.campuscanteen.model.PaymentStatus;
import com.campuscanteen.model.StatusChange;

/**
 * The FIFO pickup queue.
 *
 * An order joins the queue the moment it is placed (when the customer chooses Cash or QR Code),
 * so queueEnteredAt is the time of that choice. Payment status does not affect the position.
 *
 * There is no separate queue collection: the queue is the set of orders whose status is
 * QUEUED, PREPARING or READY, sorted by the time they entered the queue (queueEnteredAt),
 * then by token number and finally by _id, so the order is always the same.
 * Role, department and year are never used for sorting, so students and faculty are equal.
 *
 * Status changes use a conditional update ("only if the order is still in status X"), so a
 * double click or two staff members acting at once cannot skip or repeat a step.
 */
@Service
public class QueueService {

    /** Which statuses an order may move to, from which statuses. */
    private static final Map<OrderStatus, List<OrderStatus>> ALLOWED_FROM = Map.of(
            OrderStatus.PREPARING, List.of(OrderStatus.QUEUED),
            OrderStatus.READY, List.of(OrderStatus.QUEUED, OrderStatus.PREPARING),
            OrderStatus.COMPLETED, List.of(OrderStatus.READY));

    private final MongoTemplate mongo;
    private final DailyMenuService dailyMenuService;
    private final Clock clock;

    public QueueService(MongoTemplate mongo, DailyMenuService dailyMenuService, Clock clock) {
        this.mongo = mongo;
        this.dailyMenuService = dailyMenuService;
        this.clock = clock;
    }

    /** The active queue, first come first served. */
    public List<Order> activeQueue(String canteenId) {
        Query query = new Query(Criteria.where("canteenId").is(canteenId)
                .and("orderStatus").in(OrderStatus.ACTIVE_QUEUE))
                .with(Sort.by(Sort.Order.asc("queueEnteredAt"), Sort.Order.asc("tokenNumber"), Sort.Order.asc("id")));
        return mongo.find(query, Order.class);
    }

    /**
     * Moves an order forward: QUEUED -> PREPARING -> READY -> COMPLETED.
     * COMPLETED is the "Order Taken" action: the order leaves the active queue automatically
     * (because the queue only shows QUEUED/PREPARING/READY) but stays in the database for history. It is only allowed once the payment has been
     * received (cash recorded or QR payment verified).
     */
    public void updateStatus(String orderId, OrderStatus target, String adminCollegeId) {
        List<OrderStatus> from = ALLOWED_FROM.get(target);
        if (from == null) {
            throw new BusinessException("That status change is not allowed.");
        }
        Instant now = Instant.now(clock);
        Update update = new Update()
                .set("orderStatus", target)
                .set("updatedAt", now)
                .push("statusHistory", new StatusChange(target, now, adminCollegeId, null));
        if (target == OrderStatus.COMPLETED) {
            update.set("completedAt", now).set("completedBy", adminCollegeId);
        }
        Criteria criteria = Criteria.where("id").is(orderId).and("orderStatus").in(from);
        if (target == OrderStatus.COMPLETED) {
            // Food is only handed over once the money has really been received.
            criteria = criteria.and("paymentStatus").in(PaymentStatus.CASH_COLLECTED, PaymentStatus.VERIFIED);
        }
        if (mongo.updateFirst(new Query(criteria), update, Order.class).getModifiedCount() == 0) {
            Order order = mongo.findById(orderId, Order.class);
            if (target == OrderStatus.COMPLETED && order != null && order.getOrderStatus() == OrderStatus.READY
                    && !order.getPaymentStatus().isPaid()) {
                throw new BusinessException("Token " + order.getTokenDisplay() + " is not paid yet ("
                        + order.getPaymentStatus().getLabel() + "). Record the cash or verify the QR payment first.");
            }
            throw new BusinessException("This order's status has already changed. The page has been refreshed.");
        }
    }

    /**
     * Cancels an order that is in the queue (for example, the customer never came).
     * If food preparation had not started yet, the portions go back into stock.
     * Any refund is handled by the canteen at the counter.
     */
    public void cancel(String orderId, String adminCollegeId, String reason) {
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isEmpty() || cleanReason.length() > 200) {
            throw new BusinessException("Enter a short reason for cancelling (200 characters maximum).");
        }
        Instant now = Instant.now(clock);
        Query query = new Query(Criteria.where("id").is(orderId).and("orderStatus").in(OrderStatus.ACTIVE_QUEUE));
        Update update = new Update()
                .set("orderStatus", OrderStatus.CANCELLED)
                .set("statusNote", cleanReason)
                .set("updatedAt", now)
                .push("statusHistory", new StatusChange(OrderStatus.CANCELLED, now, adminCollegeId, cleanReason));
        // returnNew(false) gives back the order as it was BEFORE cancelling, so we know its old status.
        Order before = mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(false), Order.class);
        if (before == null) {
            throw new BusinessException("This order can no longer be cancelled.");
        }
        if (before.getOrderStatus() == OrderStatus.QUEUED) {
            for (OrderItem item : before.getItems()) {
                dailyMenuService.releaseStock(before.getDailyMenuId(), item.getMenuItemId(), item.getQuantity());
            }
        }
    }
}
