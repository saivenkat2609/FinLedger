package com.ledger.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Component
public class RequestTransformationFilter implements GlobalFilter, Ordered {

    private static final String REQUEST_TIMESTAMP_HEADER = "X-Request-Timestamp";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Add request timestamp header
        String requestTimestamp = Instant.now().toString();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .header(REQUEST_TIMESTAMP_HEADER, requestTimestamp)
                        .build())
                .build();

        // Also add to response headers for traceability
        exchange.getResponse().getHeaders().add(REQUEST_TIMESTAMP_HEADER, requestTimestamp);

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return 2;
    }
}
