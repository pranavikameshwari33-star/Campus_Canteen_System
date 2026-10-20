package com.campuscanteen.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A food item in the permanent catalogue ("menuItems" collection), e.g. Idly or Veg Noodles.
 * Whether it is sold today, and at what price, is decided by the daily menu.
 *
 * The default price is optional: items added from the canteen's printed menu start as drafts
 * without a price, and the administrator enters prices before selling them.
 */
@Document("menuItems")
public class MenuItem {

    public static final String BREAKFAST = "Breakfast";
    public static final String LUNCH = "Lunch";
    public static final String SNACKS = "Snacks";
    public static final String CHINESE = "Chinese";

    /** The menu sections, in the order customers see them. */
    public static final List<String> CATEGORIES = List.of(BREAKFAST, LUNCH, SNACKS, CHINESE);

    /** Sub-sections used only inside the Chinese category. */
    public static final List<String> CHINESE_SUBCATEGORIES = List.of("Rice", "Noodles", "Starters");

    @Id
    private String id;
    private String name;
    private String description;
    private String category;
    /** Rice, Noodles or Starters for Chinese items; empty for the other categories. */
    private String subcategory;
    /** Usual price, or null while the item is a draft without a price. */
    private BigDecimal defaultPrice;
    /** Optional link to a photo of the item. */
    private String imageUrl;
    @Field("isActive")
    private boolean active = true;
    /**
     * True when the name could not be read clearly from the printed menu. Such items cannot be
     * put on a daily menu until an administrator checks the name and clears this flag.
     */
    private boolean nameNeedsConfirmation;
    private Instant createdAt;
    private Instant updatedAt;

    /** Shown in the interface, e.g. "Breakfast / Tiffins" or "Chinese - Noodles". */
    public static String categoryLabel(String category, String subcategory) {
        if (BREAKFAST.equals(category)) {
            return "Breakfast / Tiffins";
        }
        if (category != null && subcategory != null && !subcategory.isBlank()) {
            return category + " - " + subcategory;
        }
        return category;
    }

    public String getCategoryLabel() {
        return categoryLabel(category, subcategory);
    }

    /** A draft still needs a price or a name check before it can be sold. */
    public boolean isDraft() {
        return defaultPrice == null || nameNeedsConfirmation;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSubcategory() { return subcategory; }
    public void setSubcategory(String subcategory) { this.subcategory = subcategory; }
    public BigDecimal getDefaultPrice() { return defaultPrice; }
    public void setDefaultPrice(BigDecimal defaultPrice) { this.defaultPrice = defaultPrice; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isNameNeedsConfirmation() { return nameNeedsConfirmation; }
    public void setNameNeedsConfirmation(boolean nameNeedsConfirmation) { this.nameNeedsConfirmation = nameNeedsConfirmation; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
