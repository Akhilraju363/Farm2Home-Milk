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

import static com.farm2home.observability.web.RequestTraceIdFilter.CORRELATION_ID_HEADER;

/**
 * WebFlux equivalent of {@link RequestTraceIdFilter} for reactive services (the API gateway).
 * MDC is not reliably propagated across the reactive pipeline's thread hops, so the
 * correlation id is logged explicitly on each line instead of relying on MDC.
 *
 * Unlike the servlet filter, this one also has to make sure the ID actually reaches every
 * downstream microservice: the gateway is the single entry point for all synchronous traffic,
 * so if a client didn't send X-Correlation-ID, the freshly generated ID must be injected onto
 * the proxied request here - otherwise Spring Cloud Gateway would forward the request exactly
 * as received (with no such header) and each downstream service would mint its own, breaking
 * correlation across the whole call chain.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReactiveRequestTraceIdFilter implements WebFilter {

    // Never written to logs: carry credentials/session tokens that must not end up in a log file.
    // x-internal-auth is the shared FARM2HOME_GATEWAY_INTERNAL_SECRET (proof a request came from the
    // api-gateway / a trusted service): logging it would write the secret to every service's log on
    // every internal call.
    private static final Set<String> SENSITIVE_HEADERS =
            Set.of("authorization", "cookie", "set-cookie", "x-internal-auth");

    private static final Logger log = LoggerFactory.getLogger(ReactiveRequestTraceIdFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String incoming = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        final String correlationId = StringUtils.hasText(incoming) ? incoming : UUID.randomUUID().toString();

        ServerWebExchange effectiveExchange = exchange;
        if (!StringUtils.hasText(incoming)) {
            // Only mutate when we had to generate one - if the client already sent the header,
            // it's already present on the request being forwarded and re-adding it would just
            // duplicate the value.
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header(CORRELATION_ID_HEADER, correlationId)
                    .build();
            effectiveExchange = exchange.mutate().request(mutatedRequest).build();
        }
        effectiveExchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);

        long start = System.currentTimeMillis();
        log.info("[{}] Incoming request {} {} clientIp={} headers={{}}", correlationId,
                request.getMethod(), request.getURI().getPath(), clientIp(request), headers(request.getHeaders()));

        ServerWebExchange finalExchange = effectiveExchange;
        return chain.filter(effectiveExchange)
                .doOnError(ex -> log.error("[{}] Unhandled error on {} {}: {}",
                        correlationId, request.getMethod(), request.getURI().getPath(), ex.getMessage(), ex))
                .doFinally(signal -> {
                    long durationMs = System.currentTimeMillis() - start;
                    log.info("[{}] Completed request {} {} status={} durationMs={}",
                            correlationId, request.getMethod(), request.getURI().getPath(),
                            finalExchange.getResponse().getStatusCode(), durationMs);
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
