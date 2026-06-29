package com.ayurveda.platform.exception;

/**
 * Exception thrown when an invalid order status transition is attempted.
 * Implements Requirement 5.8 - invalid transitions must be rejected with error message.
 */
public class InvalidStatusTransitionException extends IllegalArgumentException {
    
    private final String fromStatus;
    private final String toStatus;
    private final String reason;

    public InvalidStatusTransitionException(String fromStatus, String toStatus, String reason) {
        super(String.format("Cannot transition from %s to %s: %s", fromStatus, toStatus, reason));
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
    }

    public InvalidStatusTransitionException(String fromStatus, String toStatus) {
        this(fromStatus, toStatus, "This transition is not allowed");
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public String getReason() {
        return reason;
    }
}
