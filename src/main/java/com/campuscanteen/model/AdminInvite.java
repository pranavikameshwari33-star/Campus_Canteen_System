package com.campuscanteen.model;

import java.time.Instant;

/**
 * A one-time invitation that lets one more person register as a canteen administrator.
 * Stored inside the canteen document ("adminInvites"). Only a SHA-256 hash of the code is
 * saved, so reading the database does not reveal usable codes.
 */
public class AdminInvite {

    private String codeHash;
    private String createdBy;
    private Instant createdAt;
    private Instant expiresAt;
    /** Set when the invitation has been used; it can never be used again. */
    private String usedBy;
    private Instant usedAt;

    public AdminInvite() {
    }

    public AdminInvite(String codeHash, String createdBy, Instant createdAt, Instant expiresAt) {
        this.codeHash = codeHash;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isOpenAt(Instant now) {
        return usedAt == null && expiresAt != null && now.isBefore(expiresAt);
    }

    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public String getUsedBy() { return usedBy; }
    public void setUsedBy(String usedBy) { this.usedBy = usedBy; }

    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }
}
