package com.ifnodoraemon.nanojob.transport;

import com.ifnodoraemon.nanojob.service.QueuedJob;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;

public class LocalQueueJobDispatchTransport implements JobDispatchTransport {

    private final BlockingQueue<QueuedJob> queue;

    public LocalQueueJobDispatchTransport(int queueCapacity) {
        this.queue = queueCapacity <= 0 ? new SynchronousQueue<>() : new ArrayBlockingQueue<>(queueCapacity);
    }

    @Override
    public boolean publish(QueuedJob queuedJob) {
        return queue.offer(queuedJob);
    }

    @Override
    public QueuedJob take() throws InterruptedException {
        return queue.take();
    }

    @Override
    public int depth() {
        return queue.size();
    }

    @Override
    public int remainingCapacity() {
        return queue.remainingCapacity();
    }

    @Override
    public DispatchTransportType type() {
        return DispatchTransportType.LOCAL;
    }
}
