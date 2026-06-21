package com.ledger.gateway.filter;

import com.ledger.gateway.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Component
public class RateLimitingFilter implements GlobalFilter, Ordered {

    private static final String RATE_LIMIT_KEY_PREFIX = "rate-limit:";
    private static final long HOUR_IN_MS = 60 * 60 * 1000;

    @Value("${app.rate-limit.authenticated:1000}")
    private int authenticatedLimit;

    @Value("${app.rate-limit.anonymous:100}")
    private int anonymousLimit;

    private final RedisTemplate<String, String> redisTemplate;

    public RateLimitingFilter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        String ipAddress = getClientIp(exchange);

        String identifier = userId != null ? userId : ipAddress;
        boolean isAuthenticated = userId != null;
        int limit = isAuthenticated ? authenticatedLimit : anonymousLimit;

        String key = generateKey(identifier);

        Long currentCount = redisTemplate.opsForValue().increment(key);

        // Set expiry only on first increment
        if (currentCount == 1) {
            redisTemplate.expire(key, Duration.ofHours(1));
        }

        // Add rate limit headers to response
        long resetTime = System.currentTimeMillis() + HOUR_IN_MS;
        exchange.getResponse().getHeaders()
                .add("X-RateLimit-Limit", String.valueOf(limit))
                .add("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - currentCount)))
                .add("X-RateLimit-Reset", String.valueOf(resetTime / 1000));

        if (currentCount > limit) {
            String correlationId = MDC.get("X-Correlation-ID");
            String path = exchange.getRequest().getURI().getPath();
            String message = "Rate limit exceeded. Max " + limit + " requests per hour.";

            ErrorResponse errorResponse = ErrorResponse.of(429, message, path, correlationId);

            log.warn("Rate limit exceeded for {}: {} requests in 1 hour (limit: {}), correlationId: {}",
                    isAuthenticated ? "user " + userId : "IP " + ipAddress,
                    currentCount, limit, correlationId);

            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().add("Retry-After", "3600");

            return exchange.getResponse().writeWith(
                    Mono.fromCallable(() -> exchange.getResponse()
                            .bufferFactory()
                            .wrap(errorResponse.toJson().getBytes(StandardCharsets.UTF_8)))
                            .flux()
            );
        }

        return chain.filter(exchange);
    }

    private String generateKey(String identifier) {
        long currentHour = System.currentTimeMillis() / HOUR_IN_MS;
        return RATE_LIMIT_KEY_PREFIX + identifier + ":" + currentHour;
    }

    private String getClientIp(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
