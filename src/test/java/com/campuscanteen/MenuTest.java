package com.campuscanteen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.campuscanteen.model.DailyMenu;
import com.campuscanteen.model.DailyMenuItem;
import com.campuscanteen.model.MealSession;
import com.campuscanteen.model.MealType;
import com.campuscanteen.model.MenuItem;
import com.campuscanteen.service.BusinessException;
import com.campuscanteen.service.DailyMenuService.SaveAction;
import com.campuscanteen.web.form.DailyMenuForm;
import com.campuscanteen.web.form.MealSessionForm;

/** Requirements 5, 6, 7, 18: catalogue, meal sessions and daily menus stored in MongoDB. */
@EnabledIfEnvironmentVariable(named = "MONGODB_URI", matches = ".+")
class MenuTest extends IntegrationTestBase {

    @Test
    void databaseConnectionWorksWithTheEnvironmentVariable() {
        Document reply = mongo.getDb().runCommand(new Document("ping", 1));
        assertEquals(1.0, ((Number) reply.get("ok")).doubleValue());
        assertEquals("campus_canteen_test", mongo.getDb().getName());
        for (String name : List.of("users", "canteens", "menuItems", "mealSessions", "dailyMenus", "orders", "payments", "tokenCounters")) {
            assertTrue(mongo.collectionExists(name), "Missing collection " + name);
        }
    }

    @Test
    void menuItemsAreStoredAndReadBack() {
        MenuItem idli = menuItem("Idli", "30");
        MenuItem stored = menuItemService.get(idli.getId());
        assertEquals("Idli", stored.getName());
        assertEquals(0, new BigDecimal("30.00").compareTo(stored.getDefaultPrice()));

        menuItemService.setActive(idli.getId(), false);
        assertTrue(menuItemService.listActive().isEmpty());
        assertEquals(1, menuItemService.listAll().size());   // deactivated, not deleted

        Document raw = mongo.getCollection("menuItems").find().first();
        assertEquals(false, raw.get("isActive"));
    }

    @Test
    void openSessionFollowsConfiguredTimings() {
        MealSession lunch = lunch();   // 11:00-14:00, and the test clock says 12:00
        assertEquals(lunch.getId(), mealSessionService.findOpenSession(canteen).orElseThrow().getId());

        MealSessionForm form = new MealSessionForm();
        form.setId(lunch.getId());
        form.setName("Lunch");
        form.setStartTime("13:00");
        form.setEndTime("15:00");
        form.setType(MealType.LUNCH);
        form.setAllowedCategories(List.of(MenuItem.LUNCH));
        mealSessionService.save(canteen.getId(), form);
        assertTrue(mealSessionService.findOpenSession(canteen).isEmpty());
    }

    @Test
    void overlappingSessionsAreRejected() {
        lunch();
        MealSessionForm form = new MealSessionForm();
        form.setName("Brunch");
        form.setStartTime("10:00");
        form.setEndTime("11:30");
        form.setAllowedCategories(List.of(MenuItem.BREAKFAST));
        BusinessException error = assertThrows(BusinessException.class, () -> mealSessionService.save(canteen.getId(), form));
        assertTrue(error.getMessage().contains("overlap"), error.getMessage());
    }

    @Test
    void adminCanPublishAndUpdateDailyMenu() {
        MenuItem dosa = menuItem("Dosa", "40");
        MenuItem tea = menuItem("Tea", "10");
        MealSession lunch = lunch();

        DailyMenu menu = publishMenu(lunch, Map.of(dosa, 20));
        assertTrue(menu.isPublished());
        assertEquals("ADMIN1", menu.getPublishedBy());
        assertEquals(1, menu.getItems().size());
        assertTrue(dailyMenuService.findPublishedMenu(canteen.getId(), lunch).isPresent());

        // Change today's price and quantity, add tea, mark dosa sold out.
        DailyMenuForm form = dailyMenuService.buildForm(canteen.getId(), dailyMenuService.today(), lunch.getId());
        for (DailyMenuForm.Row row : form.getRows()) {
            if (row.getMenuItemId().equals(dosa.getId())) {
                row.setPrice(new BigDecimal("45"));
                row.setAvailable(false);
            } else if (row.getMenuItemId().equals(tea.getId())) {
                row.setIncluded(true);
                row.setQuantity(50);
            }
        }
        DailyMenu updated = dailyMenuService.save(canteen.getId(), form, "ADMIN1", SaveAction.SAVE);

        DailyMenuItem dosaToday = updated.findItem(dosa.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("45.00").compareTo(dosaToday.getPriceForThatDay()));
        assertFalse(dosaToday.isAvailable());
        assertEquals(20, dosaToday.getAvailableQuantity());
        assertEquals(50, updated.findItem(tea.getId()).orElseThrow().getAvailableQuantity());
        // The catalogue price is unchanged: daily prices are separate.
        assertEquals(0, new BigDecimal("40.00").compareTo(menuItemService.get(dosa.getId()).getDefaultPrice()));

        // Field names in MongoDB follow the agreed schema.
        Document raw = mongo.getCollection("dailyMenus").find().first();
        Document firstItem = raw.getList("items", Document.class).get(0);
        assertTrue(raw.containsKey("isPublished"));
        assertTrue(firstItem.containsKey("isAvailable"));
        assertTrue(firstItem.containsKey("priceForThatDay"));

        // Unpublish hides it from customers.
        dailyMenuService.save(canteen.getId(), dailyMenuService.buildForm(canteen.getId(), dailyMenuService.today(), lunch.getId()),
                "ADMIN1", SaveAction.UNPUBLISH);
        assertTrue(dailyMenuService.findPublishedMenu(canteen.getId(), lunch).isEmpty());
    }

    @Test
    void unchangedQuantityDoesNotUndoOrdersPlacedWhileEditing() {
        MenuItem dosa = menuItem("Dosa", "40");
        MealSession lunch = lunch();
        DailyMenu menu = publishMenu(lunch, Map.of(dosa, 10));

        DailyMenuForm openEditor = dailyMenuService.buildForm(canteen.getId(), dailyMenuService.today(), lunch.getId());
        assertTrue(dailyMenuService.reserveStock(menu.getId(), dosa.getId(), 3));   // a customer orders meanwhile
        dailyMenuService.save(canteen.getId(), openEditor, "ADMIN1", SaveAction.PUBLISH);

        assertEquals(7, dailyMenuService.get(menu.getId()).findItem(dosa.getId()).orElseThrow().getAvailableQuantity());
    }
}
