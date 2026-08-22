package com.jobseekercopilot.jobfindergateway.controller;

import com.jobseekercopilot.jobfindergateway.logging.CorrelationIdFilter;
import com.jobseekercopilot.jobfindergateway.model.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = JobSearchController.class)
public class JobSearchExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> invalidRequest() {
        return error(
                HttpStatus.BAD_REQUEST,
                "JOB_FINDER_INVALID_REQUEST",
                "The request does not meet the documented requirements.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> malformedJson() {
        return error(
                HttpStatus.BAD_REQUEST,
                "JOB_FINDER_MALFORMED_JSON",
                "The request body must contain valid JSON.");
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                "1",
                code,
                message,
                CorrelationIdFilter.currentCorrelationId()));
    }
}
