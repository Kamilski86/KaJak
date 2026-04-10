package com.canda.epcis.infrastructure.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that assigns a correlation ID to every inbound request.
 * <p>
 * If the caller supplies {@code X-Correlation-ID}, that value is used; otherwise a
 * random UUID is generated. The ID is placed into the SLF4J MDC under the key
 * {@code correlationId} so it appears in every log line for the duration of the
 * request. The same value is echoed back in the {@code X-Correlation-ID} response
 * header so callers can correlate their own logs with ours.
 */
@Component
@Order(1)
public class CorrelationIdFilter implements Filter {

    public static final String MDC_KEY = "correlationId";
    public static final String HEADER = "X-Correlation-ID";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpRes = (HttpServletResponse) response;

        String correlationId = httpReq.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        httpRes.setHeader(HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
