package com.ifnodoraemon.nanojob.support.exception;

public class RetryableHttpJobException extends RuntimeException {

    public RetryableHttpJobException(String message) {
        super(message);
    }

    public RetryableHttpJobException(String message, Throwable cause) {
        super(message, cause);
    }
}
