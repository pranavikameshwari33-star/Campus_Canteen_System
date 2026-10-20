package com.campuscanteen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.campuscanteen.model.DailyMenu;
import com.campuscanteen.model.MenuItem;
import com.campuscanteen.model.Order;
import com.campuscanteen.model.PaymentMethod;
import com.campuscanteen.model.Role;
import com.campuscanteen.model.User;
import com.campuscanteen.security.CanteenUserDetails;
import com.campuscanteen.service.BusinessException;

/** Requirements 1, 2, 3, 16, 17: registration, login, duplicate IDs and access control. */
@EnabledIfEnvironmentVariable(named = "MONGODB_URI", matches = ".+")
class AccountsAndSecurityTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;

    private void register(String role, String collegeId) throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("role", role).param("collegeId", collegeId).param("name", "Asha Rao")
                        .param("department", "CSE").param("year", "STUDENT".equals(role) ? "2" : "")
                        .param("password", "secret-pass-1").param("confirmPassword", "secret-pass-1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void studentCanRegisterAndLogIn() throws Exception {
        register("STUDENT", "21cs101");
        User saved = userRepository.findByCollegeId("21CS101").orElseThrow();
        assertEquals(Role.STUDENT, saved.getRole());
        assertNotEquals("secret-pass-1", saved.getPasswordHash());   // stored as a BCrypt hash
        assertEquals(true, saved.getPasswordHash().startsWith("$2"));

        mvc.perform(formLogin("/login").userParameter("collegeId").user("21cs101").password("secret-pass-1"))
                .andExpect(authenticated().withRoles("STUDENT"))
                .andExpect(redirectedUrl("/home"));
        mvc.perform(formLogin("/login").userParameter("collegeId").user("21CS101").password("wrong-password"))
                .andExpect(unauthenticated());
    }

    @Test
    void facultyCanRegisterAndLogIn() throws Exception {
        register("FACULTY", "FAC-042");
        assertEquals(Role.FACULTY, userRepository.findByCollegeId("FAC-042").orElseThrow().getRole());
        mvc.perform(formLogin("/login").userParameter("collegeId").user("fac-042").password("secret-pass-1"))
                .andExpect(authenticated().withRoles("FACULTY"));
    }

    @Test
    void duplicateCollegeIdIsRejected() throws Exception {
        register("STUDENT", "21CS200");
        mvc.perform(post("/register").with(csrf())
                        .param("role", "FACULTY").param("collegeId", " 21cs200 ").param("name", "Other")
                        .param("department", "ECE").param("password", "another-pass").param("confirmPassword", "another-pass"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("already exists")));
        assertEquals(1, userRepository.count());
        assertThrows(BusinessException.class, () -> customer(Role.STUDENT, "21cs200"));
    }

    @Test
    void registrationCannotCreateAnAdministrator() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("role", "CANTEEN_ADMIN").param("collegeId", "HACKER1").param("name", "X")
                        .param("department", "X").param("password", "password123").param("confirmPassword", "password123"))
                .andExpect(status().isOk());
        assertEquals(0, userRepository.count());
    }

    @Test
    void studentsCannotUseAdministratorPages() throws Exception {
        CanteenUserDetails student = new CanteenUserDetails(customer(Role.STUDENT, "21CS300"));
        mvc.perform(get("/admin/menu-items").with(user(student))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/queue").with(user(student))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/menu-items").with(user(student)).with(csrf())
                        .param("name", "Free food").param("category", "Lunch").param("defaultPrice", "0"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/orders/abc/status").with(user(student)).with(csrf()).param("status", "COMPLETED"))
                .andExpect(status().isForbidden());
        assertEquals(0, menuItemService.listAll().size());
    }

    @Test
    void customersCannotSeeOtherCustomersOrders() throws Exception {
        MenuItem dosa = menuItem("Dosa", "40");
        DailyMenu menu = publishMenu(lunch(), Map.of(dosa, 10));
        User owner = customer(Role.STUDENT, "21CS400");
        User other = customer(Role.FACULTY, "FAC-400");
        Order order = placeOrder(owner, menu, Map.of(dosa, 1), PaymentMethod.QR);
        submitProof(order);

        CanteenUserDetails otherUser = new CanteenUserDetails(other);
        mvc.perform(get("/orders/" + order.getId()).with(user(otherUser))).andExpect(status().isNotFound());
        mvc.perform(get("/files/payment-proof/" + order.getId()).with(user(otherUser))).andExpect(status().isNotFound());

        CanteenUserDetails ownerUser = new CanteenUserDetails(owner);
        mvc.perform(get("/orders/" + order.getId()).with(user(ownerUser))).andExpect(status().isOk());
        mvc.perform(get("/files/payment-proof/" + order.getId()).with(user(ownerUser))).andExpect(status().isOk());
    }

    @Test
    void anonymousUsersAreSentToLogin() throws Exception {
        mvc.perform(get("/menu")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
    }

    @Test
    void unknownIdAndWrongPasswordAreRejectedWithTheSameMessage() throws Exception {
        customer(Role.STUDENT, "21CS500");
        mvc.perform(formLogin("/login").userParameter("collegeId").user("NOSUCHID").password("password123"))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
        mvc.perform(formLogin("/login").userParameter("collegeId").user("21CS500").password("wrong-password"))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
        // An existing ID alone is never enough: an empty password fails too.
        mvc.perform(formLogin("/login").userParameter("collegeId").user("21CS500").password(""))
                .andExpect(unauthenticated());
    }

    @Test
    void administratorCanDisableAndEnableAnAccount() throws Exception {
        User student = customer(Role.STUDENT, "21CS600");
        CanteenUserDetails admin = new CanteenUserDetails(admin("ADMIN6"));
        mvc.perform(get("/admin/accounts").with(user(admin)))
                .andExpect(status().isOk()).andExpect(content().string(Matchers.containsString("21CS600")));
        // The administrator's own account is not listed (it cannot be disabled from this page).
        mvc.perform(get("/admin/accounts").param("q", "admin").with(user(admin)))
                .andExpect(content().string(Matchers.containsString("No accounts found")));

        mvc.perform(post("/admin/accounts/" + student.getId() + "/active").param("active", "false")
                .with(user(admin)).with(csrf())).andExpect(status().is3xxRedirection());
        assertEquals(false, userRepository.findById(student.getId()).orElseThrow().isActive());
        // A disabled account cannot log in, even with the correct password.
        mvc.perform(formLogin("/login").userParameter("collegeId").user("21CS600").password("password123"))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
        // ...and cannot place orders with a session it already had.
        MenuItem tea = menuItem("Tea", "10");
        DailyMenu menu = publishMenu(lunch(), Map.of(tea, 5));
        assertThrows(BusinessException.class, () -> placeOrder(student, menu, Map.of(tea, 1)));

        mvc.perform(post("/admin/accounts/" + student.getId() + "/active").param("active", "true")
                .with(user(admin)).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(formLogin("/login").userParameter("collegeId").user("21CS600").password("password123"))
                .andExpect(authenticated().withRoles("STUDENT"));
    }

    @Test
    void administratorAccountsCannotBeChangedFromTheAccountsPage() throws Exception {
        User otherAdmin = admin("ADMIN7");
        CanteenUserDetails admin = new CanteenUserDetails(admin("ADMIN8"));
        mvc.perform(post("/admin/accounts/" + otherAdmin.getId() + "/active").param("active", "false")
                .with(user(admin)).with(csrf())).andExpect(status().isNotFound());
        assertEquals(true, userRepository.findById(otherAdmin.getId()).orElseThrow().isActive());
    }

    @Test
    void studentsCannotManageAccounts() throws Exception {
        User student = customer(Role.STUDENT, "21CS700");
        CanteenUserDetails studentUser = new CanteenUserDetails(student);
        mvc.perform(get("/admin/accounts").with(user(studentUser))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/accounts/" + student.getId() + "/active").param("active", "true")
                .with(user(studentUser)).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void formsWithoutCsrfTokenAreRejected() throws Exception {
        mvc.perform(post("/register").param("role", "STUDENT").param("collegeId", "21CS999"))
                .andExpect(status().isForbidden());
    }
}
