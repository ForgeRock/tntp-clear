package org.forgerock.am.marketplace.clear;

/**
 * Clear Exception.
 */
public class ClearServiceException extends Exception {

    /**
     * Exception constructor with error message.
     *
     * @param message The error message.
     */
    public ClearServiceException(String message) {
        super(message);
    }
}