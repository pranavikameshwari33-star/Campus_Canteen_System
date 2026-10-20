package com.campuscanteen.service;

/**
 * An expected problem whose message is safe and helpful to show to the user,
 * e.g. "Only 2 plates of Dosa are left."
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
