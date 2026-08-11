package com.mindplates.nextchapter.common.exception;

public class ExternalApiException extends InfrastructureException {

    public ExternalApiException(String message) {
        super(message);
    }

    public ExternalApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
