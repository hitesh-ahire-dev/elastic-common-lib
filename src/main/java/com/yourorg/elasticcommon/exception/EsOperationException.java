package com.yourorg.elasticcommon.exception;

public class EsOperationException extends RuntimeException {

    public EsOperationException(String message) {
        super(message);
    }

    public EsOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
