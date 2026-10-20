package com.campuscanteen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.net.ssl.SSLException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import com.campuscanteen.config.MongoConnectionProblem;

/**
 * What happens when MongoDB cannot be reached. These tests need no database at all,
 * so they also run when MONGODB_URI is not set.
 */
class MongoConnectionHandlingTest {

    @Test
    void atlasIpAllowListRefusalIsExplained() {
        // This is how the driver reports Atlas refusing a computer that is not on the Network Access list.
        Exception error = new RuntimeException("Timed out while waiting for a server. Client view of cluster state is "
                + "{servers=[{exception={com.mongodb.MongoSocketWriteException: Exception sending message}, "
                + "caused by {javax.net.ssl.SSLException: (internal_error) Received fatal alert: internal_error}}]",
                new SSLException("Received fatal alert: internal_error"));
        assertTrue(MongoConnectionProblem.explain(error).contains("Network Access"));
        assertTrue(MongoConnectionProblem.explain(error).contains("IP address"));
    }

    @Test
    void wrongPasswordIsExplained() {
        Exception error = new RuntimeException("Exception authenticating MongoCredential{mechanism=SCRAM-SHA-1}",
                new RuntimeException("Command failed with error 18 (AuthenticationFailed): 'bad auth : Authentication failed.'"));
        assertTrue(MongoConnectionProblem.explain(error).contains("Database Access"));
    }

    @Test
    void applicationRefusesToStartWithAClearMessageWhenNoServerAnswers() {
        // Port 1 on this computer: nothing listens there, so the connection is refused at once.
        Exception error = assertThrows(Exception.class, () -> new SpringApplicationBuilder(CampusCanteenApplication.class)
                .run("--MONGODB_URI=mongodb://127.0.0.1:1/?serverSelectionTimeoutMS=1500",
                        "--server.port=0", "--spring.mongodb.database=campus_canteen_test", "--app.admin-setup.college-id="));
        String message = rootMessages(error);
        assertTrue(message.contains("Could not connect to MongoDB"), message);
        assertTrue(message.contains("No MongoDB server answered"), message);
        assertFalse(message.contains("mongodb://"), "the connection string must never appear in the message");
    }

    @Test
    void connectionStringWithAccidentalSpacesIsAcceptedButAWrongSchemeIsNot() {
        Exception error = assertThrows(Exception.class, () -> new SpringApplicationBuilder(CampusCanteenApplication.class)
                .run("--MONGODB_URI= \"mongodb://127.0.0.1:1\"", "--server.port=0"));
        assertTrue(rootMessages(error).contains("MONGODB_URI must start with mongodb:// or mongodb+srv://"));
    }

    private static String rootMessages(Throwable error) {
        StringBuilder text = new StringBuilder();
        for (Throwable t = error; t != null; t = t.getCause()) {
            text.append(t.getMessage()).append('\n');
        }
        return text.toString();
    }
}
