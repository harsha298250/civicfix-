package com.civicfix.config.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * CorrelationIdFilter — Intercepts all HTTP requests to establish end-to-end request tracing.
 * Captures incoming X-Request-ID / X-Correlation-ID headers or generates a unique UUID.
 * Binds the identifier to SLF4J MDC for structured log output and sets it in the response header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Request-ID";
    public static final String ALT_CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getHeader(ALT_CORRELATION_ID_HEADER);
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        } else {
            // Sanitize correlationId to alphanumeric and hyphens (max 64 chars) to prevent header injection
            correlationId = correlationId.replaceAll("[^a-zA-Z0-9-]", "");
            if (correlationId.length() > 64) {
                correlationId = correlationId.substring(0, 64);
            }
            if (correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }
        }

        try {
            MDC.put(MDC_KEY, correlationId);
            request.setAttribute(MDC_KEY, correlationId);
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
