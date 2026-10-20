package com.campuscanteen.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Daily token counter ("tokenCounters" collection). One document per canteen per day,
 * with _id = canteenId + "_" + date. See TokenService for how it is incremented atomically.
 */
@Document("tokenCounters")
public class TokenCounter {

    @Id
    private String id;
    private String canteenId;
    private String date;
    private int lastTokenNumber;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCanteenId() { return canteenId; }
    public void setCanteenId(String canteenId) { this.canteenId = canteenId; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public int getLastTokenNumber() { return lastTokenNumber; }
    public void setLastTokenNumber(int lastTokenNumber) { this.lastTokenNumber = lastTokenNumber; }
}
