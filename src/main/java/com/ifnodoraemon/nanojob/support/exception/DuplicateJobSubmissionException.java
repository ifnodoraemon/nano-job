package com.ifnodoraemon.nanojob.support.exception;

public class DuplicateJobSubmissionException extends RuntimeException {

    public DuplicateJobSubmissionException(String message) {
        super(message);
    }
}
