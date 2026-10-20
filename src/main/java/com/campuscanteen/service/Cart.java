package com.campuscanteen.service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.campuscanteen.model.DailyMenu;
import com.campuscanteen.model.DailyMenuItem;

/**
 * The shopping cart, kept in the user's login session (not in the database).
 * It only remembers which items and how many; prices are always looked up again
 * from the current daily menu on the server.
 */
public class Cart implements Serializable {

    public static final String SESSION_KEY = "CART";

    /** The daily menu this cart belongs to. */
    private String dailyMenuId;
    /** Name of that menu's meal session, e.g. "Lunch", used to explain changes. */
    private String mealSessionName;
    private final Map<String, Integer> quantities = new LinkedHashMap<>();
    /** Item names, so removed items can be named in messages. */
    private final Map<String, String> names = new HashMap<>();

    public String getDailyMenuId() { return dailyMenuId; }
    public String getMealSessionName() { return mealSessionName; }
    public Map<String, Integer> getQuantities() { return quantities; }

    public boolean isEmpty() {
        return quantities.isEmpty();
    }

    public int getItemCount() {
        return quantities.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Makes sure the cart belongs to the given menu. When the meal session has changed (for example
     * Lunch ended and Snacks began), items that are also orderable on the new menu are kept and the
     * others are removed. Returns what changed, or null if the cart already belonged to this menu.
     */
    public Change attachTo(DailyMenu menu) {
        if (dailyMenuId == null || quantities.isEmpty()) {
            dailyMenuId = menu.getId();
            mealSessionName = menu.getMealSessionName();
            quantities.clear();
            return null;
        }
        if (dailyMenuId.equals(menu.getId())) {
            return null;
        }
        List<String> removed = new ArrayList<>();
        quantities.entrySet().removeIf(entry -> {
            DailyMenuItem item = menu.findItem(entry.getKey()).orElse(null);
            if (item == null || !item.isOrderable()) {
                removed.add(names.getOrDefault(entry.getKey(), "an item"));
                return true;
            }
            names.put(item.getMenuItemId(), item.getName());
            return false;
        });
        Change change = new Change(mealSessionName, menu.getMealSessionName(), removed);
        dailyMenuId = menu.getId();
        mealSessionName = menu.getMealSessionName();
        return change;
    }

    public void setQuantity(String menuItemId, int quantity) {
        if (quantity <= 0) {
            quantities.remove(menuItemId);
        } else {
            quantities.put(menuItemId, quantity);
        }
    }

    public void setQuantity(DailyMenuItem item, int quantity) {
        names.put(item.getMenuItemId(), item.getName());
        setQuantity(item.getMenuItemId(), quantity);
    }

    public void clear() {
        quantities.clear();
        dailyMenuId = null;
        mealSessionName = null;
    }

    /** What happened to the cart when the menu changed. */
    public record Change(String oldSession, String newSession, List<String> removedItems) implements Serializable {

        /** A plain-English explanation for the customer. */
        public String message() {
            String start = oldSession != null && newSession != null && !oldSession.equals(newSession)
                    ? "The " + oldSession + " session has ended and the " + newSession + " menu is now open. "
                    : "The menu has changed. ";
            if (removedItems.isEmpty()) {
                return start + "Please check the prices in your cart again.";
            }
            return start + "These items are not available now and were removed from your cart: "
                    + String.join(", ", removedItems) + ".";
        }
    }
}
