package com.jobseekercopilot.jobfindergateway.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SearchRequestSizeFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsOnlyBoundedFailClosedConfiguration() {
        assertDoesNotThrow(() -> new SearchRequestSizeFilter(objectMapper, 1_024));
        assertDoesNotThrow(() -> new SearchRequestSizeFilter(objectMapper, 65_536));
        assertDoesNotThrow(() -> new SearchRequestSizeFilter(
                objectMapper,
                SearchRequestSizeFilter.ABSOLUTE_MAX_BYTES));

        assertThrows(
                IllegalStateException.class,
                () -> new SearchRequestSizeFilter(objectMapper, 1_023));
        assertThrows(
                IllegalStateException.class,
                () -> new SearchRequestSizeFilter(
                        objectMapper,
                        SearchRequestSizeFilter.ABSOLUTE_MAX_BYTES + 1));
    }
}
