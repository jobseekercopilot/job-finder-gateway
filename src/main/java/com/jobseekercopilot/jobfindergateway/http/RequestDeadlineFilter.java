package com.jobseekercopilot.jobfindergateway.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestDeadlineFilter extends OncePerRequestFilter {

    private final Duration requestBudget;

    public RequestDeadlineFilter(
            @Value("${job-finder.downstream.request-deadline-ms:4000}")
            long requestDeadlineMs) {
        this.requestBudget = positiveDuration(
                "job-finder.downstream.request-deadline-ms",
                requestDeadlineMs);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        RequestDeadline.start(requestBudget);
        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestDeadline.clear();
        }
    }

    private static Duration positiveDuration(String property, long milliseconds) {
        if (milliseconds < 1 || milliseconds > 60_000) {
            throw new IllegalArgumentException(
                    property + " must be between 1 and 60000 milliseconds");
        }
        return Duration.ofMillis(milliseconds);
    }
}
