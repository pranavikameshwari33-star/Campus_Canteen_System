package com.campuscanteen.model;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A meal session such as Breakfast or Lunch ("mealSessions" collection).
 * startTime and endTime are "HH:mm" text and are edited by the administrator.
 * A session is open from startTime (inclusive) until endTime (exclusive).
 * Customers can place orders only while a session is open.
 *
 * allowedCategories lists the food categories that may be sold in this session
 * (for example Lunch: Lunch and Chinese). Chinese is refused for any session whose
 * type is not LUNCH or SNACKS, whatever the list says.
 */
@Document("mealSessions")
public class MealSession {

    @Id
    private String id;
    private String name;
    private MealType type;
    private String startTime;
    private String endTime;
    private String canteenId;
    private List<String> allowedCategories = new ArrayList<>();
    @Field("isActive")
    private boolean active = true;

    public boolean isOpenAt(LocalTime time) {
        return active && !time.isBefore(start()) && time.isBefore(end());
    }

    /** True if items of this category may be sold during this session. */
    public boolean allows(String category) {
        if (MenuItem.CHINESE.equals(category) && !getType().allowsChinese()) {
            return false;
        }
        // Sessions saved before categories existed have no list: they allow every category except Chinese.
        if (allowedCategories == null || allowedCategories.isEmpty()) {
            return !MenuItem.CHINESE.equals(category);
        }
        return allowedCategories.contains(category);
    }

    public LocalTime start() {
        return LocalTime.parse(startTime);
    }

    public LocalTime end() {
        return LocalTime.parse(endTime);
    }

    public String getTimeRange() {
        return startTime + " - " + endTime;
    }

    /** The categories this session really allows (after the Chinese rule), for display. */
    public List<String> getEffectiveCategories() {
        return MenuItem.CATEGORIES.stream().filter(this::allows).toList();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public MealType getType() { return type == null ? MealType.OTHER : type; }
    public void setType(MealType type) { this.type = type; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public String getCanteenId() { return canteenId; }
    public void setCanteenId(String canteenId) { this.canteenId = canteenId; }
    public List<String> getAllowedCategories() { return allowedCategories; }
    public void setAllowedCategories(List<String> allowedCategories) { this.allowedCategories = allowedCategories; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
