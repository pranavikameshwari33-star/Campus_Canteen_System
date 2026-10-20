package com.campuscanteen.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.campuscanteen.config.AppProperties.AdminSetup;
import com.campuscanteen.service.AdminRegistrationService;
import com.campuscanteen.service.CanteenService;
import com.campuscanteen.service.UserService;

/**
 * Creates a canteen administrator account at startup, but ONLY when the environment variables
 * CANTEEN_ADMIN_ID, CANTEEN_ADMIN_NAME and CANTEEN_ADMIN_PASSWORD are set.
 * There is no built-in default administrator or password.
 *
 * (Administrators can also register on the /admin/register page; see AdminRegistrationService.)
 *
 * If an account with that ID already exists nothing is changed (its password is never reset). Remove the variables from the
 * IntelliJ run configuration after the first successful start.
 */
@Component
@Order(2)
public class AdminAccountSetup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountSetup.class);

    private final AppProperties properties;
    private final UserService userService;
    private final CanteenService canteenService;

    private final AdminRegistrationService adminRegistrationService;

    public AdminAccountSetup(AppProperties properties, UserService userService, CanteenService canteenService,
                             AdminRegistrationService adminRegistrationService) {
        this.properties = properties;
        this.userService = userService;
        this.canteenService = canteenService;
        this.adminRegistrationService = adminRegistrationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        createFromEnvironment();
        // Once any administrator exists, the open "first administrator" form on /admin/register is closed.
        adminRegistrationService.closeFirstAdminSetupIfAdminExists();
    }

    private void createFromEnvironment() {
        AdminSetup setup = properties.adminSetup();
        if (setup == null || !setup.isRequested()) {
            return;
        }
        if (userService.findByCollegeId(setup.collegeId()).isPresent()) {
            log.info("Administrator setup: an account with ID {} already exists, nothing changed. "
                    + "You can now remove the CANTEEN_ADMIN_* environment variables.",
                    UserService.normalizeCollegeId(setup.collegeId()));
            return;
        }
        if (setup.name() == null || setup.name().isBlank()) {
            log.warn("Administrator setup skipped: CANTEEN_ADMIN_NAME is not set.");
            return;
        }
        if (setup.password() == null || setup.password().length() < 8) {
            log.warn("Administrator setup skipped: CANTEEN_ADMIN_PASSWORD must be at least 8 characters.");
            return;
        }
        userService.createAdmin(setup.collegeId(), setup.name(), setup.password(),
                canteenService.getDefaultCanteen().getId());
        log.info("Administrator account {} created. Remove the CANTEEN_ADMIN_* environment variables now.",
                UserService.normalizeCollegeId(setup.collegeId()));
    }
}
