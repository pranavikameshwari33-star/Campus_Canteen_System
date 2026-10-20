package com.campuscanteen.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.mongodb.autoconfigure.MongoConnectionDetails;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.campuscanteen.config.AppProperties;
import com.campuscanteen.model.AdminInvite;
import com.campuscanteen.model.Canteen;
import com.campuscanteen.model.Role;
import com.campuscanteen.model.User;
import com.campuscanteen.repository.UserRepository;
import com.campuscanteen.web.form.AdminRegistrationForm;
import com.mongodb.ConnectionString;

/**
 * Registration of canteen administrators through /admin/register.
 *
 * 1. First administrator: allowed only while NO administrator exists. The canteen document's
 *    "firstAdminRegistered" flag is switched on with one atomic update before the account is
 *    created, so two people registering at the same moment cannot both become the first
 *    administrator. If the CANTEEN_SETUP_CODE environment variable is set, that code is required too.
 * 2. Every later administrator needs a one-time invitation code created by an existing
 *    administrator (Accounts page). Codes expire after 24 hours and can be used once; only a
 *    SHA-256 hash of each code is stored.
 * 3. Local development only: a reusable invitation code from the CANTEEN_ADMIN_INVITATION_CODE
 *    environment variable. It is OFF unless that variable is set, and even then it is accepted only
 *    when MongoDB runs on this computer AND the registration page was opened on this computer
 *    (localhost). So it cannot be used on a deployed server (Atlas) or from another device. It is
 *    never stored in the database and never written to the log.
 *
 * Passwords are hashed with BCrypt by UserService.createAdmin. Students and faculty can never
 * become administrators through the normal registration form.
 */
