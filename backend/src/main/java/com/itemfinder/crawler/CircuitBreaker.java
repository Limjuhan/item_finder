package com.itemfinder.crawler;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final String platformName;
    private final int failureThreshold;
    private final long delayMillis;

    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);

    public CircuitBreaker(String platformName, int failureThreshold, long delayMillis) {
        this.platformName = platformName;
        this.failureThreshold = failureThreshold;
        this.delayMillis = delayMillis;
    }

    public State getState() {
        if (failureCount.get() < failureThreshold) {
            return State.CLOSED;
        }
        long elapsed = System.currentTimeMillis() - lastFailureTime.get();
        if (elapsed >= delayMillis) {
            return State.HALF_OPEN;
        }
        return State.OPEN;
    }

    public boolean allowRequest() {
        State state = getState();
        return state == State.CLOSED || state == State.HALF_OPEN;
    }

    public void recordSuccess() {
        failureCount.set(0);
        lastFailureTime.set(0);
        log.info("[CircuitBreaker][{}] Reset to CLOSED", platformName);
    }

    public void recordFailure() {
        int count = failureCount.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());
        log.warn("[CircuitBreaker][{}] Failure recorded ({}/{}), state={}",
                platformName, count, failureThreshold, getState());
    }
}
