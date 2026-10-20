package com.campuscanteen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.campuscanteen.config.AdminAccountSetup;
import com.campuscanteen.config.AppProperties;
import com.campuscanteen.config.AppProperties.AdminSetup;
import com.campuscanteen.model.Role;
import com.campuscanteen.model.User;
import com.campuscanteen.service.AdminRegistrationService;

/** The first-administrator setup (CANTEEN_ADMIN_* environment variables). */
@EnabledIfEnvironmentVariable(named = "MONGODB_URI", matches = ".+")
class AdminAccountSetupTest extends IntegrationTestBase {

    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AdminRegistrationService adminRegistrationService;

    private void runSetup(String id, String name, String password) {
        AppProperties properties = new AppProperties("Asia/Kolkata", "target/test-uploads", new AdminSetup(id, name, password, null, null));
        new AdminAccountSetup(properties, userService, canteenService, adminRegistrationService).run(null);
    }

    @Test
    void createsTheAdministratorOnceAndNeverResetsThePassword() {
        runSetup("canteen01", "Canteen Manager", "first-password");
        User admin = userRepository.findByCollegeId("CANTEEN01").orElseThrow();
        assertEquals(Role.CANTEEN_ADMIN, admin.getRole());
        assertTrue(passwordEncoder.matches("first-password", admin.getPasswordHash()));

        // Starting again with the variables still set (even with another password) changes nothing.
        runSetup("CANTEEN01", "Someone Else", "second-password");
        User after = userRepository.findByCollegeId("CANTEEN01").orElseThrow();
        assertEquals(1, userRepository.count());
        assertEquals("Canteen Manager", after.getName());
        assertTrue(passwordEncoder.matches("first-password", after.getPasswordHash()));
    }

    @Test
    void doesNothingWhenVariablesAreMissingOrPasswordTooShort() {
        runSetup("", "", "");
        runSetup("CANTEEN02", "Manager", "short");
        runSetup("CANTEEN03", "", "long-enough-password");
        assertEquals(0, userRepository.count());
    }
}
