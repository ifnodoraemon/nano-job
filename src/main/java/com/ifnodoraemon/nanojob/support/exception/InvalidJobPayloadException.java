package com.ifnodoraemon.nanojob.support.exception;

import com.ifnodoraemon.nanojob.domain.enums.JobType;

public class InvalidJobPayloadException extends IllegalArgumentException {

    public InvalidJobPayloadException(JobType type, String message) {
        super("Invalid payload for job type " + type + ": " + message);
    }
}
