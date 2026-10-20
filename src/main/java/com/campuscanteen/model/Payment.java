package com.campuscanteen.model;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Payment record for an order ("payments" collection), one per order, created when the order is placed.
 * Cash orders never have a screenshot. For QR orders the customer may upload one later; the screenshot file is stored on disk (uploads/payment-proofs); only its reference is kept here.
 * A screenshot alone is not proof of payment: an administrator must verify it.
 */
@Document("payments")
public class Payment {

    @Id
    private String id;
    private String orderId;
    private String orderNumber;
    private String userId;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    /** UPI transaction / reference number typed by the customer (helps the admin match the payment). */
    private String transactionRef;
    /** Internal link used to view the screenshot (only the owner and administrators can open it). */
    private String proofImageUrl;
    /** Stored file name inside uploads/payment-proofs. */
    private String proofFileName;
    private Instant proofSubmittedAt;
    private PaymentStatus status;
    private String verifiedBy;
    private Instant verifiedAt;
    private String rejectionReason;
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }

    public String getProofImageUrl() { return proofImageUrl; }
    public void setProofImageUrl(String proofImageUrl) { this.proofImageUrl = proofImageUrl; }

    public String getProofFileName() { return proofFileName; }
    public void setProofFileName(String proofFileName) { this.proofFileName = proofFileName; }

    public Instant getProofSubmittedAt() { return proofSubmittedAt; }
    public void setProofSubmittedAt(Instant proofSubmittedAt) { this.proofSubmittedAt = proofSubmittedAt; }

    public boolean isProofUploaded() { return proofFileName != null; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public String getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(String verifiedBy) { this.verifiedBy = verifiedBy; }

    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