@Service
public class AdminRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(AdminRegistrationService.class);

    static final Duration INVITE_VALIDITY = Duration.ofHours(24);
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final UserService userService;
    private final UserRepository userRepository;
    private final CanteenService canteenService;
    private final AppProperties properties;
    private final MongoTemplate mongo;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    /** The local-development invitation code, or "" when it is switched off. */
    private final String localInvitationCode;

    public AdminRegistrationService(UserService userService, UserRepository userRepository, CanteenService canteenService,
                                    AppProperties properties, MongoTemplate mongo, Clock clock,
                                    MongoConnectionDetails mongoConnection) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.canteenService = canteenService;
        this.properties = properties;
        this.mongo = mongo;
        this.clock = clock;
        this.localInvitationCode = localInvitationCode(properties, mongoConnection.getConnectionString());
    }

    private static String localInvitationCode(AppProperties properties, ConnectionString connection) {
        AppProperties.AdminSetup setup = properties.adminSetup();
        String code = setup == null || setup.invitationCode() == null ? "" : setup.invitationCode().trim();
        if (code.isEmpty()) {
            return "";
        }
        if (!isLocalDatabase(connection)) {
            log.warn("CANTEEN_ADMIN_INVITATION_CODE is set but IGNORED: it only works with a MongoDB server on this computer "
                    + "(local development). Remove it from this environment.");
            return "";
        }
        log.info("Local-development administrator invitation code (CANTEEN_ADMIN_INVITATION_CODE) is enabled "
                + "for /admin/register opened on this computer.");
        return code;
    }

    /** True if every MongoDB host in the connection string is this computer (localhost / 127.0.0.1 / ::1). */
    public static boolean isLocalDatabase(ConnectionString connection) {
        if (connection.isSrvProtocol() || connection.getHosts().isEmpty()) {
            return false;   // mongodb+srv:// is always a remote cluster such as Atlas
        }
        return connection.getHosts().stream().allMatch(host -> {
            String h = host.toLowerCase(Locale.ROOT);
            return h.equals("localhost") || h.startsWith("localhost:") || h.equals("127.0.0.1") || h.startsWith("127.0.0.1:")
                    || h.equals("[::1]") || h.startsWith("[::1]:");
        });
    }

    /** True if the request came from this computer (the browser is on the same machine as the server). */
    static boolean isFromThisComputer(String remoteAddress) {
        return remoteAddress != null && (remoteAddress.equals("127.0.0.1") || remoteAddress.equals("0:0:0:0:0:0:0:1")
                || remoteAddress.equals("::1"));
    }

    /** True if the local-development invitation code may be used by a request from this address. */
    public boolean isLocalInvitationCodeEnabled(String remoteAddress) {
        return !localInvitationCode.isEmpty() && isFromThisComputer(remoteAddress);
    }

    /** True while nobody has registered as administrator yet, so the first-administrator form is shown. */
    public boolean isFirstAdminSetupOpen() {
        return !canteenService.getDefaultCanteen().isFirstAdminRegistered() && !userRepository.existsByRole(Role.CANTEEN_ADMIN);
    }

    /** True if the first administrator must also type the CANTEEN_SETUP_CODE. */
    public boolean isSetupCodeRequired() {
        return !setupCode().isEmpty();
    }

    /** Startup: if an administrator already exists (e.g. created earlier), close the first-administrator setup. */
    public void closeFirstAdminSetupIfAdminExists() {
        if (userRepository.existsByRole(Role.CANTEEN_ADMIN)) {
            mongo.updateMulti(new Query(Criteria.where("firstAdminRegistered").ne(true)),
                    new Update().set("firstAdminRegistered", true), Canteen.class);
        }
    }

    public User register(AdminRegistrationForm form) {
        return register(form, null);
    }

    /** remoteAddress: the browser's IP address (needed only for the local-development invitation code). */
    public User register(AdminRegistrationForm form, String remoteAddress) {
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw new BusinessException("The two passwords do not match.");
        }
        String collegeId = UserService.normalizeCollegeId(form.getCollegeId());
        if (userRepository.existsByCollegeId(collegeId)) {
            throw new BusinessException("An account with this ID already exists.");
        }
        Canteen canteen = canteenService.getDefaultCanteen();
        if (isFirstAdminSetupOpen()) {
            return registerFirst(form, canteen);
        }
        if (isLocalInvitationCodeEnabled(remoteAddress) && matchesLocalInvitationCode(form.getCode())) {
            // Reusable: nothing is marked as used. The ID was checked above and is also protected by a unique index.
            User admin = userService.createAdmin(form.getCollegeId(), form.getName(), form.getPassword(), canteen.getId());
            log.info("Administrator account {} registered with the local-development invitation code.", admin.getCollegeId());
            return admin;
        }
        return registerInvited(form, canteen, collegeId);
    }

    private boolean matchesLocalInvitationCode(String typed) {
        byte[] expected = localInvitationCode.getBytes(StandardCharsets.UTF_8);
        byte[] actual = typed == null ? new byte[0] : typed.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private User registerFirst(AdminRegistrationForm form, Canteen canteen) {
        if (isSetupCodeRequired() && !MessageDigest.isEqual(
                setupCode().getBytes(StandardCharsets.UTF_8), form.getCode() == null ? new byte[0]
                        : form.getCode().trim().getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException("The setup code is not correct.");
        }
        // Claim the "first administrator" place atomically.
        Query unclaimed = new Query(Criteria.where("id").is(canteen.getId()).and("firstAdminRegistered").ne(true));
        if (mongo.updateFirst(unclaimed, new Update().set("firstAdminRegistered", true), Canteen.class).getModifiedCount() == 0) {
            throw new BusinessException("An administrator has already been registered. Ask an administrator for an invitation code.");
        }
        try {
            return userService.createAdmin(form.getCollegeId(), form.getName(), form.getPassword(), canteen.getId());
        } catch (RuntimeException e) {
            // Give the place back so the setup can be tried again.
            mongo.updateFirst(new Query(Criteria.where("id").is(canteen.getId())),
                    new Update().set("firstAdminRegistered", false), Canteen.class);
            throw e;
        }
    }

    private User registerInvited(AdminRegistrationForm form, Canteen canteen, String collegeId) {
        String code = clean(form.getCode());
        if (code.isEmpty()) {
            throw new BusinessException("Enter the invitation code you received from a canteen administrator.");
        }
        Instant now = Instant.now(clock);
        String hash = hash(code);
        // Use the invitation in one atomic step: it must match, be unused and not expired.
        Query usable = new Query(Criteria.where("id").is(canteen.getId()).and("adminInvites").elemMatch(
                Criteria.where("codeHash").is(hash).and("usedAt").is(null).and("expiresAt").gt(now)));
        Update use = new Update().set("adminInvites.$.usedAt", now).set("adminInvites.$.usedBy", collegeId);
        if (mongo.updateFirst(usable, use, Canteen.class).getModifiedCount() == 0) {
            throw new BusinessException("The invitation code is not valid. It may be mistyped, already used or expired.");
        }
        try {
            return userService.createAdmin(form.getCollegeId(), form.getName(), form.getPassword(), canteen.getId());
        } catch (RuntimeException e) {
            // The account was not created, so the invitation can still be used.
            Query used = new Query(Criteria.where("id").is(canteen.getId())
                    .and("adminInvites").elemMatch(Criteria.where("codeHash").is(hash)));
            mongo.updateFirst(used, new Update().unset("adminInvites.$.usedAt").unset("adminInvites.$.usedBy"), Canteen.class);
            throw e;
        }
    }

    /**
     * Creates a one-time invitation code for a new administrator. The plain code is returned
     * only here (to show once); the database keeps its hash.
     */
    public NewInvite createInvite(String adminCollegeId) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            if (i > 0 && i % 4 == 0) {
                code.append('-');
            }
            code.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        Instant now = Instant.now(clock);
        AdminInvite invite = new AdminInvite(hash(code.toString()), adminCollegeId, now, now.plus(INVITE_VALIDITY));
        mongo.updateFirst(new Query(Criteria.where("id").is(canteenService.getDefaultCanteen().getId())),
                new Update().push("adminInvites", invite), Canteen.class);
        return new NewInvite(code.toString(), invite.getExpiresAt());
    }

    /** Invitations, newest first (for the Accounts page; codes themselves are never shown again). */
    public List<AdminInvite> listInvites() {
        return canteenService.getDefaultCanteen().getAdminInvites().stream()
                .sorted(Comparator.comparing(AdminInvite::getCreatedAt).reversed())
                .limit(20)
                .toList();
    }

    public List<User> listAdmins() {
        return mongo.find(new Query(Criteria.where("role").is(Role.CANTEEN_ADMIN)), User.class);
    }

    private String setupCode() {
        AppProperties.AdminSetup setup = properties.adminSetup();
        return setup == null || setup.setupCode() == null ? "" : setup.setupCode().trim();
    }

    /** Invitation codes are compared without spaces and in upper case, so "abcd-efgh-..." also works. */
    private static String clean(String code) {
        return code == null ? "" : code.replace(" ", "").trim().toUpperCase(Locale.ROOT);
    }

    static String hash(String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(clean(code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record NewInvite(String code, Instant expiresAt) {
    }
}
