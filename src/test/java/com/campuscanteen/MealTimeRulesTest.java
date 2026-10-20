package com.campuscanteen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.web.servlet.MockMvc;

import com.campuscanteen.config.StarterMenuSetup;
import com.campuscanteen.model.DailyMenu;
import com.campuscanteen.model.DailyMenuItem;
import com.campuscanteen.model.MealSession;
import com.campuscanteen.model.MealType;
import com.campuscanteen.model.MenuItem;
import com.campuscanteen.model.Order;
import com.campuscanteen.model.PaymentMethod;
import com.campuscanteen.model.Role;
import com.campuscanteen.model.User;
import com.campuscanteen.security.CanteenUserDetails;
import com.campuscanteen.service.BusinessException;
import com.campuscanteen.service.Cart;
import com.campuscanteen.service.CurrentMenuService;
import com.campuscanteen.service.DailyMenuService.SaveAction;
import com.campuscanteen.web.form.DailyMenuForm;
import com.campuscanteen.web.form.MealSessionForm;
import com.campuscanteen.web.form.MenuItemForm;

/**
 * The time-based menu rules: Breakfast before 12:00, Lunch 12:00-15:00 (with Chinese),
 * Snacks from 15:00 until closing (with Chinese), Chinese never at breakfast, draft items,
 * and cart checks when the session changes. The test clock is moved to exact times.
 */
@EnabledIfEnvironmentVariable(named = "MONGODB_URI", matches = ".+")
class MealTimeRulesTest extends IntegrationTestBase {

    @Autowired private StarterMenuSetup starterMenuSetup;
    @Autowired private CurrentMenuService currentMenuService;
    @Autowired private MockMvc mvc;

    @BeforeEach
    void addDefaults() {
        starterMenuSetup.run();   // default sessions + draft food list, exactly as on a new database
    }

