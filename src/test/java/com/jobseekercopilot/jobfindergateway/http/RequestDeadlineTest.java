package com.jobseekercopilot.jobfindergateway.http;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RequestDeadlineTest {

    @AfterEach
    void clearDeadline() {
        RequestDeadline.clear();
    }

    @Test
    void exposesOnlyTheRemainingMonotonicBudget() {
        RequestDeadline.start(Duration.ofSeconds(1));

        Duration remaining = RequestDeadline.remaining().orElseThrow();

        assertTrue(!remaining.isNegative());
        assertTrue(remaining.compareTo(Duration.ofSeconds(1)) <= 0);
    }

    @Test
    void rejectsDisabledOrUnboundedRequestBudgets() {
        assertThrows(IllegalArgumentException.class, () -> new RequestDeadlineFilter(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RequestDeadlineFilter(60_001));
    }
}
