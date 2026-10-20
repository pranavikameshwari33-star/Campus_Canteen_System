package com.campuscanteen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.campuscanteen.model.DailyMenu;
import com.campuscanteen.model.DailyMenuItem;
import com.campuscanteen.service.Cart;
import com.campuscanteen.service.CartView;

/** Requirement 8: cart lines and total are calculated correctly on the server. No database needed. */
class CartViewTest {

    private static DailyMenuItem item(String id, String name, String price, int quantity, boolean available) {
        DailyMenuItem item = new DailyMenuItem();
        item.setMenuItemId(id);
        item.setName(name);
        item.setPriceForThatDay(new BigDecimal(price));
        item.setAvailableQuantity(quantity);
        item.setAvailable(available);
        return item;
    }

    private static DailyMenu menu() {
        DailyMenu menu = new DailyMenu();
        menu.setId("menu1");
        menu.getItems().add(item("dosa", "Dosa", "40.00", 10, true));
        menu.getItems().add(item("tea", "Tea", "12.50", 3, true));
        menu.getItems().add(item("vada", "Vada", "15.00", 5, false));
        return menu;
    }

    @Test
    void totalIsSumOfPriceTimesQuantity() {
        Cart cart = new Cart();
        cart.attachTo(menu());
        cart.setQuantity("dosa", 2);
        cart.setQuantity("tea", 3);

        CartView view = CartView.calculate(menu(), cart);

        assertEquals(new BigDecimal("80.00"), view.getLines().get(0).getSubtotal());
        assertEquals(new BigDecimal("37.50"), view.getLines().get(1).getSubtotal());
        assertEquals(0, new BigDecimal("117.50").compareTo(view.getTotal()));
        assertTrue(view.isReadyForCheckout());
    }

    @Test
    void quantityAboveStockBlocksCheckout() {
        Cart cart = new Cart();
        cart.attachTo(menu());
        cart.setQuantity("tea", 4);

        CartView view = CartView.calculate(menu(), cart);

        assertFalse(view.isReadyForCheckout());
        assertEquals("Only 3 left", view.getLines().get(0).getProblem());
    }

    @Test
    void soldOutItemBlocksCheckout() {
        Cart cart = new Cart();
        cart.attachTo(menu());
        cart.setQuantity("vada", 1);

        assertFalse(CartView.calculate(menu(), cart).isReadyForCheckout());
    }

    @Test
    void sessionChangeKeepsItemsOnTheNewMenuAndNamesTheRemovedOnes() {
        DailyMenu lunch = menu();
        lunch.setMealSessionName("Lunch");
        Cart cart = new Cart();
        cart.attachTo(lunch);
        cart.setQuantity(lunch.findItem("dosa").orElseThrow(), 1);
        cart.setQuantity(lunch.findItem("tea").orElseThrow(), 2);

        DailyMenu snacks = new DailyMenu();
        snacks.setId("menu2");
        snacks.setMealSessionName("Snacks");
        snacks.getItems().add(item("tea", "Tea", "12.50", 3, true));   // tea is sold in both sessions

        Cart.Change change = cart.attachTo(snacks);
        assertEquals(List.of("Dosa"), change.removedItems());
        assertEquals(Map.of("tea", 2), cart.getQuantities());
        assertEquals("menu2", cart.getDailyMenuId());
        assertTrue(change.message().contains("The Lunch session has ended and the Snacks menu is now open"));
        assertTrue(change.message().contains("removed from your cart: Dosa"));
        assertNull(cart.attachTo(snacks));   // nothing changes the second time
    }

    @Test
    void removingSetsQuantityToZero() {
        Cart cart = new Cart();
        cart.attachTo(menu());
        cart.setQuantity("dosa", 2);
        cart.setQuantity("dosa", 0);
        assertTrue(cart.isEmpty());
        assertTrue(CartView.calculate(menu(), cart).isEmpty());
    }
}
