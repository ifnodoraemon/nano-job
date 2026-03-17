package com.ifnodoraemon.nanojob.transport;

import com.ifnodoraemon.nanojob.service.QueuedJob;

public interface JobDispatchTransport {

    boolean publish(QueuedJob queuedJob);

    DispatchDelivery take() throws InterruptedException;

    int depth();

    int remainingCapacity();

    DispatchTransportType type();
}
