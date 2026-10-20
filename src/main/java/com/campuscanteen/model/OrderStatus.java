package com.campuscanteen.model;

import java.util.List;

/**
 * Life cycle of an order:
 * QUEUED -> PREPARING -> READY -> COMPLETED
 * An order is QUEUED (and gets its token) as soon as the customer chooses Cash or QR Code.
 * It can also end as REJECTED (QR payment not accepted) or CANCELLED (by an administrator).
 * AWAITING_VERIFICATION is no longer used for new orders; it is kept so old orders can still be read.
 */
public enum OrderStatus {
    AWAITING_VERIFICATION("Waiting for payment verification"),
    QUEUED("In queue"),
    PREPARING("Preparing"),
    READY("Ready for pickup"),
    COMPLETED("Collected"),
    CANCELLED("Cancelled"),
    REJECTED("Payment rejected");

    /** Orders in these states form the active FIFO queue. */
    public static final List<OrderStatus> ACTIVE_QUEUE = List.of(QUEUED, PREPARING, READY);

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isInQueue() {
        return ACTIVE_QUEUE.contains(this);
    }

    public boolean isFinished() {
        return this == COMPLETED || this == CANCELLED || this == REJECTED;
    }
}
