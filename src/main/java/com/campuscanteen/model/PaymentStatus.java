package com.campuscanteen.model;

import java.util.List;

/**
 * Payment status of an order. It is kept separate from the order status: an order is in the
 * queue as soon as it is placed, whatever its payment status.
 *
 * Cash:  CASH_DUE -> CASH_COLLECTED (an administrator records that the cash was received)
 * QR:    QR_PAYMENT_PENDING -> QR_PROOF_SUBMITTED -> VERIFIED (an administrator checked the UPI account)
 * Either QR status can also end as REJECTED.
 * PENDING is only kept so that orders saved by older versions of the application can still be read.
 */
public enum PaymentStatus {
    CASH_DUE("Cash due at pickup"),
    CASH_COLLECTED("Cash received"),
    QR_PAYMENT_PENDING("QR payment pending"),
    QR_PROOF_SUBMITTED("QR proof awaiting verification"),
    VERIFIED("Payment verified"),
    REJECTED("Payment rejected"),
    PENDING("Pending verification");

    /** QR payments an administrator still has to check. */
    public static final List<PaymentStatus> QR_UNCHECKED = List.of(QR_PAYMENT_PENDING, QR_PROOF_SUBMITTED, PENDING);

    /** Payments where no money has been confirmed yet. */
    public static final List<PaymentStatus> UNPAID = List.of(CASH_DUE, QR_PAYMENT_PENDING, QR_PROOF_SUBMITTED, PENDING);

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** True once the canteen has actually received the money (cash collected or QR verified). */
    public boolean isPaid() {
        return this == CASH_COLLECTED || this == VERIFIED;
    }
}
