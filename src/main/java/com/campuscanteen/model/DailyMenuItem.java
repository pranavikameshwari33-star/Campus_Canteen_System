package com.campuscanteen.model;

import java.math.BigDecimal;

import org.springframework.data.mongodb.core.mapping.Field;

/**
 * One item inside a daily menu. Name, description, category and image are copied from the
 * catalogue when the menu is saved. availableQuantity is the remaining stock: it goes down
 * (atomically) every time an order is placed.
 */
public class DailyMenuItem {

    private String menuItemId;
    private String name;
    private String description;
    private String category;
    private String subcategory;
    private String imageUrl;
    private BigDecimal priceForThatDay;
    private int availableQuantity;
    @Field("isAvailable")
    private boolean available = true;

    /** True when a customer can add this item to the cart right now. */
    public boolean isOrderable() {
        return available && availableQuantity > 0;
    }

    public String getMenuItemId() { return menuItemId; }
    public void setMenuItemId(String menuItemId) { this.menuItemId = menuItemId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSubcategory() { return subcategory; }
    public void setSubcategory(String subcategory) { this.subcategory = subcategory; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public BigDecimal getPriceForThatDay() { return priceForThatDay; }
    public void setPriceForThatDay(BigDecimal priceForThatDay) { this.priceForThatDay = priceForThatDay; }

    public int getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(int availableQuantity) { this.availableQuantity = availableQuantity; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}
