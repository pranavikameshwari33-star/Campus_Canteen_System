package com.campuscanteen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import com.campuscanteen.model.DailyMenu;
import com.campuscanteen.model.MealSession;
import com.campuscanteen.model.MenuItem;
import com.campuscanteen.model.Order;
import com.campuscanteen.model.OrderStatus;
import com.campuscanteen.model.PaymentStatus;
import com.campuscanteen.model.Role;
import com.campuscanteen.security.CanteenUserDetails;
import com.campuscanteen.service.PaymentService;

/**
 * Requirement 19: the application starts and every page renders.
 * Also walks through the whole journey in the browser layer: menu -> cart -> checkout ->
 * choose QR Code (order queued with token at once) -> upload screenshot -> admin verifies ->
 * preparing -> ready -> order taken; and the same for a cash order.
 */
@EnabledIfEnvironmentVariable(named = "MONGODB_URI", matches = ".+")
class WebPagesTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired PaymentService paymentService;

    @Test
    void publicPagesRender() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(Matchers.containsString("How it works")));
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/admin/login")).andExpect(status().isOk());
        mvc.perform(get("/register")).andExpect(status().isOk());
        mvc.perform(get("/css/style.css")).andExpect(status().isOk());
    }

    @Test
    void fullOrderJourneyThroughTheWebsite() throws Exception {
        MenuItem dosa = menuItem("Masala Dosa", "45");
        MealSession lunch = lunch();
        DailyMenu menu = publishMenu(lunch, Map.of(dosa, 10));
        CanteenUserDetails student = new CanteenUserDetails(customer(Role.STUDENT, "21CS777"));
        CanteenUserDetails admin = new CanteenUserDetails(admin("ADMIN1"));
        MockHttpSession session = new MockHttpSession();

        // Customer pages
        mvc.perform(get("/home").with(user(student)).session(session)).andExpect(status().isOk());
        mvc.perform(get("/menu").with(user(student)).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Masala Dosa")))
                .andExpect(content().string(Matchers.containsString("₹45.00")));
        mvc.perform(post("/cart/add").with(user(student)).with(csrf()).session(session)
                .param("menuItemId", dosa.getId()).param("quantity", "2"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/cart").with(user(student)).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("₹90.00")));
        // Checkout: server-calculated total with the two payment options beneath it.
        String checkoutPage = mvc.perform(get("/checkout").with(user(student)).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(checkoutPage.contains("Total to pay"));
        assertTrue(checkoutPage.contains("₹90.00"));
        int total = checkoutPage.indexOf("Total to pay");
        assertTrue(checkoutPage.indexOf("Pay by cash") > total && checkoutPage.indexOf("Pay by QR code") > total,
                "Both payment options are shown beneath the total");
        String checkoutKey = checkoutPage.replaceAll("(?s).*name=\"checkoutKey\" value=\"([^\"]+)\".*", "$1");

        // Choosing QR Code places the order and puts it in the queue right away, then shows the QR page.
        mvc.perform(post("/checkout").param("paymentMethod", "QR").param("checkoutKey", checkoutKey)
                        .param("total", "1.00")   // anything the browser sends as a total is ignored
                        .with(user(student)).with(csrf()).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/orders/*/pay"));
        // Pressing the button again (or refreshing) must not create a second order.
        mvc.perform(post("/checkout").param("paymentMethod", "QR").param("checkoutKey", checkoutKey)
                        .with(user(student)).with(csrf()).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/orders/*/pay"));
        assertEquals(1, orderService.listForCustomer(student.getId()).size());

        Order order = orderService.listForCustomer(student.getId()).get(0);
        assertEquals(OrderStatus.QUEUED, order.getOrderStatus());
        assertEquals(PaymentStatus.QR_PAYMENT_PENDING, order.getPaymentStatus());
        assertEquals(0, new BigDecimal("90.00").compareTo(order.getTotalAmount()));
        assertEquals("001", order.getTokenDisplay());

        mvc.perform(get("/orders/" + order.getId() + "/pay").with(user(student)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("testcanteen@bank")))
                .andExpect(content().string(Matchers.containsString("001")))
                .andExpect(content().string(Matchers.containsString("₹90.00")));
        mvc.perform(multipart("/orders/" + order.getId() + "/payment-proof").file(TestImages.png("paid.png"))
                        .param("transactionRef", "UTR998877").with(user(student)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/orders/*"));
        mvc.perform(get("/orders/" + order.getId()).with(user(student)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("001")))
                .andExpect(content().string(Matchers.containsString("QR proof awaiting verification")));
        mvc.perform(get("/orders").with(user(student))).andExpect(status().isOk());
        assertEquals(order.getId(), orderService.listForCustomer(student.getId()).get(0).getId());
        assertEquals(1, orderService.get(order.getId()).getTokenNumber(), "Same order and token throughout");

        // Administrator pages: the order is in the queue with its payment method and status.
        mvc.perform(get("/admin").with(user(admin))).andExpect(status().isOk());
        mvc.perform(get("/admin/queue").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("21CS777")))
                .andExpect(content().string(Matchers.containsString("UPI QR code")))
                .andExpect(content().string(Matchers.containsString("QR proof awaiting verification")));
        mvc.perform(get("/admin/payments").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("UTR998877")));
        mvc.perform(get("/files/payment-proof/" + order.getId()).with(user(admin))).andExpect(status().isOk());
        mvc.perform(post("/admin/orders/" + order.getId() + "/verify").with(user(admin)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(PaymentStatus.VERIFIED, orderService.get(order.getId()).getPaymentStatus());

        for (String next : new String[]{"PREPARING", "READY", "COMPLETED"}) {
            mvc.perform(post("/admin/orders/" + order.getId() + "/status").param("status", next)
                    .with(user(admin)).with(csrf())).andExpect(status().is3xxRedirection());
        }
        assertEquals(OrderStatus.COMPLETED, orderService.get(order.getId()).getOrderStatus());
        mvc.perform(get("/admin/queue").with(user(admin)))
                .andExpect(content().string(Matchers.containsString("The queue is empty")));

        // Remaining administrator pages render with data present
        mvc.perform(get("/admin/orders/" + order.getId()).with(user(admin))).andExpect(status().isOk());
        mvc.perform(get("/admin/history").with(user(admin)))
                .andExpect(status().isOk()).andExpect(content().string(Matchers.containsString(order.getOrderNumber())));
        mvc.perform(get("/admin/reports").with(user(admin)))
                .andExpect(status().isOk()).andExpect(content().string(Matchers.containsString("₹90.00")));
        mvc.perform(get("/admin/menu-items").with(user(admin))).andExpect(status().isOk());
        mvc.perform(get("/admin/menu-items/new").with(user(admin))).andExpect(status().isOk());
        mvc.perform(get("/admin/menu-items/" + dosa.getId() + "/edit").with(user(admin))).andExpect(status().isOk());
        mvc.perform(get("/admin/daily-menu").with(user(admin))).andExpect(status().isOk());
        mvc.perform(get("/admin/daily-menu").param("sessionId", lunch.getId()).with(user(admin)))
                .andExpect(status().isOk()).andExpect(content().string(Matchers.containsString("Published")));
        mvc.perform(get("/admin/sessions").with(user(admin))).andExpect(status().isOk());
        mvc.perform(get("/admin/sessions/" + lunch.getId() + "/edit").with(user(admin))).andExpect(status().isOk());
        mvc.perform(get("/admin/settings").with(user(admin))).andExpect(status().isOk());
        mvc.perform(get("/home").with(user(student))).andExpect(status().isOk());
        assertEquals(menu.getId(), order.getDailyMenuId());
    }

    @Test
    void cashOrderThroughTheWebsite() throws Exception {
        MenuItem meals = menuItem("Veg Meals", "70");
        publishMenu(lunch(), Map.of(meals, 10));
        CanteenUserDetails faculty = new CanteenUserDetails(customer(Role.FACULTY, "FAC-777"));
        CanteenUserDetails admin = new CanteenUserDetails(admin("ADMIN3"));
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/cart/add").with(user(faculty)).with(csrf()).session(session)
                .param("menuItemId", meals.getId()).param("quantity", "1"));
        String checkoutPage = mvc.perform(get("/checkout").with(user(faculty)).session(session))
                .andReturn().getResponse().getContentAsString();
        String checkoutKey = checkoutPage.replaceAll("(?s).*name=\"checkoutKey\" value=\"([^\"]+)\".*", "$1");

        mvc.perform(post("/checkout").param("paymentMethod", "CASH").param("checkoutKey", checkoutKey)
                        .with(user(faculty)).with(csrf()).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/orders/*"));
        Order order = orderService.listForCustomer(faculty.getId()).get(0);
        assertEquals(OrderStatus.QUEUED, order.getOrderStatus());
        assertEquals(PaymentStatus.CASH_DUE, order.getPaymentStatus());
        mvc.perform(get("/orders/" + order.getId()).with(user(faculty)))
                .andExpect(content().string(Matchers.containsString("in cash at the counter")))
                .andExpect(content().string(Matchers.containsString(order.getTokenDisplay())));
        // A cash order has no QR page.
        mvc.perform(get("/orders/" + order.getId() + "/pay").with(user(faculty)))
                .andExpect(redirectedUrl("/orders/" + order.getId()));

        // The queue (also usable on phones: viewport tag and stacked table) shows a cash order with the amount due.
        mvc.perform(get("/admin/queue").with(user(admin)))
                .andExpect(content().string(Matchers.containsString("name=\"viewport\"")))
                .andExpect(content().string(Matchers.containsString("class=\"stack\"")))
                .andExpect(content().string(Matchers.containsString("Cash due at pickup")))
                .andExpect(content().string(Matchers.containsString("₹70.00")));
        mvc.perform(post("/admin/orders/" + order.getId() + "/status").param("status", "READY")
                .with(user(admin)).with(csrf()));
        // "Order taken" alone is refused while the cash is due...
        mvc.perform(post("/admin/orders/" + order.getId() + "/status").param("status", "COMPLETED")
                .with(user(admin)).with(csrf()));
        assertEquals(OrderStatus.READY, orderService.get(order.getId()).getOrderStatus());
        // ...but "Cash received & order taken" records both.
        mvc.perform(post("/admin/orders/" + order.getId() + "/cash-and-taken").with(user(admin)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        Order done = orderService.get(order.getId());
        assertEquals(OrderStatus.COMPLETED, done.getOrderStatus());
        assertEquals(PaymentStatus.CASH_COLLECTED, done.getPaymentStatus());
        assertEquals("ADMIN3", done.getCompletedBy());
        mvc.perform(get("/admin/reports").with(user(admin)))
                .andExpect(content().string(Matchers.containsString("₹70.00")));
    }

    @Test
    void checkoutWithoutChoosingAPaymentMethodPlacesNoOrder() throws Exception {
        MenuItem tea = menuItem("Tea", "10");
        publishMenu(lunch(), Map.of(tea, 10));
        CanteenUserDetails student = new CanteenUserDetails(customer(Role.STUDENT, "21CS779"));
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/cart/add").with(user(student)).with(csrf()).session(session)
                .param("menuItemId", tea.getId()).param("quantity", "1"));
        mvc.perform(post("/checkout").param("checkoutKey", "abcdef123456").with(user(student)).with(csrf()).session(session))
                .andExpect(redirectedUrl("/checkout"));
        mvc.perform(post("/checkout").param("paymentMethod", "FREE").param("checkoutKey", "abcdef123456")
                .with(user(student)).with(csrf()).session(session))
                .andExpect(redirectedUrl("/checkout"));
        assertTrue(orderService.listForCustomer(student.getId()).isEmpty());
    }

    @Test
    void adminFormsSaveThroughTheWebsite() throws Exception {
        CanteenUserDetails admin = new CanteenUserDetails(admin("ADMIN2"));
        mvc.perform(post("/admin/menu-items").with(user(admin)).with(csrf())
                        .param("name", "Veg Noodles").param("category", "Chinese").param("subcategory", "Noodles").param("defaultPrice", "60")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection());
        assertEquals("Veg Noodles", menuItemService.listAll().get(0).getName());
        assertEquals("Noodles", menuItemService.listAll().get(0).getSubcategory());

        // A draft without a price can be added, and an unused draft can be deleted.
        mvc.perform(post("/admin/menu-items").with(user(admin)).with(csrf())
                        .param("name", "Bonda").param("category", "Breakfast").param("active", "true"))
                .andExpect(status().is3xxRedirection());
        MenuItem bonda = menuItemService.findByName("bonda").orElseThrow();
        assertNull(bonda.getDefaultPrice());
        mvc.perform(post("/admin/menu-items/" + bonda.getId() + "/delete").with(user(admin)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertTrue(menuItemService.findByName("Bonda").isEmpty());

        mvc.perform(post("/admin/sessions").with(user(admin)).with(csrf())
                        .param("name", "Breakfast").param("type", "BREAKFAST").param("allowedCategories", "Breakfast")
                        .param("startTime", "07:30").param("endTime", "10:00").param("active", "true"))
                .andExpect(status().is3xxRedirection());
        assertEquals("Breakfast", mealSessionService.listForCanteen(canteen.getId()).get(0).getName());

        // Chinese cannot be added to a breakfast session, even by sending the form directly.
        mvc.perform(post("/admin/sessions").with(user(admin)).with(csrf())
                        .param("name", "Early").param("type", "BREAKFAST").param("allowedCategories", "Breakfast", "Chinese")
                        .param("startTime", "06:00").param("endTime", "07:00").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Chinese items can only be sold in Lunch or Snacks")));

        // Invalid input shows a message instead of an error page.
        mvc.perform(post("/admin/sessions").with(user(admin)).with(csrf())
                        .param("name", "Bad").param("startTime", "25:00").param("endTime", "10:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Start time must look like")));
    }

    @Test
    void menuShowsClearMessageWhenNoSessionIsOpen() throws Exception {
        CanteenUserDetails student = new CanteenUserDetails(customer(Role.STUDENT, "21CS888"));
        mvc.perform(get("/menu").with(user(student)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Ordering is closed right now")));
    }
}
