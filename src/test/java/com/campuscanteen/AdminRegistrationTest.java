package com.campuscanteen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.campuscanteen.model.Role;
import com.campuscanteen.model.User;
import com.campuscanteen.security.CanteenUserDetails;
import com.campuscanteen.service.AdminRegistrationService;
import com.campuscanteen.service.AdminRegistrationService.NewInvite;
import com.campuscanteen.service.BusinessException;
import com.campuscanteen.web.form.AdminRegistrationForm;

/** Administrator registration (/admin/register), invitations, login and role restrictions. */
@EnabledIfEnvironmentVariable(named = "MONGODB_URI", matches = ".+")
class AdminRegistrationTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired AdminRegistrationService adminRegistrationService;
    @Autowired PasswordEncoder passwordEncoder;

    private ResultActions registerAdmin(String id, String code) throws Exception {
        var request = post("/admin/register").with(csrf())
                .param("collegeId", id).param("name", "Canteen Manager")
                .param("password", "admin-pass-1").param("confirmPassword", "admin-pass-1");
        if (code != null) {
            request.param("code", code);
        }
        return mvc.perform(request);
    }

    @Test
    void firstAdministratorRegistersThenOpenRegistrationCloses() throws Exception {
        mvc.perform(get("/admin/register")).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("No administrator exists yet")));

        registerAdmin("canteenadmin01", null).andExpect(redirectedUrl("/admin/login"));
        User admin = userRepository.findByCollegeId("CANTEENADMIN01").orElseThrow();
        assertEquals(Role.CANTEEN_ADMIN, admin.getRole());
        assertTrue(admin.getPasswordHash().startsWith("$2"), "Password stored as a BCrypt hash");
        assertTrue(passwordEncoder.matches("admin-pass-1", admin.getPasswordHash()));

        // The administrator logs in through the administrator login form and reaches the dashboard.
        mvc.perform(formLogin("/login").userParameter("collegeId").user("canteenadmin01").password("admin-pass-1"))
                .andExpect(authenticated().withRoles("CANTEEN_ADMIN"))
                .andExpect(redirectedUrl("/admin"));
        mvc.perform(get("/admin/dashboard").with(user(new CanteenUserDetails(admin))))
                .andExpect(redirectedUrl("/admin"));

        // Now nobody can register as administrator without an invitation.
        assertFalse(adminRegistrationService.isFirstAdminSetupOpen());
        mvc.perform(get("/admin/register")).andExpect(content().string(Matchers.containsString("invitation code")));
        registerAdmin("INTRUDER1", null).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Enter the invitation code")));
        registerAdmin("INTRUDER2", "AAAA-BBBB-CCCC-DDDD").andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("not valid")));
        // The local-development code is OFF unless CANTEEN_ADMIN_INVITATION_CODE is set.
        assertFalse(adminRegistrationService.isLocalInvitationCodeEnabled("127.0.0.1"));
        registerAdmin("INTRUDER3", "YIC@1234").andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("not valid")));
        assertEquals(1, userRepository.count());
    }

    @Test
    void twoSimultaneousFirstRegistrationsCreateOnlyOneAdministrator() throws Exception {
        int people = 6;
        ExecutorService pool = Executors.newFixedThreadPool(people);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < people; i++) {
            AdminRegistrationForm form = new AdminRegistrationForm();
            form.setCollegeId("FIRST" + i);
            form.setName("Person " + i);
            form.setPassword("admin-pass-1");
            form.setConfirmPassword("admin-pass-1");
            results.add(pool.submit(() -> {
                start.await();
                try {
                    adminRegistrationService.register(form);
                    return true;
                } catch (BusinessException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        int created = 0;
        for (Future<Boolean> f : results) {
            created += f.get() ? 1 : 0;
        }
        pool.shutdown();
        assertEquals(1, created);
        assertEquals(1, adminRegistrationService.listAdmins().size());
    }

    @Test
    void existingAdministratorClosesSetupAtStartup() {
        admin("OLDADMIN");   // e.g. created earlier by the environment-variable setup
        // isFirstAdminSetupOpen already sees the existing administrator...
        assertFalse(adminRegistrationService.isFirstAdminSetupOpen());
        // ...and the startup check records it on the canteen too.
        adminRegistrationService.closeFirstAdminSetupIfAdminExists();
        assertTrue(canteenService.getDefaultCanteen().isFirstAdminRegistered());
    }

    @Test
    void invitationCodeWorksOnceAndExpires() throws Exception {
        User first = admin("ADMIN1");
        adminRegistrationService.closeFirstAdminSetupIfAdminExists();
        CanteenUserDetails firstAdmin = new CanteenUserDetails(first);

        // The administrator creates a code on the Accounts page; it is shown once.
        mvc.perform(post("/admin/accounts/invite").with(user(firstAdmin)).with(csrf()))
                .andExpect(redirectedUrl("/admin/accounts"))
                .andExpect(flash().attributeExists("newInvite"));
        NewInvite invite = adminRegistrationService.createInvite("ADMIN1");
        assertFalse(canteenService.getDefaultCanteen().getAdminInvites().stream()
                .anyMatch(i -> i.getCodeHash().contains(invite.code())), "Only a hash of the code is stored");

        registerAdmin("ADMIN2", invite.code().toLowerCase()).andExpect(redirectedUrl("/admin/login"));
        assertEquals(Role.CANTEEN_ADMIN, userRepository.findByCollegeId("ADMIN2").orElseThrow().getRole());
        // The same code cannot be used twice.
        registerAdmin("ADMIN3", invite.code()).andExpect(content().string(Matchers.containsString("not valid")));

        // Codes expire after 24 hours. Simulate an expired code (expiry just before the application clock's "now").
        NewInvite old = adminRegistrationService.createInvite("ADMIN1");
        assertTrue(old.expiresAt().isAfter(clock.instant().plus(java.time.Duration.ofHours(23))));
        mongo.updateFirst(new org.springframework.data.mongodb.core.query.Query(),
                new org.springframework.data.mongodb.core.query.Update().set("adminInvites.$[].expiresAt",
                        clock.instant().minusSeconds(60)), "canteens");
        registerAdmin("ADMIN4", old.code()).andExpect(content().string(Matchers.containsString("not valid")));
        assertEquals(2, adminRegistrationService.listAdmins().size());

        mvc.perform(get("/admin/accounts").with(user(firstAdmin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Used by ADMIN2")));
    }

    @Test
    void customersCannotCreateInvitationsOrReachAdminPages() throws Exception {
        admin("ADMIN1");
        CanteenUserDetails student = new CanteenUserDetails(customer(Role.STUDENT, "21CS101"));
        CanteenUserDetails faculty = new CanteenUserDetails(customer(Role.FACULTY, "FAC-101"));
        for (CanteenUserDetails customer : List.of(student, faculty)) {
            mvc.perform(post("/admin/accounts/invite").with(user(customer)).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(get("/admin").with(user(customer))).andExpect(status().isForbidden());
            mvc.perform(get("/admin/dashboard").with(user(customer))).andExpect(status().isForbidden());
            mvc.perform(get("/admin/payments").with(user(customer))).andExpect(status().isForbidden());
            mvc.perform(post("/admin/orders/x/cash").with(user(customer)).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(post("/admin/orders/x/verify").with(user(customer)).with(csrf())).andExpect(status().isForbidden());
        }
        assertTrue(canteenService.getDefaultCanteen().getAdminInvites().isEmpty());
    }
}
