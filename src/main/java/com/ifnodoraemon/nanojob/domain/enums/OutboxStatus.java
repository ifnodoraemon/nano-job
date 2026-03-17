package com.ifnodoraemon.nanojob.domain.enums;

public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    PROCESSING,
    PROCESSED,
    DISCARDED
}
