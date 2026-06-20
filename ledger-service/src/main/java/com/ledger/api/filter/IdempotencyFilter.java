package com.ledger.api.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.api.domain.IdempotencyKey;
import com.ledger.api.repository.IdempotencyKeyRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class IdempotencyFilter implements Filter {
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyFilter(IdempotencyKeyRepository idempotencyKeyRepository, ObjectMapper objectMapper) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Only apply to write operations (POST, PUT, PATCH)
        String method = httpRequest.getMethod();
        if (!method.equals("POST") && !method.equals("PUT") && !method.equals("PATCH")) {
            chain.doFilter(request, response);
            return;
        }

        // Extract Idempotency-Key header
        String idempotencyKey = httpRequest.getHeader("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        // Try to insert the key (atomic race condition guard)
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
        int rowsAffected = idempotencyKeyRepository.insertIfAbsent(
            idempotencyKey,
            "{\"processing\": true}",
            0,
            expiresAt
        );

        if (rowsAffected == 0) {
            // Key already exists — return stored response
            Optional<IdempotencyKey> existingKey = idempotencyKeyRepository.findByKey(idempotencyKey);
            if (existingKey.isPresent()) {
                httpResponse.setStatus(existingKey.get().getHttpStatusCode());
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write(existingKey.get().getResponsePayload());
                return;
            }
        }

        // New request — process and capture response
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(httpResponse);
        try {
            chain.doFilter(request, wrappedResponse);
        } finally {
            // Store the response in idempotency_keys
            byte[] responseBody = wrappedResponse.getContentAsByteArray();
            String responsePayload = new String(responseBody);
            int statusCode = wrappedResponse.getStatus();

            idempotencyKeyRepository.save(new IdempotencyKey(
                idempotencyKey,
                responsePayload,
                statusCode,
                expiresAt
            ));

            wrappedResponse.copyBodyToResponse();
        }
    }
}
