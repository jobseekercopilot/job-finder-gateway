package com.jobseekercopilot.jobfindergateway.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.jobfindergateway.model.dto.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SearchRequestSizeFilter extends OncePerRequestFilter {

    static final String SEARCH_PATH = "/api/jobs/search";
    static final int ABSOLUTE_MAX_BYTES = 262_144;

    private final ObjectMapper objectMapper;
    private final int maxBytes;

    public SearchRequestSizeFilter(
            ObjectMapper objectMapper,
            @Value("${job-finder.http.max-search-request-bytes:65536}") int maxBytes) {
        if (maxBytes < 1_024 || maxBytes > ABSOLUTE_MAX_BYTES) {
            throw new IllegalStateException(
                    "Job Finder maximum search request bytes must be between 1024 and "
                            + ABSOLUTE_MAX_BYTES);
        }
        this.objectMapper = objectMapper;
        this.maxBytes = maxBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !SEARCH_PATH.equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            reject(response);
            return;
        }

        byte[] body = readBounded(request, maxBytes);
        if (body == null) {
            reject(response);
            return;
        }
        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private byte[] readBounded(HttpServletRequest request, int limit) throws IOException {
        try (ServletInputStream input = request.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream(
                     Math.min(Math.max(request.getContentLength(), 0), limit))) {
            byte[] buffer = new byte[8_192];
            int remaining = limit + 1;
            while (remaining > 0) {
                int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read < 0) {
                    return output.toByteArray();
                }
                output.write(buffer, 0, read);
                remaining -= read;
            }
            return null;
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(
                "1",
                "JOB_SEARCH_PAYLOAD_TOO_LARGE",
                "The job search request body must not exceed " + maxBytes + " bytes.",
                CorrelationIdFilter.currentCorrelationId()));
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new CachedBodyInputStream(body);
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null
                    ? StandardCharsets.UTF_8
                    : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    private static final class CachedBodyInputStream extends ServletInputStream {

        private final ByteArrayInputStream input;

        private CachedBodyInputStream(byte[] body) {
            input = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return input.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            return input.read(bytes, offset, length);
        }

        @Override
        public boolean isFinished() {
            return input.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            try {
                if (!isFinished()) {
                    readListener.onDataAvailable();
                }
                if (isFinished()) {
                    readListener.onAllDataRead();
                }
            } catch (IOException exception) {
                readListener.onError(exception);
            }
        }
    }
}
