package com.campuscanteen.tools;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;

import com.campuscanteen.config.SecurityConfig;
import com.campuscanteen.model.Role;
import com.campuscanteen.service.UserService;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;

/**
 * Local-only recovery tool: sets a new password for an EXISTING canteen administrator account.
 *
 * It is a separate program, not part of the website. It never runs when the website starts and
 * there is no web page for it. You run it yourself from a terminal (see README.md,
 * "Forgotten administrator password"):
 *
 *   reset-admin-password.cmd --admin-id CANTEENADMIN01 --database campus_canteen
 *
 * Safety rules:
 * - Uses the same MONGODB_URI environment variable as the website, but refuses to run unless that
 *   URI points to a MongoDB server on this computer (localhost / 127.0.0.1 / ::1). Atlas
 *   (mongodb+srv:// or *.mongodb.net) and any other server are refused.
 * - Only the databases campus_canteen and campus_canteen_test are accepted, and the database must be
 *   named explicitly with --database.
 * - Only an existing account with the CANTEEN_ADMIN role is changed. Nothing is created or deleted.
 * - Only two fields are written: passwordHash (BCrypt, same encoder as the website) and
 *   passwordChangedAt. All other fields and all other data stay as they are.
 * - The password, its hash and the connection string are never printed.
 */
public final class AdminPasswordReset {

    /** Databases this tool is allowed to change. */
    static final Set<String> ALLOWED_DATABASES = Set.of("campus_canteen", "campus_canteen_test");
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    private AdminPasswordReset() {
    }

    /** Thrown for every refusal; the message is safe to show (it contains no secrets). */
    public static class ResetRefused extends RuntimeException {
        public ResetRefused(String message) {
            super(message);
        }
    }

