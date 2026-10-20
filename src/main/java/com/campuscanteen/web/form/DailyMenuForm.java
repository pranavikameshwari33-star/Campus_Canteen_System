package com.campuscanteen.web.form;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The daily menu editor: one row per catalogue item. The administrator ticks the items to
 * include and sets the price, remaining quantity and availability for that day and session.
 */
public class DailyMenuForm {

    private String date;
    private String mealSessionId;
    private List<Row> rows = new ArrayList<>();

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getMealSessionId() { return mealSessionId; }
    public void setMealSessionId(String mealSessionId) { this.mealSessionId = mealSessionId; }

    public List<Row> getRows() { return rows; }
    public void setRows(List<Row> rows) { this.rows = rows; }

    public static class Row {
        private String menuItemId;
        private String name;
        private String category;
        /** e.g. "Chinese - Noodles", for display only. */
        private String categoryLabel;
        /** Why this row needs attention (missing price, unconfirmed name), for display only. */
        private String note;
        private boolean included;
        private BigDecimal price;
        private Integer quantity;
        /**
         * The quantity shown when the page was opened. If the administrator leaves the quantity unchanged,
         * the stored value is kept, so orders placed while the page was open are not "undone".
         */
        private Integer originalQuantity;
        private boolean available = true;

        public String getMenuItemId() { return menuItemId; }
        public void setMenuItemId(String menuItemId) { this.menuItemId = menuItemId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getCategoryLabel() { return categoryLabel; }
        public void setCategoryLabel(String categoryLabel) { this.categoryLabel = categoryLabel; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
        public boolean isIncluded() { return included; }
        public void setIncluded(boolean included) { this.included = included; }

        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public Integer getOriginalQuantity() { return originalQuantity; }
        public void setOriginalQuantity(Integer originalQuantity) { this.originalQuantity = originalQuantity; }

        public boolean isAvailable() { return available; }
        public void setAvailable(boolean available) { this.available = available; }
    }
}
