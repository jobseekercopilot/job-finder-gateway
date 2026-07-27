package com.jobseekercopilot.jobfindergateway.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    public static final String SERVICE_MDC_KEY = "serviceName";

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    private static final Pattern UUID_PATH_SEGMENT = Pattern.compile(
            "(?i)(?<=/)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?=/|$)");
    private static final Pattern SAVED_JOB_PATH_SEGMENT = Pattern.compile(
            "(?i)(?<=/api/jobs/saved/)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?=/|$)");
    private static final Pattern USER_PATH_SEGMENT = Pattern.compile(
            "(?<=/applications/user/)[^/]+");
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]+");
    private static final int MAX_CORRELATION_ID_LENGTH = 128;

    private final String serviceName;

    public CorrelationIdFilter(@Value("${spring.application.name:job-finder-gateway}") String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = safeCorrelationId(request.getHeader(HEADER_NAME));

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
        String withoutSavedJobIds =
                SAVED_JOB_PATH_SEGMENT.matcher(path).replaceAll("{savedJobId}");
        String withoutApplicationIds =
                UUID_PATH_SEGMENT.matcher(withoutSavedJobIds).replaceAll("{applicationId}");
        return USER_PATH_SEGMENT.matcher(withoutApplicationIds).replaceAll("{subject}");
    }

    public static String safeCorrelationId(String candidate) {
        if (!StringUtils.hasText(candidate)
                || candidate.length() > MAX_CORRELATION_ID_LENGTH
                || !SAFE_CORRELATION_ID.matcher(candidate).matches()) {
            return UUID.randomUUID().toString();
        }
        return candidate;
    }

    public static String currentCorrelationId() {
        String correlationId = MDC.get(MDC_KEY);
        return StringUtils.hasText(correlationId)
                ? correlationId
                : UUID.randomUUID().toString();
    }
}