    public static void main(String[] args) {
        quietLogging();
        try {
            run(args);
        } catch (ResetRefused e) {
            System.err.println("NOT CHANGED: " + e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            // Driver errors can contain the connection string, so only the error type is shown.
            System.err.println("NOT CHANGED: unexpected error (" + e.getClass().getSimpleName()
                    + "). Check that the MongoDB service is running.");
            System.exit(1);
        }
    }

    private static void run(String[] args) {
        String adminId = argument(args, "--admin-id");
        String database = argument(args, "--database");
        if (adminId == null || database == null) {
            throw new ResetRefused("Usage: reset-admin-password.cmd --admin-id CANTEENADMIN01 --database campus_canteen");
        }
        String uri = System.getenv("MONGODB_URI");
        ConnectionString target = checkTarget(uri, database);
        adminId = UserService.normalizeCollegeId(adminId);

        System.out.println("Administrator password reset");
        System.out.println("  Server:   " + String.join(", ", target.getHosts()) + " (local)");
        System.out.println("  Database: " + database);
        System.out.println("  Account:  " + adminId);

        try (PasswordInput input = PasswordInput.open()) {
            char[] password = input.readPassword("New password (8-64 characters): ");
            char[] confirm = input.readPassword("Type the new password again: ");
            try {
                if (!Arrays.equals(password, confirm)) {
                    throw new ResetRefused("The two passwords do not match.");
                }
                checkPassword(password);
                String answer = input.readLine("Type RESET to change the password of " + adminId + ": ");
                if (!"RESET".equals(answer == null ? null : answer.trim())) {
                    throw new ResetRefused("You did not type RESET.");
                }
                try (MongoClient client = MongoClients.create(target)) {
                    resetPassword(client.getDatabase(database), adminId, password,
                            new SecurityConfig().passwordEncoder(), Instant.now());
                }
            } finally {
                Arrays.fill(password, '\0');
                Arrays.fill(confirm, '\0');
            }
        }
        System.out.println("DONE: the password of " + adminId + " in database '" + database + "' was changed.");
        System.out.println("Log in at http://localhost:8080/admin/login with the new password.");
    }

    /**
     * Checks that the URI is a local MongoDB server and the database is one of the allowed ones.
     * Returns the parsed connection string.
     */
    public static ConnectionString checkTarget(String uri, String database) {
        if (uri == null || uri.isBlank()) {
            throw new ResetRefused("The MONGODB_URI environment variable is not set.");
        }
        if (!ALLOWED_DATABASES.contains(database)) {
            throw new ResetRefused("Database '" + database + "' is not allowed. Use campus_canteen or campus_canteen_test.");
        }
        uri = uri.trim();
        if (uri.startsWith("mongodb+srv://") || uri.toLowerCase(Locale.ROOT).contains(".mongodb.net")) {
            throw new ResetRefused("MONGODB_URI points to MongoDB Atlas. This tool only works on the local MongoDB server.");
        }
        if (!uri.startsWith("mongodb://")) {
            throw new ResetRefused("MONGODB_URI must start with mongodb://");
        }
        ConnectionString cs;
        try {
            cs = new ConnectionString(uri);
        } catch (IllegalArgumentException e) {
            throw new ResetRefused("MONGODB_URI is not a valid MongoDB connection string.");
        }
        List<String> hosts = cs.getHosts();
        for (String hostAndPort : hosts) {
            if (!LOCAL_HOSTS.contains(hostOnly(hostAndPort).toLowerCase(Locale.ROOT))) {
                throw new ResetRefused("MONGODB_URI points to another computer. This tool only works on the local MongoDB server.");
            }
        }
        return cs;
    }

    /** Same rule as the registration forms: 8-64 characters (and within BCrypt's 72-byte limit). */
    public static void checkPassword(char[] password) {
        if (password.length < 8 || password.length > 64) {
            throw new ResetRefused("The password must be 8-64 characters.");
        }
        if (StandardCharsets.UTF_8.encode(CharBuffer.wrap(password)).remaining() > 72) {
            throw new ResetRefused("The password is too long (use fewer special characters).");
        }
        if (new String(password).isBlank()) {
            throw new ResetRefused("The password cannot be only spaces.");
        }
    }

    /**
     * Sets a new BCrypt password hash on an existing administrator account. Only passwordHash and
     * passwordChangedAt are written; the account must exist and have the CANTEEN_ADMIN role.
     */
    public static void resetPassword(MongoDatabase db, String adminId, char[] newPassword,
                                     PasswordEncoder encoder, Instant now) {
        checkPassword(newPassword);
        String collegeId = UserService.normalizeCollegeId(adminId);
        Document user = db.getCollection("users").find(Filters.eq("collegeId", collegeId)).first();
        if (user == null) {
            throw new ResetRefused("No account with ID " + collegeId + " exists in this database. Nothing was created.");
        }
        if (!Role.CANTEEN_ADMIN.name().equals(user.getString("role"))) {
            throw new ResetRefused("Account " + collegeId + " is not a canteen administrator. Its password was not changed.");
        }
        String hash = encoder.encode(CharBuffer.wrap(newPassword));
        UpdateResult result = db.getCollection("users").updateOne(
                Filters.and(Filters.eq("_id", user.get("_id")), Filters.eq("role", Role.CANTEEN_ADMIN.name())),
                Updates.combine(Updates.set("passwordHash", hash), Updates.set("passwordChangedAt", now)));
        if (result.getMatchedCount() != 1) {
            throw new ResetRefused("The account changed while resetting. Nothing was changed; please try again.");
        }
    }

    /** Outside Spring Boot the logging library would print debug details; keep only warnings. */
    private static void quietLogging() {
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            context.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.WARN);
        }
    }

    private static String hostOnly(String hostAndPort) {
        if (hostAndPort.startsWith("[")) {
            int end = hostAndPort.indexOf(']');
            return end > 0 ? hostAndPort.substring(0, end + 1) : hostAndPort;
        }
        int colon = hostAndPort.lastIndexOf(':');
        return colon > 0 && hostAndPort.indexOf(':') == colon ? hostAndPort.substring(0, colon) : hostAndPort;
    }

    private static String argument(String[] args, String name) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                return args[i + 1].trim();
            }
        }
        return null;
    }

    /** Reads hidden input from the terminal; falls back to plain input (e.g. the IntelliJ Run window). */
    private static final class PasswordInput implements AutoCloseable {
        private final Console console;
        private final BufferedReader reader;

        private PasswordInput(Console console, BufferedReader reader) {
            this.console = console;
            this.reader = reader;
        }

        static PasswordInput open() {
            Console console = System.console();
            if (console != null) {
                return new PasswordInput(console, null);
            }
            System.out.println("Note: no terminal found, so what you type will be visible in this window.");
            return new PasswordInput(null, new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)));
        }

        char[] readPassword(String prompt) {
            if (console != null) {
                char[] value = console.readPassword(prompt);
                return value == null ? new char[0] : value;
            }
            String line = readLine(prompt);
            return line == null ? new char[0] : line.toCharArray();
        }

        String readLine(String prompt) {
            if (console != null) {
                return console.readLine(prompt);
            }
            System.out.print(prompt);
            System.out.flush();
            try {
                return reader.readLine();
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        public void close() {
        }
    }
}
