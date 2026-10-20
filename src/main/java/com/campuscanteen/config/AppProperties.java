package com.campuscanteen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings that start with "app." in application.properties.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String timezone, String uploadDir, AdminSetup adminSetup) {

    /**
     * Values used only to create the first administrator account from environment variables.
     * setupCode (CANTEEN_SETUP_CODE) is optional: if set, the first administrator must also
     * type it on the /admin/register page.
     * invitationCode (CANTEEN_ADMIN_INVITATION_CODE) is an optional, reusable invitation code for
     * LOCAL DEVELOPMENT only; see AdminRegistrationService for when it is accepted.
     */
    public record AdminSetup(String collegeId, String name, String password, String setupCode, String invitationCode) {
        public boolean isRequested() {
            return collegeId != null && !collegeId.isBlank();
        }
    }
}
