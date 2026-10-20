package com.campuscanteen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.campuscanteen.config.AppProperties;
import com.campuscanteen.model.Role;
import com.campuscanteen.model.User;
import com.campuscanteen.security.CanteenUserDetails;
import com.campuscanteen.service.AdminRegistrationService;
import com.campuscanteen.service.AdminRegistrationService.NewInvite;
import com.mongodb.ConnectionString;

/** The reusable local-development invitation code (CANTEEN_ADMIN_INVITATION_CODE). */
@EnabledIfEnvironmentVariable(named = "MONGODB_URI", matches = "mongodb://(localhost|127\\.0\\.0\\.1)[:/].*")
@TestPropertySource(properties = "app.admin-setup.invitation-code=YIC@1234")
class LocalInvitationCodeTest extends IntegrationTestBase {

    private static final String CODE = "YIC@1234";

    @Autowired MockMvc mvc;
    @Autowired AdminRegistrationService adminRegistrationService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AppProperties properties;
    @Autowired Clock appClock;

    private ResultActions registerAdmin(String id, String code, String remoteAddress) throws Exception {
        return mvc.perform(post("/admin/register").with(csrf())
                .with(request -> { request.setRemoteAddr(remoteAddress); return request; })
                .param("code", code).param("collegeId", id).param("name", "Local Admin " + id)
                .param("password", "my-new-pass-1").param("confirmPassword", "my-new-pass-1"));
    }

    /** An administrator already exists (like CANTEENADMIN01 whose password was forgotten), plus a student. */
    private User existingAdmin() {
        User old = admin("CANTEENADMIN01");
        adminRegistrationService.closeFirstAdminSetupIfAdminExists();
        return old;
    }

