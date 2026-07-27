package com.jobseekercopilot.jobfindergateway.http;

import com.jobseekercopilot.jobfindergateway.logging.CorrelationIdFilter;
import com.jobseekercopilot.jobfindergateway.model.dto.ApiErrorResponse;
import java.net.SocketTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class DownstreamFailureResponses {

    private DownstreamFailureResponses() {
    }

    public static ResponseEntity<ApiErrorResponse> timeout(
            String code,
            String message) {
        return error(HttpStatus.GATEWAY_TIMEOUT, code, message);
    }

    public static ResponseEntity<ApiErrorResponse> unavailable(
            String code,
            String message) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }

    public static ResponseEntity<ApiErrorResponse> badGateway(
            String code,
            String message) {
        return error(HttpStatus.BAD_GATEWAY, code, message);
    }

    public static ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(
                        "1",
                        code,
                        message,
                        CorrelationIdFilter.currentCorrelationId()));
    }

    public static boolean isTimeout(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static boolean isMalformedResponse(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            String type = current.getClass().getSimpleName();
            if (type.startsWith("Json")
                    || type.contains("JsonProcessing")
                    || type.contains("HttpMessageConversion")
                    || type.contains("MismatchedInput")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
