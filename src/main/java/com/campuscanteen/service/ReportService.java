package com.campuscanteen.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.campuscanteen.model.Order;
import com.campuscanteen.model.OrderItem;
import com.campuscanteen.model.OrderStatus;
import com.campuscanteen.model.PaymentStatus;
import com.campuscanteen.repository.OrderRepository;

/** Simple daily report built from the orders placed on one date. */
@Service
public class ReportService {

    private final OrderRepository orderRepository;

    public ReportService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public DailyReport dailyReport(String canteenId, String date) {
        List<Order> orders = orderRepository.findByCanteenIdAndOrderDateOrderByCreatedAtAsc(canteenId, date);
        DailyReport report = new DailyReport(date);
        for (Order order : orders) {
            report.totalOrders++;
            switch (order.getOrderStatus()) {
                case COMPLETED -> {
                    report.completedOrders++;
                    report.completedRevenue = report.completedRevenue.add(order.getTotalAmount());
                    for (OrderItem item : order.getItems()) {
                        report.itemsSold.merge(item.getItemName(), item.getQuantity(), Integer::sum);
                    }
                }
                case QUEUED, PREPARING, READY -> {
                    report.inQueue++;
                    report.inQueueAmount = report.inQueueAmount.add(order.getTotalAmount());
                    if (order.getPaymentStatus() == PaymentStatus.CASH_DUE) {
                        report.cashDueOrders++;
                        report.cashDueAmount = report.cashDueAmount.add(order.getTotalAmount());
                    } else if (PaymentStatus.QR_UNCHECKED.contains(order.getPaymentStatus())) {
                        report.qrToCheck++;
                    }
                }
                case AWAITING_VERIFICATION -> report.qrToCheck++;
                case REJECTED -> report.rejected++;
                case CANCELLED -> report.cancelled++;
            }
            // Money actually received, by method (whatever the order status).
            if (order.getPaymentStatus() == PaymentStatus.CASH_COLLECTED) {
                report.cashReceived = report.cashReceived.add(order.getTotalAmount());
            } else if (order.getPaymentStatus() == PaymentStatus.VERIFIED) {
                report.qrReceived = report.qrReceived.add(order.getTotalAmount());
            }
        }
        return report;
    }

    public static class DailyReport {
        private final String date;
        private int totalOrders;
        private int completedOrders;
        private BigDecimal completedRevenue = BigDecimal.ZERO;
        private int inQueue;
        private BigDecimal inQueueAmount = BigDecimal.ZERO;
        private int cashDueOrders;
        private BigDecimal cashDueAmount = BigDecimal.ZERO;
        private int qrToCheck;
        private BigDecimal cashReceived = BigDecimal.ZERO;
        private BigDecimal qrReceived = BigDecimal.ZERO;
        private int rejected;
        private int cancelled;
        /** Item name -> portions collected, sorted by name. */
        private final Map<String, Integer> itemsSold = new TreeMap<>();

        DailyReport(String date) {
            this.date = date;
        }

        public String getDate() { return date; }
        public int getTotalOrders() { return totalOrders; }
        public int getCompletedOrders() { return completedOrders; }
        public BigDecimal getCompletedRevenue() { return completedRevenue; }
        public int getInQueue() { return inQueue; }
        public BigDecimal getInQueueAmount() { return inQueueAmount; }
        public int getCashDueOrders() { return cashDueOrders; }
        public BigDecimal getCashDueAmount() { return cashDueAmount; }
        public int getQrToCheck() { return qrToCheck; }
        public BigDecimal getCashReceived() { return cashReceived; }
        public BigDecimal getQrReceived() { return qrReceived; }
        public int getRejected() { return rejected; }
        public int getCancelled() { return cancelled; }
        public Map<String, Integer> getItemsSold() { return itemsSold; }
    }

    /** Orders whose status means they are finished (shown on the history page). */
    public static final List<OrderStatus> FINISHED = List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.REJECTED);
}
