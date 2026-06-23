package com.ledger.api.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Aspect
@Component
public class CacheLoggingAspect {

    @Around("@annotation(org.springframework.cache.annotation.Cacheable)")
    public Object logCacheableMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        String correlationId = MDC.get("X-Correlation-ID");
        Object[] args = joinPoint.getArgs();
        String methodName = joinPoint.getSignature().getName();

        long startTime = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long duration = System.currentTimeMillis() - startTime;

        // If duration < 5ms, likely a cache hit; if > 20ms, likely a miss
        boolean likelyCacheHit = duration < 5;

        if (args.length > 0 && args[0] instanceof UUID) {
            UUID accountId = (UUID) args[0];
            log.debug("Cache operation: method={}, accountId={}, duration={}ms, likelyCacheHit={}, correlationId={}",
                    methodName, accountId, duration, likelyCacheHit, correlationId);
        }

        return result;
    }
}
