package com.jobseekercopilot.jobfindergateway.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.util.stream.Stream;
import org.apache.hc.client5.http.config.RequestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.PropertySource;

@SuppressWarnings("deprecation")
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
                () -> config.restTemplate(0, 250, 4_000, customizers));
        assertThrows(
                IllegalArgumentException.class,
                () -> config.restTemplate(500, 0, 4_000, customizers));
        assertThrows(
                IllegalArgumentException.class,
                () -> config.restTemplate(500, 250, 60_001, customizers));
    }

    @Test
    void alignsTheDefaultResponseTimeoutWithTheGatewayRequestBudget()
            throws IOException {
        PropertySource<?> application = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .get(0);

        assertEquals(
                "${JOB_FINDER_RESPONSE_TIMEOUT_MS:4000}",
                application.getProperty(
                        "job-finder.downstream.response-timeout-ms"));
        assertEquals(
                "${JOB_FINDER_REQUEST_DEADLINE_MS:4000}",
                application.getProperty(
                        "job-finder.downstream.request-deadline-ms"));
    }

    @Test
    void usesTheFullConfiguredResponseTimeoutWhileTheBudgetRemains() {
        RequestConfig requestConfig = RestTemplateConfig.boundedRequestConfig(
                Duration.ofMillis(500),
                Duration.ofMillis(250),
                Duration.ofMillis(4_000),
                Duration.ofMillis(4_000));

        assertEquals(
                4_000,
                requestConfig.getResponseTimeout().toMilliseconds());
    }

    @Test
    void boundsEveryDownstreamTimeoutByTheRemainingRequestBudget() {
        RequestConfig requestConfig = RestTemplateConfig.boundedRequestConfig(
                Duration.ofMillis(500),
                Duration.ofMillis(250),
                Duration.ofMillis(4_000),
                Duration.ofMillis(100));

        assertEquals(
                100,
                requestConfig.getConnectTimeout().toMilliseconds());
        assertEquals(
                100,
                requestConfig.getConnectionRequestTimeout().toMilliseconds());
        assertEquals(
                100,
                requestConfig.getResponseTimeout().toMilliseconds());
    }

    @Test
    void keepsAnExpiredBudgetAsAFiniteTimeout() {
        RequestConfig requestConfig = RestTemplateConfig.boundedRequestConfig(
                Duration.ofMillis(500),
                Duration.ofMillis(250),
                Duration.ofMillis(4_000),
                Duration.ZERO);

        assertEquals(
                1,
                requestConfig.getResponseTimeout().toMilliseconds());
    }
}
