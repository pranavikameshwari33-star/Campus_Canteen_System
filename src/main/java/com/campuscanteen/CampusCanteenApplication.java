package com.campuscanteen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Starting point of the website. Run this class from IntelliJ.
 *
 * The MongoDB connection string is read from the MONGODB_URI environment variable
 * (see application.properties). It is never printed.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CampusCanteenApplication {

    public static void main(String[] args) {
        String uri = System.getenv("MONGODB_URI");
        if (uri == null || uri.isBlank()) {
            System.err.println("MONGODB_URI environment variable is not set.");
            System.err.println("In IntelliJ: Run > Edit Configurations > Environment variables, add MONGODB_URI.");
            System.exit(1);
        }
        SpringApplication.run(CampusCanteenApplication.class, args);
    }
}
