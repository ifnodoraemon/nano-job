package com.ifnodoraemon.nanojob.support.exception;

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(String jobKey) {
        super("Job not found: " + jobKey);
    }
}