    private MealSession session(String name) {
        return mealSessionService.listForCanteen(canteen.getId()).stream()
                .filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    private MenuItem item(String name) {
        return menuItemService.findByName(name).orElseThrow();
    }

    private String openSessionAt(LocalTime time) {
        clock.setTime(time);
        MealSession open = currentMenuService.current().openSession();
        return open == null ? "none" : open.getName();
    }

    /** Publishes today's menu for a session: item name -> price, each with the given quantity. */
    private DailyMenu publish(MealSession session, Map<String, String> prices, int quantity) {
        DailyMenuForm form = dailyMenuService.buildForm(canteen.getId(), dailyMenuService.today(), session.getId());
        for (DailyMenuForm.Row row : form.getRows()) {
            String name = menuItemService.get(row.getMenuItemId()).getName();
            if (prices.containsKey(name)) {
                row.setIncluded(true);
                row.setPrice(new BigDecimal(prices.get(name)));
                row.setQuantity(quantity);
            }
        }
        return dailyMenuService.save(canteen.getId(), form, "ADMIN1", SaveAction.PUBLISH);
    }

    private Cart cartFor(DailyMenu menu, Map<String, Integer> quantities) {
        Cart cart = new Cart();
        cart.attachTo(menu);
        quantities.forEach((name, quantity) ->
                cart.setQuantity(menu.findItem(item(name).getId()).orElseThrow(), quantity));
        return cart;
    }

    private Order order(User user, Cart cart) {
        return orderService.placeOrder(user.getId(), cart, PaymentMethod.CASH, java.util.UUID.randomUUID().toString());
    }

    // ---------- Timings ----------

    @Test
    void defaultSessionsFollowTheExactBoundaries() {
        assertEquals("none", openSessionAt(LocalTime.of(7, 29)));        // canteen not open yet
        assertEquals("Breakfast", openSessionAt(LocalTime.of(7, 30)));
        assertEquals("Breakfast", openSessionAt(LocalTime.of(9, 0)));
        assertEquals("Breakfast", openSessionAt(LocalTime.of(11, 59, 59)));
        assertEquals("Lunch", openSessionAt(LocalTime.of(12, 0)));         // lunch starts exactly at 12:00
        assertEquals("Lunch", openSessionAt(LocalTime.of(12, 30)));
        assertEquals("Lunch", openSessionAt(LocalTime.of(14, 59, 59)));
        assertEquals("Snacks", openSessionAt(LocalTime.of(15, 0)));        // snacks start exactly at 15:00
        assertEquals("Snacks", openSessionAt(LocalTime.of(15, 30)));
        assertEquals("Snacks", openSessionAt(LocalTime.of(17, 59)));
        assertEquals("none", openSessionAt(LocalTime.of(18, 0)));          // closing time
        assertFalse(currentMenuService.current().canteenOpen());

        assertEquals(MealType.BREAKFAST, session("Breakfast").getType());
        assertEquals(List.of(MenuItem.BREAKFAST), session("Breakfast").getEffectiveCategories());
        assertEquals(List.of(MenuItem.LUNCH, MenuItem.CHINESE), session("Lunch").getEffectiveCategories());
        assertEquals(List.of(MenuItem.SNACKS, MenuItem.CHINESE), session("Snacks").getEffectiveCategories());
    }

    @Test
    void closingTimeFromSettingsEndsOrdering() {
        canteen = canteenService.getDefaultCanteen();
        canteen.setClosingTime("17:00");
        canteenService.save(canteen);
        assertEquals("Snacks", openSessionAt(LocalTime.of(16, 59)));
        assertEquals("none", openSessionAt(LocalTime.of(17, 0)));
    }

    // ---------- Starter data ----------

    @Test
    void starterMenuIsAddedOnceAsDraftsAndRestartsKeepAdminChanges() {
        List<MenuItem> items = menuItemService.listAll();
        assertEquals(StarterMenuSetup.STARTER_ITEMS.size(), items.size());
        assertEquals(60, items.size());
        assertTrue(items.stream().allMatch(i -> i.getDefaultPrice() == null), "no invented prices");
        assertEquals(List.of("Bonda", "Karam Dosa", "Punugulu", "Ajwain Pakoda").stream().sorted().toList(),
                items.stream().filter(MenuItem::isNameNeedsConfirmation).map(MenuItem::getName).sorted().toList());
        assertEquals("Noodles", item("Veg Hakka Noodles").getSubcategory());
        assertEquals("Starters", item("Gobi 65").getSubcategory());
        assertEquals(3, mealSessionService.listForCanteen(canteen.getId()).size());

        // The administrator corrects a name, sets a price, deletes a draft and changes a timing ...
        MenuItemForm karam = menuItemService.toForm(item("Karam Dosa"));
        karam.setName("Kaaram Dosa");
        karam.setNameNeedsConfirmation(false);
        menuItemService.save(karam);
        MenuItemForm idly = menuItemService.toForm(item("Idly"));
        idly.setDefaultPrice(new BigDecimal("30"));
        menuItemService.save(idly);
        menuItemService.delete(item("Bonda").getId());
        MealSessionForm lunch = mealSessionService.toForm(session("Lunch"));
        lunch.setEndTime("14:45");
        mealSessionService.save(canteen.getId(), lunch);

        // ... and the application restarts (the setup runs again).
        starterMenuSetup.run();
        starterMenuSetup.run();

        assertEquals(59, menuItemService.listAll().size(), "nothing duplicated or re-added");
        assertTrue(menuItemService.findByName("Karam Dosa").isEmpty());
        assertTrue(menuItemService.findByName("Bonda").isEmpty());
        assertEquals(0, new BigDecimal("30.00").compareTo(item("Idly").getDefaultPrice()));
        assertEquals(3, mealSessionService.listForCanteen(canteen.getId()).size());
        assertEquals("14:45", session("Lunch").getEndTime());
    }

    // ---------- Chinese rules ----------

    @Test
    void chineseCanNeverBeSoldAtBreakfast() {
        MealSession breakfast = session("Breakfast");

        // Not offered in the breakfast editor ...
        DailyMenuForm form = dailyMenuService.buildForm(canteen.getId(), dailyMenuService.today(), breakfast.getId());
        assertTrue(form.getRows().stream().allMatch(r -> MenuItem.BREAKFAST.equals(r.getCategory())));

        // ... refused if sent anyway ...
        DailyMenuForm.Row noodles = new DailyMenuForm.Row();
        noodles.setMenuItemId(item("Veg Noodles").getId());
        noodles.setIncluded(true);
        noodles.setPrice(new BigDecimal("60"));
        noodles.setQuantity(10);
        form.getRows().add(noodles);
        BusinessException error = assertThrows(BusinessException.class,
                () -> dailyMenuService.save(canteen.getId(), form, "ADMIN1", SaveAction.PUBLISH));
        assertTrue(error.getMessage().contains("cannot be sold during Breakfast"), error.getMessage());

        // ... the breakfast session cannot be configured to allow Chinese ...
        MealSessionForm sessionForm = mealSessionService.toForm(breakfast);
        sessionForm.setAllowedCategories(List.of(MenuItem.BREAKFAST, MenuItem.CHINESE));
        assertThrows(BusinessException.class, () -> mealSessionService.save(canteen.getId(), sessionForm));

        // ... and even a Chinese item written straight into the database is never shown or sold.
        DailyMenu menu = publish(breakfast, Map.of("Idly", "30"), 10);
        DailyMenuItem sneaked = new DailyMenuItem();
        sneaked.setMenuItemId(item("Veg Noodles").getId());
        sneaked.setName("Veg Noodles");
        sneaked.setCategory(MenuItem.CHINESE);
        sneaked.setPriceForThatDay(new BigDecimal("60.00"));
        sneaked.setAvailableQuantity(10);
        mongo.updateFirst(new Query(Criteria.where("id").is(menu.getId())), new Update().push("items", sneaked), DailyMenu.class);
        clock.setTime(LocalTime.of(9, 0));
        DailyMenu shown = currentMenuService.current().menu();
        assertEquals(List.of("Idly"), shown.getItems().stream().map(DailyMenuItem::getName).toList());
    }

    @Test
    void chineseCanBeOrderedAtLunchAndSnacks() {
        User student = customer(Role.STUDENT, "21CS501");
        User faculty = customer(Role.FACULTY, "FAC-501");
        DailyMenu lunch = publish(session("Lunch"), Map.of("Meals", "80", "Veg Noodles", "60"), 10);
        DailyMenu snacks = publish(session("Snacks"), Map.of("Mirchi Bajji", "20", "Veg Noodles", "65"), 10);

        clock.setTime(LocalTime.of(12, 30));
        Order lunchOrder = order(student, cartFor(lunch, Map.of("Meals", 1, "Veg Noodles", 1)));
        assertEquals(0, new BigDecimal("140.00").compareTo(lunchOrder.getTotalAmount()));

        clock.setTime(LocalTime.of(15, 30));
        Order snackOrder = order(faculty, cartFor(snacks, Map.of("Mirchi Bajji", 2, "Veg Noodles", 1)));
        assertEquals(0, new BigDecimal("105.00").compareTo(snackOrder.getTotalAmount()));
        assertEquals("Snacks", snackOrder.getMealSessionName());
    }

    @Test
    void customerMenuPageFollowsTheClock() throws Exception {
        CanteenUserDetails student = new CanteenUserDetails(customer(Role.STUDENT, "21CS502"));
        publish(session("Breakfast"), Map.of("Masala Dosa", "40"), 10);
        publish(session("Lunch"), Map.of("Meals", "80", "Veg Noodles", "60"), 10);

        clock.setTime(LocalTime.of(9, 0));
        mvc.perform(get("/menu").with(user(student))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Masala Dosa")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Veg Noodles"))));

        clock.setTime(LocalTime.of(12, 30));
        mvc.perform(get("/menu").with(user(student))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Chinese - Noodles")))
                .andExpect(content().string(Matchers.containsString("Veg Noodles")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Masala Dosa"))));

        clock.setTime(LocalTime.of(15, 30));   // snacks menu not published
        mvc.perform(get("/menu").with(user(student))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("has not been published yet")));
    }

    // ---------- Drafts, prices, stock ----------

    @Test
    void missingOrInvalidPricesQuantitiesAndUnconfirmedNamesBlockPublishing() {
        MealSession lunch = session("Lunch");
        assertThrows(BusinessException.class, () -> publishWithRow(lunch, "Meals", null, 10), "draft without price");
        assertThrows(BusinessException.class, () -> publishWithRow(lunch, "Meals", "0", 10), "zero price");
        assertThrows(BusinessException.class, () -> publishWithRow(lunch, "Meals", "-5", 10), "negative price");
        assertThrows(BusinessException.class, () -> publishWithRow(lunch, "Meals", "80", null), "missing quantity");
        BusinessException unconfirmed = assertThrows(BusinessException.class,
                () -> publishWithRow(session("Snacks"), "Punugulu", "25", 10));
        assertTrue(unconfirmed.getMessage().contains("needs to be confirmed"), unconfirmed.getMessage());
        assertTrue(dailyMenuService.find(canteen.getId(), dailyMenuService.today(), lunch.getId()).isEmpty());

        publishWithRow(lunch, "Meals", "80", 10);   // valid
        assertTrue(dailyMenuService.findPublishedMenu(canteen.getId(), lunch).isPresent());
    }

    private DailyMenu publishWithRow(MealSession session, String name, String price, Integer quantity) {
        DailyMenuForm form = dailyMenuService.buildForm(canteen.getId(), dailyMenuService.today(), session.getId());
        for (DailyMenuForm.Row row : form.getRows()) {
            if (row.getMenuItemId().equals(item(name).getId())) {
                row.setIncluded(true);
                row.setPrice(price == null ? null : new BigDecimal(price));
                row.setQuantity(quantity);
            }
        }
        return dailyMenuService.save(canteen.getId(), form, "ADMIN1", SaveAction.PUBLISH);
    }

    @Test
    void unpublishedAndSoldOutItemsCannotBeOrdered() {
        User student = customer(Role.STUDENT, "21CS503");
        MealSession lunch = session("Lunch");
        DailyMenu menu = publish(lunch, Map.of("Meals", "80", "Jeera Rice", "50"), 1);
        Cart cart = cartFor(menu, Map.of("Meals", 1));

        // Unpublished: no ordering.
        dailyMenuService.save(canteen.getId(), dailyMenuService.buildForm(canteen.getId(), dailyMenuService.today(), lunch.getId()),
                "ADMIN1", SaveAction.UNPUBLISH);
        BusinessException closed = assertThrows(BusinessException.class, () -> order(student, cart));
        assertEquals("The menu for this meal session has not been published yet.", closed.getMessage());

        // Published again, then the only portion of Meals is sold: the next customer cannot buy it.
        publish(lunch, Map.of("Meals", "80", "Jeera Rice", "50"), 1);
        order(student, cartFor(menu, Map.of("Meals", 1)));
        User other = customer(Role.FACULTY, "FAC-503");
        assertThrows(BusinessException.class, () -> order(other, cartFor(menu, Map.of("Meals", 1))));
    }

    // ---------- Session change with a filled cart ----------

    @Test
    void cartIsCheckedAgainWhenLunchChangesToSnacks() {
        User student = customer(Role.STUDENT, "21CS504");
        DailyMenu lunch = publish(session("Lunch"), Map.of("Meals", "80", "Veg Noodles", "60"), 10);
        publish(session("Snacks"), Map.of("Mirchi Bajji", "20", "Veg Noodles", "65"), 10);

        clock.setTime(LocalTime.of(14, 50));
        Cart cart = cartFor(lunch, Map.of("Meals", 1, "Veg Noodles", 2));

        clock.setTime(LocalTime.of(15, 5));   // lunch has ended before checkout
        BusinessException changed = assertThrows(BusinessException.class, () -> order(student, cart));
        assertTrue(changed.getMessage().contains("The Lunch session has ended and the Snacks menu is now open"), changed.getMessage());
        assertTrue(changed.getMessage().contains("removed from your cart: Meals"), changed.getMessage());
        assertEquals(Map.of(item("Veg Noodles").getId(), 2), cart.getQuantities());
        assertEquals(10, lunch.findItem(item("Veg Noodles").getId()).orElseThrow().getAvailableQuantity());
        assertEquals(10, dailyMenuService.get(lunch.getId()).findItem(item("Veg Noodles").getId()).orElseThrow()
                .getAvailableQuantity(), "no lunch stock was taken");

        // After reviewing, the customer orders: the snacks price (65) is used, calculated on the server.
        Order order = order(student, cart);
        assertEquals(0, new BigDecimal("130.00").compareTo(order.getTotalAmount()));
        assertEquals("Snacks", order.getMealSessionName());
        assertEquals(1, order.getTokenNumber(), "the token is issued as soon as the order is placed");
    }
}
