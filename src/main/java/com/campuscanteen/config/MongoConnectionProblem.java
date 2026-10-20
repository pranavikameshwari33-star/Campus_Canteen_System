package com.campuscanteen.config;

import java.util.Locale;

/**
 * Turns a MongoDB connection error into a plain-English explanation with the setting to check.
 *
 * The driver's error text is only searched for known clues; it is never printed, so the
 * connection string and password cannot appear in the log.
 */
public final class MongoConnectionProblem {

    private MongoConnectionProblem() {
    }

    public static String explain(Throwable error) {
        String text = allMessages(error);
        if (text.contains("internal_error") || text.contains("received fatal alert")) {
            return "MongoDB Atlas refused the secure connection from this computer. This almost always means this "
                    + "computer's public IP address is not on the Atlas allow list. Fix: Atlas > Security > Network Access > "
                    + "Add IP Address > Add Current IP Address, wait until it shows 'Active', then start the application again.";
        }
        if (text.contains("authentication failed") || text.contains("mongosecurityexception") || text.contains("bad auth")) {
            return "MongoDB Atlas rejected the database username or password in MONGODB_URI. Fix: check the user under "
                    + "Atlas > Security > Database Access, and that the password in MONGODB_URI is correct "
                    + "(special characters such as @ or # must be URL-encoded).";
        }
        if (text.contains("srvresolution") || text.contains("failed looking up") || text.contains("unknownhost")) {
            return "The cluster address in MONGODB_URI could not be found. Fix: copy the connection string again from "
                    + "Atlas > Database > Connect > Drivers, and check that this computer is connected to the internet.";
        }
        if (text.contains("connection refused") || text.contains("connectexception") || text.contains("timed out")) {
            return "No MongoDB server answered. Fix: for a local server (localhost), check that the Windows service "
                    + "'MongoDB Server (MongoDB)' is running (Services app). For Atlas, check your internet connection, "
                    + "that a firewall or college network does not block outgoing connections on port 27017, "
                    + "and Atlas > Security > Network Access.";
        }
        return "Could not connect to MongoDB. Check that MONGODB_URI is correct, the database user and password are valid, "
                + "and this computer's IP address is allowed under Atlas > Security > Network Access.";
    }

    /** All messages and exception class names in the error and its causes, in lower case. */
    private static String allMessages(Throwable error) {
        StringBuilder text = new StringBuilder();
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            text.append(t.getClass().getSimpleName()).append(' ').append(t.getMessage()).append('\n');
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }
}
