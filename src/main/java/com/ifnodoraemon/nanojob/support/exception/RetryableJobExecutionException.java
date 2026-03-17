package com.ifnodoraemon.nanojob.support.exception;

public class RetryableJobExecutionException extends RuntimeException {

    public RetryableJobExecutionException(String message) {
        super(message);
    }

    public RetryableJobExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
