package com.ledger.gateway.filter;

import com.ledger.gateway.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class JwtValidationFilter implements GlobalFilter, Ordered {

    private final WebClient webClient;

    public JwtValidationFilter(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("http://auth-service:8081").build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Skip JWT validation for public endpoints
        if (isPublicEndpoint(path)) {
            return chain.filter(exchange);
        }

        String token = extractToken(exchange);

        if (token == null || token.isEmpty()) {
            return onError(exchange, "Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED);
        }

        // Call auth-service to validate token
        return webClient.post()
                .uri("/auth/validate")
                .bodyValue(new TokenRequest(token))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .flatMap(response -> {
                    log.debug("JWT validated successfully. Injecting headers - User ID: {}, Roles: {}",
                            response.getUserId(), response.getRoles());

                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(exchange.getRequest().mutate()
                                    .header("X-User-Id", response.getUserId())
                                    .header("X-User-Roles", response.getRoles() != null ? response.getRoles() : "")
                                    .build())
                            .build();
                    return chain.filter(mutatedExchange);
                })
                .onErrorResume(error -> {
                    log.warn("JWT validation failed: {}", error.getMessage());
                    return onError(exchange, "Invalid or expired token", HttpStatus.UNAUTHORIZED);
                });
    }

    private String extractToken(ServerWebExchange exchange) {
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/auth/") ||
               path.startsWith("/actuator/") ||
               path.equals("/health");
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        String correlationId = MDC.get("X-Correlation-ID");
        String path = exchange.getRequest().getURI().getPath();

        ErrorResponse errorResponse = ErrorResponse.of(status.value(), message, path, correlationId);

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");

        return exchange.getResponse().writeWith(
                Mono.fromCallable(() -> exchange.getResponse()
                        .bufferFactory()
                        .wrap(errorResponse.toJson().getBytes(StandardCharsets.UTF_8)))
                        .flux()
        );
    }

    @Override
    public int getOrder() {
        return 1;
    }

    // DTOs for auth-service communication
    public static class TokenRequest {
        public String token;

        public TokenRequest(String token) {
            this.token = token;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }

    public static class TokenResponse {
        private String userId;
        private String roles;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getRoles() {
            return roles;
        }

        public void setRoles(String roles) {
            this.roles = roles;
        }
    }
}
