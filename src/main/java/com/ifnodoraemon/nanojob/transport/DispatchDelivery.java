package com.ifnodoraemon.nanojob.transport;

import com.ifnodoraemon.nanojob.service.QueuedJob;
import java.util.Objects;

public record DispatchDelivery(
        QueuedJob queuedJob,
        Runnable ackAction,
        Runnable retryLaterAction
) {

    public DispatchDelivery {
        Objects.requireNonNull(queuedJob, "queuedJob must not be null");
        Objects.requireNonNull(ackAction, "ackAction must not be null");
        Objects.requireNonNull(retryLaterAction, "retryLaterAction must not be null");
    }

    public void ack() {
        ackAction.run();
    }

    public void retryLater() {
        retryLaterAction.run();
    }
}
