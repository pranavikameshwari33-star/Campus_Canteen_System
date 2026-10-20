package com.campuscanteen.model;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A canteen ("canteens" collection). The first version uses one canteen, created automatically
 * at startup, but every menu, session, order and token counter stores a canteenId so more can be added later.
 * Times are stored as "HH:mm" text.
 */
@Document("canteens")
public class Canteen {

    @Id
    private String id;
    private String name;
    private String location;
    private String openingTime;
    private String closingTime;
    @Field("isActive")
    private boolean active = true;
    /** UPI ID shown under the QR code (optional, set by the administrator). */
    private String upiId;
    /** File name of the uploaded payment QR image (inside uploads/qr), or null if not configured. */
    private String paymentQrFile;
    /** Set once the default Breakfast, Lunch and Snacks sessions have been created, so they are never re-created. */
    private boolean defaultSessionsCreated;
    /** Set once the starter food list (from the printed menu) has been added, so it is never added again. */
    private boolean starterMenuAdded;
    /**
     * Set (atomically) when the first administrator registers through /admin/register, or at startup
     * if an administrator already exists. After that, new administrators need an invitation.
     */
    private boolean firstAdminRegistered;
    /** Invitations created by administrators for new administrators. */
    private List<AdminInvite> adminInvites = new ArrayList<>();

    /**
     * True if the canteen is open at this time. Opening time is inclusive, closing time exclusive.
     * Meal sessions are only open while the canteen itself is open.
     */
    public boolean isOpenAt(LocalTime time) {
        if (openingTime == null || closingTime == null) {
            return true;
        }
        return !time.isBefore(LocalTime.parse(openingTime)) && time.isBefore(LocalTime.parse(closingTime));
    }

    public String getOpeningHours() {
        return openingTime + " - " + closingTime;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getOpeningTime() { return openingTime; }
    public void setOpeningTime(String openingTime) { this.openingTime = openingTime; }

    public String getClosingTime() { return closingTime; }
    public void setClosingTime(String closingTime) { this.closingTime = closingTime; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getUpiId() { return upiId; }
    public void setUpiId(String upiId) { this.upiId = upiId; }

    public String getPaymentQrFile() { return paymentQrFile; }
    public void setPaymentQrFile(String paymentQrFile) { this.paymentQrFile = paymentQrFile; }

    public boolean isDefaultSessionsCreated() { return defaultSessionsCreated; }
    public void setDefaultSessionsCreated(boolean defaultSessionsCreated) { this.defaultSessionsCreated = defaultSessionsCreated; }
    public boolean isStarterMenuAdded() { return starterMenuAdded; }
    public void setStarterMenuAdded(boolean starterMenuAdded) { this.starterMenuAdded = starterMenuAdded; }

    public boolean isFirstAdminRegistered() { return firstAdminRegistered; }
    public void setFirstAdminRegistered(boolean firstAdminRegistered) { this.firstAdminRegistered = firstAdminRegistered; }

    public List<AdminInvite> getAdminInvites() { return adminInvites; }
    public void setAdminInvites(List<AdminInvite> adminInvites) { this.adminInvites = adminInvites; }

    /** True if customers can choose the QR Code option (a QR image or a UPI ID has been set). */
    public boolean isQrPaymentAvailable() {
        return isPaymentQrConfigured() || (upiId != null && !upiId.isBlank());
    }

    public boolean isPaymentQrConfigured() {
        return paymentQrFile != null && !paymentQrFile.isBlank();
    }
}
