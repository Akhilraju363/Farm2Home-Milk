package com.farm2home.observability.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.farm2home.observability.web.RequestTraceIdFilter.TRACE_ID_HEADER;

/**
 * WebFlux equivalent of {@link RequestTraceIdFilter} for reactive services (the API gateway).
 * MDC is not reliably propagated across the reactive pipeline's thread hops, so the
 * correlation id is logged explicitly on each line instead of relying on MDC.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReactiveRequestTraceIdFilter implements WebFilter {

    // Never written to logs: carry credentials/session tokens that must not end up in a log file.
    private static final Set<String> SENSITIVE_HEADERS = Set.of("authorization", "cookie", "set-cookie");

    private static final Logger log = LoggerFactory.getLogger(ReactiveRequestTraceIdFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String traceId = request.getHeaders().getFirst(TRACE_ID_HEADER);
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString();
        }
        final String finalTraceId = traceId;
        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, finalTraceId);

        long start = System.currentTimeMillis();
        log.info("[{}] Incoming request {} {} clientIp={} headers={{}}", finalTraceId,
                request.getMethod(), request.getURI().getPath(), clientIp(request), headers(request.getHeaders()));

        return chain.filter(exchange)
                .doOnError(ex -> log.error("[{}] Unhandled error on {} {}: {}",
                        finalTraceId, request.getMethod(), request.getURI().getPath(), ex.getMessage(), ex))
                .doFinally(signal -> {
                    long durationMs = System.currentTimeMillis() - start;
                    log.info("[{}] Completed request {} {} status={} durationMs={}",
                            finalTraceId, request.getMethod(), request.getURI().getPath(),
                            exchange.getResponse().getStatusCode(), durationMs);
                });
    }

    private static String clientIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress != null && remoteAddress.getAddress() != null
                ? remoteAddress.getAddress().getHostAddress()
                : "unknown";
    }

    private static String headers(HttpHeaders headers) {
        return headers.keySet().stream()
                .filter(name -> !SENSITIVE_HEADERS.contains(name.toLowerCase()))
                .map(name -> name + "=" + String.join(",", headers.get(name)))
                .collect(Collectors.joining(", "));
    }
}
