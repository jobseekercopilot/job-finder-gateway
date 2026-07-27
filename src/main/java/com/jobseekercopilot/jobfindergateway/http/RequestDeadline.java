package com.jobseekercopilot.jobfindergateway.http;

import java.time.Duration;
import java.util.Optional;

/**
 * Holds the monotonic deadline for the current servlet request.
 *
 * <p>The value is deliberately thread-local because the gateway uses synchronous
 * Spring MVC and RestTemplate calls. It must not be copied to asynchronous work.
 */
public final class RequestDeadline {

    private static final ThreadLocal<Long> DEADLINE_NANOS = new ThreadLocal<>();

    private RequestDeadline() {
    }

    public static void start(Duration budget) {
        DEADLINE_NANOS.set(System.nanoTime() + budget.toNanos());
    }

    public static Optional<Duration> remaining() {
        Long deadline = DEADLINE_NANOS.get();
        if (deadline == null) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofNanos(Math.max(0, deadline - System.nanoTime())));
    }

    public static void clear() {
        DEADLINE_NANOS.remove();
    }
}
