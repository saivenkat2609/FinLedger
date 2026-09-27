package com.ledger.gateway.controller;

import com.ledger.gateway.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestController
public class FallbackController {

    @GetMapping("/service-unavailable")
    public Mono<ResponseEntity<ErrorResponse>> serviceUnavailable(ServerWebExchange exchange) {
        String correlationId = MDC.get("X-Correlation-ID");
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        String serviceName = extractServiceFromPath(exchange.getRequest().getPath().value());

        log.warn("[{}] Circuit breaker is OPEN for service: {}. Returning 503.",
            correlationId, serviceName);

        ErrorResponse errorResponse = ErrorResponse.builder()
            .timestamp(Instant.now().toString())
            .status(HttpStatus.SERVICE_UNAVAILABLE.value())
            .error("Service Unavailable")
            .message("The downstream service (" + serviceName + ") is temporarily unavailable. " +
                "The circuit breaker is open. Please retry after 30 seconds.")
            .path(exchange.getRequest().getPath().value())
            .correlationId(correlationId)
            .build();

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse));
    }

    private String extractServiceFromPath(String path) {
        if (path.startsWith("/auth")) return "auth-service";
        if (path.startsWith("/api")) return "ledger-service";
        if (path.startsWith("/reports")) return "reporting-service";
        return "unknown-service";
    }
}
