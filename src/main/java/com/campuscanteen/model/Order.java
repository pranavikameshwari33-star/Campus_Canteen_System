package com.campuscanteen.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * An order ("orders" collection). This collection is also the persistent FIFO queue.
 * An order enters the queue (status QUEUED, with a token) the moment the customer chooses
 * Cash or QR Code at checkout; payment is tracked separately in paymentStatus.
 * The active queue is simply every order whose status is QUEUED, PREPARING or READY,
 * sorted by queueEnteredAt (then tokenNumber, then _id). Orders are never deleted.
 *
 * Item names and prices are copied into the order when it is placed, so later menu
 * changes do not alter what was charged.
 */
@Document("orders")
public class Order {

    @Id
    private String id;
    private String orderNumber;
    private String userId;
    private String customerName;
    private String collegeId;
    /** Stored for records only. It is never used for sorting the queue. */
    private Role role;
    private String canteenId;
    private String mealSessionId;
    private String mealSessionName;
    private String dailyMenuId;
    /** Date the order was placed, "yyyy-MM-dd". */
    private String orderDate;
    private List<OrderItem> items = new ArrayList<>();
    private BigDecimal totalAmount;
    private PaymentMethod paymentMethod;
    /** Independent of orderStatus: e.g. an order can be READY while its QR payment is still being checked. */
    private PaymentStatus paymentStatus;
    /** When and by whom cash was collected or the QR payment was verified/rejected. */
    private Instant paymentConfirmedAt;
    private String paymentConfirmedBy;
    /**
     * Random key from the checkout page. A unique index on (userId, checkoutKey) makes sure a
     * double click or a resubmitted form can never create a second order.
     */
    private String checkoutKey;
    private OrderStatus orderStatus;
    /** Pickup token, assigned when the order is placed. Resets daily per canteen. */
    private Integer tokenNumber;
    /** Date the token belongs to, "yyyy-MM-dd". */
    private String tokenDate;
    private Instant queueEnteredAt;
    private Instant completedAt;
    private String completedBy;
    /** Reason shown to the customer if the order was rejected or cancelled. */
    private String statusNote;
    private Instant createdAt;
    private Instant updatedAt;
    private List<StatusChange> statusHistory = new ArrayList<>();

    public String getTokenDisplay() {
        return tokenNumber == null ? null : String.format("%03d", tokenNumber);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCollegeId() { return collegeId; }
    public void setCollegeId(String collegeId) { this.collegeId = collegeId; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getCanteenId() { return canteenId; }
    public void setCanteenId(String canteenId) { this.canteenId = canteenId; }

    public String getMealSessionId() { return mealSessionId; }
    public void setMealSessionId(String mealSessionId) { this.mealSessionId = mealSessionId; }

    public String getMealSessionName() { return mealSessionName; }
    public void setMealSessionName(String mealSessionName) { this.mealSessionName = mealSessionName; }

    public String getDailyMenuId() { return dailyMenuId; }
    public void setDailyMenuId(String dailyMenuId) { this.dailyMenuId = dailyMenuId; }

    public String getOrderDate() { return orderDate; }
    public void setOrderDate(String orderDate) { this.orderDate = orderDate; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public Instant getPaymentConfirmedAt() { return paymentConfirmedAt; }
    public void setPaymentConfirmedAt(Instant paymentConfirmedAt) { this.paymentConfirmedAt = paymentConfirmedAt; }

    public String getPaymentConfirmedBy() { return paymentConfirmedBy; }
    public void setPaymentConfirmedBy(String paymentConfirmedBy) { this.paymentConfirmedBy = paymentConfirmedBy; }

    public String getCheckoutKey() { return checkoutKey; }
    public void setCheckoutKey(String checkoutKey) { this.checkoutKey = checkoutKey; }

    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }

    public OrderStatus getOrderStatus() { return orderStatus; }
    public void setOrderStatus(OrderStatus orderStatus) { this.orderStatus = orderStatus; }

    public Integer getTokenNumber() { return tokenNumber; }
    public void setTokenNumber(Integer tokenNumber) { this.tokenNumber = tokenNumber; }

    public String getTokenDate() { return tokenDate; }
    public void setTokenDate(String tokenDate) { this.tokenDate = tokenDate; }

    public Instant getQueueEnteredAt() { return queueEnteredAt; }
    public void setQueueEnteredAt(Instant queueEnteredAt) { this.queueEnteredAt = queueEnteredAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public String getCompletedBy() { return completedBy; }
    public void setCompletedBy(String completedBy) { this.completedBy = completedBy; }

    public String getStatusNote() { return statusNote; }
    public void setStatusNote(String statusNote) { this.statusNote = statusNote; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<StatusChange> getStatusHistory() { return statusHistory; }
    public void setStatusHistory(List<StatusChange> statusHistory) { this.statusHistory = statusHistory; }
}
