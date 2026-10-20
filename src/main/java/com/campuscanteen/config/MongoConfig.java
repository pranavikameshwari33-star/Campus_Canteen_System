package com.campuscanteen.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.mongodb.ConnectionString;
import org.springframework.boot.mongodb.autoconfigure.MongoConnectionDetails;

/**
 * Tells Spring Boot how to connect to MongoDB, using the MONGODB_URI environment variable.
 *
 * Spaces or line breaks before/after the value (easy to paste by accident into IntelliJ) are
 * removed, because the MongoDB driver rejects a connection string that does not start exactly
 * with "mongodb://" or "mongodb+srv://".
 *
 * The connection string is never logged. The database name is set separately by
 * spring.mongodb.database in application.properties (default: campus_canteen).
 */
@Configuration
public class MongoConfig {

    @Bean
    public MongoConnectionDetails mongoConnectionDetails(Environment environment) {
        String uri = environment.getProperty("MONGODB_URI", "").trim();
        if (uri.isEmpty()) {
            throw new IllegalStateException("MONGODB_URI is not set. Add it to the IntelliJ run configuration "
                    + "(Run > Edit Configurations > Environment variables).");
        }
        if (!uri.startsWith("mongodb://") && !uri.startsWith("mongodb+srv://")) {
            throw new IllegalStateException("MONGODB_URI must start with mongodb:// or mongodb+srv:// "
                    + "(check for extra characters or quotes in the run configuration).");
        }
        ConnectionString connectionString = new ConnectionString(uri);
        return () -> connectionString;
    }
}
