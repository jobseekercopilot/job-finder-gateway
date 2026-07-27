package com.jobseekercopilot.jobfindergateway.config;

import com.jobseekercopilot.jobfindergateway.http.RequestDeadline;
import java.time.Duration;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(
            @Value("${job-finder.downstream.connect-timeout-ms:500}")
            long connectTimeoutMs,
            @Value("${job-finder.downstream.connection-request-timeout-ms:250}")
            long connectionRequestTimeoutMs,
            @Value("${job-finder.downstream.response-timeout-ms:2500}")
            long responseTimeoutMs,
            ObjectProvider<RestTemplateCustomizer> customizers) {
        Duration connectTimeout = positiveDuration(
                "job-finder.downstream.connect-timeout-ms", connectTimeoutMs);
        Duration connectionRequestTimeout = positiveDuration(
                "job-finder.downstream.connection-request-timeout-ms",
                connectionRequestTimeoutMs);
        Duration responseTimeout = positiveDuration(
                "job-finder.downstream.response-timeout-ms", responseTimeoutMs);

        var httpClient = HttpClients.custom()
                .disableAutomaticRetries()
                .build();
        var requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setConnectionRequestTimeout(connectionRequestTimeout);
        requestFactory.setHttpContextFactory((method, uri) -> {
            Duration remaining = RequestDeadline.remaining()
                    .orElse(responseTimeout);
            Duration boundedResponseTimeout = minPositive(
                    responseTimeout,
                    remaining);
            HttpClientContext context = HttpClientContext.create();
            context.setRequestConfig(RequestConfig.custom()
                    .setConnectTimeout(Timeout.of(minPositive(
                            connectTimeout,
                            remaining)))
                    .setConnectionRequestTimeout(Timeout.of(minPositive(
                            connectionRequestTimeout,
                            remaining)))
                    .setResponseTimeout(Timeout.of(boundedResponseTimeout))
                    .setHardCancellationEnabled(true)
                    .build());
            return context;
        });

        RestTemplate restTemplate = new RestTemplate(requestFactory);
        customizers.orderedStream()
                .forEach(customizer -> customizer.customize(restTemplate));
        return restTemplate;
    }

    private static Duration positiveDuration(String property, long milliseconds) {
        if (milliseconds < 1 || milliseconds > 60_000) {
            throw new IllegalArgumentException(
                    property + " must be between 1 and 60000 milliseconds");
        }
        return Duration.ofMillis(milliseconds);
    }

    private static Duration minPositive(Duration configured, Duration remaining) {
        Duration minimum = Duration.ofMillis(1);
        Duration boundedRemaining = remaining.compareTo(minimum) < 0
                ? minimum
                : remaining;
        return configured.compareTo(boundedRemaining) < 0
                ? configured
                : boundedRemaining;
    }
}
