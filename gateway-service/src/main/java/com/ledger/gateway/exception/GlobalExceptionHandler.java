package com.ledger.gateway.exception;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.DefaultErrorWebExceptionHandler;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public class GlobalExceptionHandler extends DefaultErrorWebExceptionHandler {

    public GlobalExceptionHandler(ErrorAttributes errorAttributes,
                                  WebProperties.Resources resources,
                                  ApplicationContext applicationContext) {
        super(errorAttributes, resources, applicationContext);
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(
                RequestPredicates.all(),
                request -> renderErrorResponse(request)
        );
    }

    private Mono<ServerResponse> renderErrorResponse(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        HttpStatus status = getHttpStatus(exchange);
        String correlationId = MDC.get("X-Correlation-ID");
        String message = getErrorMessage(exchange, status);

        Map<String, Object> errorResponse = new LinkedHashMap<>();
        errorResponse.put("timestamp", Instant.now().toString());
        errorResponse.put("status", status.value());
        errorResponse.put("error", status.getReasonPhrase());
        errorResponse.put("message", message);
        errorResponse.put("path", path);
        if (correlationId != null) {
            errorResponse.put("correlationId", correlationId);
        }

        log.warn("Error response: status={}, path={}, message={}, correlationId={}",
                status.value(), path, message, correlationId);

        return ServerResponse
                .status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(errorResponse);
    }

    private HttpStatus getHttpStatus(ServerWebExchange exchange) {
        Integer statusCode = exchange.getResponse().getStatusCode() != null
                ? exchange.getResponse().getStatusCode().value()
                : 500;

        return HttpStatus.resolve(statusCode);
    }

    private String getErrorMessage(ServerWebExchange exchange, HttpStatus status) {
        // Try to get the message from response status
        String message = exchange.getAttribute("javax.servlet.error.message");

        // Provide meaningful default messages based on status code
        if (message == null || message.isEmpty()) {
            message = switch (status) {
                case BAD_REQUEST -> "Bad request. Please check your input.";
                case UNAUTHORIZED -> "Unauthorized. Invalid or missing authentication.";
                case FORBIDDEN -> "Forbidden. You don't have permission to access this resource.";
                case NOT_FOUND -> "Not found. The requested resource does not exist.";
                case METHOD_NOT_ALLOWED -> "Method not allowed for this endpoint.";
                case CONFLICT -> "Conflict. The request conflicts with existing data.";
                case INTERNAL_SERVER_ERROR -> "Internal server error. Please try again later.";
                case BAD_GATEWAY -> "Bad gateway. Upstream service is unavailable.";
                case SERVICE_UNAVAILABLE -> "Service unavailable. Please try again later.";
                case GATEWAY_TIMEOUT -> "Gateway timeout. The request took too long.";
                default -> status.getReasonPhrase();
            };
        }

        return message;
    }
}
