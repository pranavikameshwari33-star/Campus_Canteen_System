package com.campuscanteen.model;

import java.time.Instant;

/** One entry in an order's status history: what changed, when, and who did it. */
public class StatusChange {

    private OrderStatus status;
    private Instant changedAt;
    /** College ID of the person who made the change. */
    private String changedBy;
    private String note;

    public StatusChange() {
    }

    public StatusChange(OrderStatus status, Instant changedAt, String changedBy, String note) {
        this.status = status;
        this.changedAt = changedAt;
        this.changedBy = changedBy;
        this.note = note;
    }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }

    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
