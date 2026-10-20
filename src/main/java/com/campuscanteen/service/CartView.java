package com.campuscanteen.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.campuscanteen.model.DailyMenu;
import com.campuscanteen.model.DailyMenuItem;

/**
 * The cart with prices filled in from the daily menu. Built entirely on the server;
 * nothing sent by the browser (such as a total) is trusted.
 */
public class CartView {

    /** Maximum portions of one item in a single order. */
    public static final int MAX_PER_ITEM = 20;

    private final List<Line> lines = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();
    private BigDecimal total = BigDecimal.ZERO;

    /** Works out line subtotals and the total, and lists anything that prevents checkout. */
    public static CartView calculate(DailyMenu menu, Cart cart) {
        CartView view = new CartView();
        cart.getQuantities().forEach((menuItemId, quantity) -> {
            DailyMenuItem item = menu.findItem(menuItemId).orElse(null);
            if (item == null) {
                view.problems.add("An item in your cart is no longer on the menu. Please remove it.");
                return;
            }
            Line line = new Line(item, quantity);
            if (!item.isOrderable()) {
                line.problem = "Sold out";
                view.problems.add(item.getName() + " is sold out. Please remove it.");
            } else if (quantity > item.getAvailableQuantity()) {
                line.problem = "Only " + item.getAvailableQuantity() + " left";
                view.problems.add("Only " + item.getAvailableQuantity() + " of " + item.getName() + " left.");
            } else if (quantity > MAX_PER_ITEM) {
                line.problem = "Maximum " + MAX_PER_ITEM;
                view.problems.add("You can order at most " + MAX_PER_ITEM + " of one item.");
            }
            view.lines.add(line);
            view.total = view.total.add(line.subtotal);
        });
        return view;
    }

    public List<Line> getLines() { return lines; }
    public List<String> getProblems() { return problems; }
    public BigDecimal getTotal() { return total; }

    public boolean isEmpty() {
        return lines.isEmpty() && problems.isEmpty();
    }

    public boolean isReadyForCheckout() {
        return !lines.isEmpty() && problems.isEmpty();
    }

    public static class Line {
        private final String menuItemId;
        private final String name;
        private final BigDecimal unitPrice;
        private final int quantity;
        private final BigDecimal subtotal;
        private final int maxQuantity;
        private String problem;

        Line(DailyMenuItem item, int quantity) {
            this.menuItemId = item.getMenuItemId();
            this.name = item.getName();
            this.unitPrice = item.getPriceForThatDay();
            this.quantity = quantity;
            this.subtotal = item.getPriceForThatDay().multiply(BigDecimal.valueOf(quantity));
            this.maxQuantity = Math.min(MAX_PER_ITEM, Math.max(item.getAvailableQuantity(), 1));
        }

        public String getMenuItemId() { return menuItemId; }
        public String getName() { return name; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public int getQuantity() { return quantity; }
        public BigDecimal getSubtotal() { return subtotal; }
        public int getMaxQuantity() { return maxQuantity; }
        public String getProblem() { return problem; }
    }
}
