package com.jobseekercopilot.jobfindergateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.jobfindergateway.logging.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class JobFinderAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JobFinderAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String correlationId = CorrelationIdFilter.currentCorrelationId();
        response.setHeader(CorrelationIdFilter.HEADER_NAME, correlationId);
        objectMapper.writeValue(response.getOutputStream(), new AuthenticationError(
                "1",
                "JOB_FINDER_AUTHENTICATION_REQUIRED",
                "A valid Bearer access token is required.",
                correlationId));
    }

    private record AuthenticationError(
            String schemaVersion,
            String code,
            String message,
            String correlationId) {
    }
}
