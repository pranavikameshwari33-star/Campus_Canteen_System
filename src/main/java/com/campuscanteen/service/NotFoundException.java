package com.campuscanteen.service;

/**
 * Thrown when a record does not exist, or exists but the current user is not allowed to see it.
 * Both cases show the same "not found" page so users cannot guess other people's order IDs.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