    @Test
    void codeRegistersAdministratorsRepeatedlyWithoutTouchingExistingAccounts() throws Exception {
        User old = existingAdmin();
        User student = customer(Role.STUDENT, "25251A0599");

        mvc.perform(get("/admin/register")).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("CANTEEN_ADMIN_INVITATION_CODE")))
                .andExpect(content().string(Matchers.not(Matchers.containsString(CODE))));

        registerAdmin("canteenadmin02", CODE, "127.0.0.1").andExpect(redirectedUrl("/admin/login"));
        // Reusable: the same code works again (also from an IPv6 localhost address).
        registerAdmin("CANTEENADMIN03", " " + CODE + " ", "0:0:0:0:0:0:0:1").andExpect(redirectedUrl("/admin/login"));

        for (String id : new String[] {"CANTEENADMIN02", "CANTEENADMIN03"}) {
            User admin = userRepository.findByCollegeId(id).orElseThrow();
            assertEquals(Role.CANTEEN_ADMIN, admin.getRole());
            assertEquals(canteen.getId(), admin.getCanteenId());
            assertTrue(admin.getPasswordHash().startsWith("$2"), "Password stored as a BCrypt hash");
            assertNotEquals("my-new-pass-1", admin.getPasswordHash());
            assertTrue(passwordEncoder.matches("my-new-pass-1", admin.getPasswordHash()));
        }
        // The new administrator logs in and reaches the dashboard.
        mvc.perform(formLogin("/login").userParameter("collegeId").user("canteenadmin02").password("my-new-pass-1"))
                .andExpect(authenticated().withRoles("CANTEEN_ADMIN"))
                .andExpect(redirectedUrl("/admin"));
        User newAdmin = userRepository.findByCollegeId("CANTEENADMIN02").orElseThrow();
        mvc.perform(get("/admin").with(user(new CanteenUserDetails(newAdmin)))).andExpect(status().isOk());

        // Existing accounts are unchanged, an existing ID cannot be taken over, and nothing is stored for the code.
        registerAdmin("CANTEENADMIN01", CODE, "127.0.0.1").andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("already exists")));
        User oldAfter = userRepository.findByCollegeId("CANTEENADMIN01").orElseThrow();
        assertEquals(old.getPasswordHash(), oldAfter.getPasswordHash());
        assertEquals(old.getName(), oldAfter.getName());
        assertEquals(Role.CANTEEN_ADMIN, oldAfter.getRole());
        assertEquals(student.getPasswordHash(), userRepository.findByCollegeId("25251A0599").orElseThrow().getPasswordHash());
        assertEquals(4, userRepository.count());
        assertTrue(canteenService.getDefaultCanteen().getAdminInvites().isEmpty());
    }

    @Test
    void wrongCodeAndOtherComputersAreRejected() throws Exception {
        existingAdmin();
        for (String wrong : new String[] {"YIC@12345", "yic@1234", "YIC1234", ""}) {
            registerAdmin("BADADMIN", wrong, "127.0.0.1").andExpect(status().isOk())
                    .andExpect(content().string(Matchers.anyOf(Matchers.containsString("not valid"),
                            Matchers.containsString("Enter the invitation code"))));
        }
        // The right code typed on another device (e.g. a phone on the campus Wi-Fi) is not accepted.
        mvc.perform(get("/admin/register").with(request -> { request.setRemoteAddr("192.168.1.50"); return request; }))
                .andExpect(content().string(Matchers.not(Matchers.containsString("CANTEEN_ADMIN_INVITATION_CODE"))));
        registerAdmin("BADADMIN", CODE, "192.168.1.50").andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("not valid")));
        assertFalse(userRepository.existsByCollegeId("BADADMIN"));
        assertEquals(1, adminRegistrationService.listAdmins().size());
    }

    @Test
    void oneTimeInvitationsStillWorkAlongsideTheCode() throws Exception {
        existingAdmin();
        NewInvite invite = adminRegistrationService.createInvite("CANTEENADMIN01");
        registerAdmin("INVITED1", invite.code(), "192.168.1.50").andExpect(redirectedUrl("/admin/login"));
        registerAdmin("INVITED2", invite.code(), "127.0.0.1").andExpect(content().string(Matchers.containsString("not valid")));
        registerAdmin("LOCAL1", CODE, "127.0.0.1").andExpect(redirectedUrl("/admin/login"));
        assertEquals(3, adminRegistrationService.listAdmins().size());
    }

    @Test
    void studentsAndFacultyCannotBecomeAdministrators() throws Exception {
        existingAdmin();
        for (String role : new String[] {"CANTEEN_ADMIN", "STUDENT", "FACULTY"}) {
            mvc.perform(post("/register").with(csrf())
                    .param("role", role).param("collegeId", "SNEAKY1").param("name", "X").param("department", "CSE")
                    .param("year", "2").param("code", CODE)
                    .param("password", "password123").param("confirmPassword", "password123"));
        }
        assertEquals(1, adminRegistrationService.listAdmins().size());
        userRepository.findByCollegeId("SNEAKY1").ifPresent(u -> assertTrue(u.getRole().isCustomer()));

        // A logged-in student cannot use the administrator registration page.
        CanteenUserDetails student = new CanteenUserDetails(customer(Role.STUDENT, "21CS777"));
        mvc.perform(post("/admin/register").with(user(student)).with(csrf())
                        .param("code", CODE).param("collegeId", "STUADMIN").param("name", "X")
                        .param("password", "password123").param("confirmPassword", "password123"))
                .andExpect(redirectedUrl("/"));
        assertFalse(userRepository.existsByCollegeId("STUADMIN"));
    }

    @Test
    void codeIsIgnoredWhenTheDatabaseIsNotOnThisComputer() throws Exception {
        existingAdmin();
        // Same settings (code set) but connected to a remote/Atlas database, as on a deployed server.
        for (String uri : new String[] {"mongodb+srv://user:secret@cluster0.example.mongodb.net/campus_canteen",
                "mongodb://db.example.com:27017/campus_canteen"}) {
            AdminRegistrationService deployed = new AdminRegistrationService(userService, userRepository, canteenService,
                    properties, mongo, appClock, () -> new ConnectionString(uri));
            assertFalse(AdminRegistrationService.isLocalDatabase(new ConnectionString(uri)));
            assertFalse(deployed.isLocalInvitationCodeEnabled("127.0.0.1"));
        }
        assertTrue(AdminRegistrationService.isLocalDatabase(new ConnectionString("mongodb://localhost:27017/campus_canteen")));
        assertTrue(AdminRegistrationService.isLocalDatabase(new ConnectionString("mongodb://127.0.0.1/campus_canteen")));
    }
}
