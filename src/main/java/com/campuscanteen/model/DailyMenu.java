package com.campuscanteen.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * The menu for one date and one meal session ("dailyMenus" collection).
 * There is at most one per canteen + date + session (unique index).
 * date is stored as "yyyy-MM-dd" text in the canteen's time zone.
 */
@Document("dailyMenus")
public class DailyMenu {

    @Id
    private String id;
    private String date;
    private String canteenId;
    private String mealSessionId;
    /** Copy of the session name, for display. */
    private String mealSessionName;
    private List<DailyMenuItem> items = new ArrayList<>();
    @Field("isPublished")
    private boolean published;
    /** College ID of the administrator who last published it. */
    private String publishedBy;
    private Instant publishedAt;
    private Instant updatedAt;

    public Optional<DailyMenuItem> findItem(String menuItemId) {
        return items.stream().filter(i -> i.getMenuItemId().equals(menuItemId)).findFirst();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getCanteenId() { return canteenId; }
    public void setCanteenId(String canteenId) { this.canteenId = canteenId; }

    public String getMealSessionId() { return mealSessionId; }
    public void setMealSessionId(String mealSessionId) { this.mealSessionId = mealSessionId; }

    public String getMealSessionName() { return mealSessionName; }
    public void setMealSessionName(String mealSessionName) { this.mealSessionName = mealSessionName; }

    public List<DailyMenuItem> getItems() { return items; }
    public void setItems(List<DailyMenuItem> items) { this.items = items; }

    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }

    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
