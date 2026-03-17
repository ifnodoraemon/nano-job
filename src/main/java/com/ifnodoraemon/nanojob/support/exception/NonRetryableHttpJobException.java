package com.ifnodoraemon.nanojob.support.exception;

public class NonRetryableHttpJobException extends IllegalArgumentException {

    public NonRetryableHttpJobException(String message) {
        super(message);
    }

    public NonRetryableHttpJobException(String message, Throwable cause) {
        super(message, cause);
    }
}
