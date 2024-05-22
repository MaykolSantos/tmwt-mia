package com.dissertation.tmwt;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskMonitor {
    private AtomicInteger taskCount = new AtomicInteger(0);
    private ConcurrentHashMap<String, ConcurrentHashMap<String, String>> sensorData = new ConcurrentHashMap<>();

    public void incrementTasks() {
        taskCount.incrementAndGet();
    }

    public void decrementTasks() {
        if (taskCount.get() > 0) {
            taskCount.decrementAndGet();
        }

    }

    public int getNumberOfTasks() {
        return taskCount.get();
    }
}
