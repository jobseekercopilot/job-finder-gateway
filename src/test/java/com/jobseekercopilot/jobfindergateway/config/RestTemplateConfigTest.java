package com.jobseekercopilot.jobfindergateway.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.client.RestTemplateCustomizer;

class RestTemplateConfigTest {

    private final RestTemplateConfig config = new RestTemplateConfig();

    @SuppressWarnings("unchecked")
    private final ObjectProvider<RestTemplateCustomizer> customizers =
            org.mockito.Mockito.mock(ObjectProvider.class);

    @Test
    void rejectsDisabledOrUnboundedTimeoutConfiguration() {
        when(customizers.orderedStream()).thenReturn(Stream.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> config.restTemplate(0, 250, 2_500, customizers));
        assertThrows(
                IllegalArgumentException.class,
                () -> config.restTemplate(500, 0, 2_500, customizers));
        assertThrows(
                IllegalArgumentException.class,
                () -> config.restTemplate(500, 250, 60_001, customizers));
    }
}
