package com.campuscanteen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.campuscanteen.model.Role;
import com.campuscanteen.model.User;
import com.campuscanteen.tools.AdminPasswordReset;
import com.campuscanteen.tools.AdminPasswordReset.ResetRefused;
import com.mongodb.client.MongoDatabase;

/** The local administrator password-reset tool (tools/AdminPasswordReset). Runs on campus_canteen_test only. */
@EnabledIfEnvironmentVariable(named = "MONGODB_URI", matches = ".+")
class AdminPasswordResetTest extends IntegrationTestBase {

    @Autowired PasswordEncoder passwordEncoder;

    private MongoDatabase testDb() {
        MongoDatabase db = mongo.getDb();
        assertEquals("campus_canteen_test", db.getName());
        return db;
    }

    @Test
    void changesOnlyThePasswordOfTheExistingAdministrator() {
        User admin = admin("CANTEENADMIN01");
        User student = customer(Role.STUDENT, "STU01");
        Document before = testDb().getCollection("users").find(new Document("collegeId", "CANTEENADMIN01")).first();
        Document studentBefore = testDb().getCollection("users").find(new Document("collegeId", "STU01")).first();
        Instant now = Instant.parse("2026-10-14T10:00:00Z");

        AdminPasswordReset.resetPassword(testDb(), "canteenadmin01", "my-new-password".toCharArray(), passwordEncoder, now);

        User after = userRepository.findByCollegeId("CANTEENADMIN01").orElseThrow();
        assertTrue(passwordEncoder.matches("my-new-password", after.getPasswordHash()));
        assertFalse(passwordEncoder.matches("adminpass123", after.getPasswordHash()));
        assertTrue(after.getPasswordHash().startsWith("$2"), "stored as a BCrypt hash, not plain text");
        assertEquals(now, after.getPasswordChangedAt());

        // Same account (same id), still an administrator, every other field unchanged.
        Document doc = testDb().getCollection("users").find(new Document("collegeId", "CANTEENADMIN01")).first();
        assertNotNull(doc);
        assertEquals(admin.getId(), doc.getObjectId("_id").toHexString());
        for (String key : before.keySet()) {
            if (!key.equals("passwordHash")) {
                assertEquals(before.get(key), doc.get(key), "field " + key + " must not change");
            }
        }
        assertEquals(before.keySet().size() + 1, doc.keySet().size(), "only passwordChangedAt is added");
        assertEquals(2, userRepository.count());
        assertEquals(studentBefore, testDb().getCollection("users").find(new Document("collegeId", "STU01")).first());
        assertTrue(passwordEncoder.matches("password123", userRepository.findById(student.getId()).orElseThrow().getPasswordHash()));
    }

    @Test
    void refusesStudentsMissingAccountsAndBadPasswords() {
        User student = customer(Role.STUDENT, "STU01");
        admin("CANTEENADMIN01");
        String studentHash = student.getPasswordHash();

        assertThrows(ResetRefused.class, () -> AdminPasswordReset.resetPassword(
                testDb(), "STU01", "long-enough-pw".toCharArray(), passwordEncoder, Instant.now()));
        assertEquals(studentHash, userRepository.findById(student.getId()).orElseThrow().getPasswordHash());

        assertThrows(ResetRefused.class, () -> AdminPasswordReset.resetPassword(
                testDb(), "NOSUCHADMIN", "long-enough-pw".toCharArray(), passwordEncoder, Instant.now()));
        assertEquals(2, userRepository.count(), "no account is created");

        assertThrows(ResetRefused.class, () -> AdminPasswordReset.resetPassword(
                testDb(), "CANTEENADMIN01", "short".toCharArray(), passwordEncoder, Instant.now()));
        assertThrows(ResetRefused.class, () -> AdminPasswordReset.resetPassword(
                testDb(), "CANTEENADMIN01", "x".repeat(65).toCharArray(), passwordEncoder, Instant.now()));
        assertThrows(ResetRefused.class, () -> AdminPasswordReset.resetPassword(
                testDb(), "CANTEENADMIN01", "         ".toCharArray(), passwordEncoder, Instant.now()));
        assertTrue(passwordEncoder.matches("adminpass123",
                userRepository.findByCollegeId("CANTEENADMIN01").orElseThrow().getPasswordHash()));
    }

    @Test
    void onlyAcceptsTheLocalServerAndTheTwoCanteenDatabases() {
        // Accepted
        AdminPasswordReset.checkTarget("mongodb://localhost:27017/campus_canteen", "campus_canteen");
        AdminPasswordReset.checkTarget("mongodb://127.0.0.1:27017", "campus_canteen_test");
        AdminPasswordReset.checkTarget("mongodb://[::1]:27017/", "campus_canteen");

        // Refused: Atlas, other computers, other databases, missing URI
        assertThrows(ResetRefused.class, () -> AdminPasswordReset.checkTarget(
                "mongodb+srv://user:secret@cluster0.abcde.mongodb.net/campus_canteen", "campus_canteen"));
        assertThrows(ResetRefused.class, () -> AdminPasswordReset.checkTarget(
                "mongodb://user:secret@cluster0-shard-00-00.abcde.mongodb.net:27017/campus_canteen", "campus_canteen"));
        assertThrows(ResetRefused.class, () -> AdminPasswordReset.checkTarget(
                "mongodb://192.168.1.20:27017/campus_canteen", "campus_canteen"));
        assertThrows(ResetRefused.class, () -> AdminPasswordReset.checkTarget(
                "mongodb://localhost:27017,db.example.com:27017/campus_canteen", "campus_canteen"));
        assertThrows(ResetRefused.class, () -> AdminPasswordReset.checkTarget(
                "mongodb://localhost.example.com:27017/", "campus_canteen"));
        assertThrows(ResetRefused.class, () -> AdminPasswordReset.checkTarget(
                "mongodb://localhost:27017/", "campus_canteen_e2e_test"));
        assertThrows(ResetRefused.class, () -> AdminPasswordReset.checkTarget(
                "mongodb://localhost:27017/", "admin"));
        assertThrows(ResetRefused.class, () -> AdminPasswordReset.checkTarget(null, "campus_canteen"));
        assertThrows(ResetRefused.class, () -> AdminPasswordReset.checkTarget(" ", "campus_canteen"));

        // Refusal messages never repeat the connection string or its password.
        ResetRefused refused = assertThrows(ResetRefused.class, () -> AdminPasswordReset.checkTarget(
                "mongodb+srv://user:secret@cluster0.abcde.mongodb.net/campus_canteen", "campus_canteen"));
        assertFalse(refused.getMessage().contains("secret"));
        assertFalse(refused.getMessage().contains("cluster0"));
    }
}
