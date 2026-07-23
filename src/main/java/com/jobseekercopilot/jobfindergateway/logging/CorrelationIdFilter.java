package com.jobseekercopilot.jobfindergateway.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    public static final String SERVICE_MDC_KEY = "serviceName";

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    private static final Pattern UUID_PATH_SEGMENT = Pattern.compile(
            "(?i)(?<=/)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?=/|$)");
    private static final Pattern USER_PATH_SEGMENT = Pattern.compile(
            "(?<=/applications/user/)[^/]+");

    private final String serviceName;

    public CorrelationIdFilter(@Value("${spring.application.name:job-finder-gateway}") String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER_NAME);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }

        long startedAt = System.nanoTime();
        MDC.put(MDC_KEY, correlationId);
        MDC.put(SERVICE_MDC_KEY, serviceName);
        response.setHeader(HEADER_NAME, correlationId);
        String requestPath = redactedPath(request.getRequestURI());

        try {
            log.info("service={} request started method={} path={}", serviceName, request.getMethod(), requestPath);
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("service={} request completed method={} path={} status={} durationMs={}",
                    serviceName,
                    request.getMethod(),
                    requestPath,
                    response.getStatus(),
                    durationMs);
            MDC.remove(MDC_KEY);
            MDC.remove(SERVICE_MDC_KEY);
        }
    }

    static String redactedPath(String path) {
        String withoutApplicationIds =
                UUID_PATH_SEGMENT.matcher(path).replaceAll("{applicationId}");
        return USER_PATH_SEGMENT.matcher(withoutApplicationIds).replaceAll("{subject}");
    }
}
