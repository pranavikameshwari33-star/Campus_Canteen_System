package com.campuscanteen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import com.campuscanteen.config.AppProperties;
import com.campuscanteen.config.DatabaseInitializer;
import com.campuscanteen.model.Canteen;
import com.campuscanteen.model.DailyMenu;
import com.campuscanteen.model.MealSession;
import com.campuscanteen.model.MealType;
import com.campuscanteen.model.MenuItem;
import com.campuscanteen.model.Order;
import com.campuscanteen.model.PaymentMethod;
import com.campuscanteen.model.Role;
import com.campuscanteen.model.User;
import com.campuscanteen.repository.UserRepository;
import com.campuscanteen.service.CanteenService;
import com.campuscanteen.service.Cart;
import com.campuscanteen.service.DailyMenuService;
import com.campuscanteen.service.DailyMenuService.SaveAction;
import com.campuscanteen.service.MealSessionService;
import com.campuscanteen.service.MenuItemService;
import com.campuscanteen.service.OrderService;
import com.campuscanteen.service.UserService;
import com.campuscanteen.web.form.DailyMenuForm;
import com.campuscanteen.web.form.MealSessionForm;
import com.campuscanteen.web.form.MenuItemForm;
import com.campuscanteen.web.form.RegistrationForm;

/**
 * Base class for tests that use a real MongoDB server (from MONGODB_URI, local or Atlas).
 *
 * Safety: tests use the separate database "campus_canteen_test" and refuse to run if the
 * database name does not end with "_test", so your real data is never touched.
 * Each test class is skipped when MONGODB_URI is not set (@EnabledIfEnvironmentVariable).
 *
 * The clock is set to 12:00 today (canteen time zone) before each test and keeps ticking, so a
 * "Lunch" session from 11:00 to 14:00 is open. Tests can move it with clock.setTime(...).
 */
@SpringBootTest(properties = {
        "spring.mongodb.database=campus_canteen_test",
        "app.upload-dir=target/test-uploads",
        "app.admin-setup.college-id=",
        "app.admin-setup.invitation-code="
})
@AutoConfigureMockMvc
@Import(IntegrationTestBase.TestClockConfig.class)
abstract class IntegrationTestBase {

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        AdjustableClock testClock(AppProperties properties) {
            return new AdjustableClock(ZoneId.of(properties.timezone()));
        }
    }

    /** A clock that runs normally but can be set to any time of today, e.g. 11:59 or 15:00. */
    static class AdjustableClock extends Clock {
        private final ZoneId zone;
        private volatile Duration offset = Duration.ZERO;

        AdjustableClock(ZoneId zone) {
            this.zone = zone;
        }

        void setTime(LocalTime time) {
            offset = Duration.between(Instant.now(), LocalDate.now(zone).atTime(time).atZone(zone).toInstant());
        }

        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return Clock.offset(Clock.system(zone), offset); }
        @Override public Instant instant() { return Instant.now().plus(offset); }
    }

    @Autowired protected AdjustableClock clock;

    @Autowired protected MongoTemplate mongo;
    @Autowired protected CanteenService canteenService;
    @Autowired protected UserService userService;
    @Autowired protected UserRepository userRepository;
    @Autowired protected MenuItemService menuItemService;
    @Autowired protected MealSessionService mealSessionService;
    @Autowired protected DailyMenuService dailyMenuService;
    @Autowired protected OrderService orderService;

    protected Canteen canteen;

    @BeforeEach
    void resetTestDatabase() {
        clock.setTime(LocalTime.NOON);
        String dbName = mongo.getDb().getName();
        assertTrue(dbName.endsWith("_test"), "Refusing to clear a non-test database: " + dbName);
        for (String collection : DatabaseInitializer.COLLECTIONS) {
            mongo.remove(new Query(), collection);
        }
        canteenService.ensureDefaultCanteen();
        canteen = canteenService.getDefaultCanteen();
        canteen.setUpiId("testcanteen@bank");
        canteenService.save(canteen);
    }

    // ---------- helpers ----------

    protected User customer(Role role, String collegeId) {
        RegistrationForm form = new RegistrationForm();
        form.setRole(role);
        form.setCollegeId(collegeId);
        form.setName("Test " + collegeId);
        form.setDepartment("CSE");
        form.setYear(role == Role.STUDENT ? 2 : null);
        form.setPassword("password123");
        form.setConfirmPassword("password123");
        return userService.registerCustomer(form);
    }

    protected User admin(String collegeId) {
        return userService.createAdmin(collegeId, "Admin " + collegeId, "adminpass123", canteen.getId());
    }

    protected MenuItem menuItem(String name, String price) {
        MenuItemForm form = new MenuItemForm();
        form.setName(name);
        form.setCategory(MenuItem.LUNCH);
        form.setDefaultPrice(new BigDecimal(price));
        return menuItemService.save(form);
    }

    protected MealSession lunch() {
        MealSessionForm form = new MealSessionForm();
        form.setName("Lunch");
        form.setType(MealType.LUNCH);
        form.setStartTime("11:00");
        form.setEndTime("14:00");
        form.setAllowedCategories(List.of(MenuItem.LUNCH, MenuItem.CHINESE));
        return mealSessionService.save(canteen.getId(), form);
    }

    /** Publishes today's lunch menu with the given items and quantities (item -> quantity). */
    protected DailyMenu publishMenu(MealSession session, Map<MenuItem, Integer> stock) {
        DailyMenuForm form = dailyMenuService.buildForm(canteen.getId(), dailyMenuService.today(), session.getId());
        for (DailyMenuForm.Row row : form.getRows()) {
            stock.forEach((item, quantity) -> {
                if (item.getId().equals(row.getMenuItemId())) {
                    row.setIncluded(true);
                    row.setQuantity(quantity);
                }
            });
        }
        return dailyMenuService.save(canteen.getId(), form, "ADMIN1", SaveAction.PUBLISH);
    }

    protected Cart cart(DailyMenu menu, Map<MenuItem, Integer> quantities) {
        Cart cart = new Cart();
        cart.attachTo(menu);
        quantities.forEach((item, quantity) -> cart.setQuantity(item.getId(), quantity));
        return cart;
    }

    /** Places a cash order (the customer pressed "Pay by cash" on a fresh checkout page). */
    protected Order placeOrder(User user, DailyMenu menu, Map<MenuItem, Integer> quantities) {
        return placeOrder(user, menu, quantities, PaymentMethod.CASH);
    }

    protected Order placeOrder(User user, DailyMenu menu, Map<MenuItem, Integer> quantities, PaymentMethod method) {
        return orderService.placeOrder(user.getId(), cart(menu, quantities), method, UUID.randomUUID().toString());
    }

    /** The customer uploads a payment screenshot for a QR order. */
    protected void submitProof(Order order) {
        orderService.submitPaymentProof(order.getId(), order.getUserId(), TestImages.png("proof.png"), "UTR123");
    }
}
