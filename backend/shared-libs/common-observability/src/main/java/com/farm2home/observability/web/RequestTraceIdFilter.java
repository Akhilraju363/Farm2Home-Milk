package com.farm2home.observability.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Assigns/propagates a correlation id for every request and binds it (plus method/path)
 * to the MDC so every log line emitted while handling the request carries it. Also logs
 * the request/response envelope (method, URI, client IP, headers, status, duration).
 */
public class RequestTraceIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_HTTP_METHOD = "httpMethod";
    public static final String MDC_HTTP_PATH = "httpPath";

    // Never written to logs: carry credentials/session tokens that must not end up in a log file.
    // x-internal-auth is the shared FARM2HOME_GATEWAY_INTERNAL_SECRET (proof a request came from the
    // api-gateway / a trusted service): logging it would write the secret to every service's log on
    // every internal call.
    private static final Set<String> SENSITIVE_HEADERS =
            Set.of("authorization", "cookie", "set-cookie", "x-internal-auth");

    private static final Logger log = LoggerFactory.getLogger(RequestTraceIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        MDC.put(MDC_CORRELATION_ID, correlationId);
        MDC.put(MDC_HTTP_METHOD, request.getMethod());
        MDC.put(MDC_HTTP_PATH, request.getRequestURI());

        long start = System.currentTimeMillis();
        try {
            log.info("Incoming request {} {} clientIp={} headers={{}}",
                    request.getMethod(), request.getRequestURI(), clientIp(request), headers(request));
            chain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            log.info("Completed request {} {} status={} durationMs={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_HTTP_METHOD);
            MDC.remove(MDC_HTTP_PATH);
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String headers(HttpServletRequest request) {
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return "";
        }
        return StreamSupport.stream(Collections.list(names).spliterator(), false)
                .filter(name -> !SENSITIVE_HEADERS.contains(name.toLowerCase()))
                .map(name -> name + "=" + request.getHeader(name))
                .collect(Collectors.joining(", "));
    }
}
