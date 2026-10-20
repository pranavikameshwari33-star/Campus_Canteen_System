package com.campuscanteen.model;

/** How the customer chose to pay at checkout. Both methods put the order in the queue straight away. */
public enum PaymentMethod {
    CASH("Cash at pickup"),
    QR("UPI QR code");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
