package com.dissertation.tmwt;

import java.util.concurrent.atomic.AtomicInteger;

public class TaskMonitor {
    private AtomicInteger taskCount = new AtomicInteger(0);

    public void incrementTasks() {
        taskCount.incrementAndGet();
    }

    public void decrementTasks() {
        taskCount.decrementAndGet();
    }

    public int getNumberOfTasks() {
        return taskCount.get();
    }
}
