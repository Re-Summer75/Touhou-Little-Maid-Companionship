package com.laixia.maidintelligence.feature.physics.client.benchmark;

import java.lang.management.ManagementFactory;

final class ThreadAllocationMeter {
    private final com.sun.management.ThreadMXBean bean;
    private final long threadId;

    private ThreadAllocationMeter(
            com.sun.management.ThreadMXBean bean,
            long threadId
    ) {
        this.bean = bean;
        this.threadId = threadId;
    }

    static ThreadAllocationMeter create() {
        java.lang.management.ThreadMXBean platform =
                ManagementFactory.getThreadMXBean();
        if (!(platform instanceof com.sun.management.ThreadMXBean bean)
                || !bean.isThreadAllocatedMemorySupported()) {
            return new ThreadAllocationMeter(null, -1L);
        }
        if (!bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }
        return new ThreadAllocationMeter(
                bean,
                Thread.currentThread().getId()
        );
    }

    boolean available() {
        return bean != null;
    }

    long currentBytes() {
        return available() ? bean.getThreadAllocatedBytes(threadId) : -1L;
    }
}
