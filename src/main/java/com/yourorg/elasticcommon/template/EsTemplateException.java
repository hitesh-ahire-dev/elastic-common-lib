package com.yourorg.elasticcommon.template;

public class EsTemplateException extends RuntimeException {

    public EsTemplateException(String message) {
        super(message);
    }

    public EsTemplateException(String message, Throwable cause) {
        super(message, cause);
    }
}
