package com.ledger.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ResilienceConfig {

    @Bean
    public RegistryEventConsumer<CircuitBreaker> circuitBreakerEventConsumer() {
        return new RegistryEventConsumer<CircuitBreaker>() {
            @Override
            public void onEntryAdded(EntryAddedEvent<CircuitBreaker> entryAddedEvent) {
                CircuitBreaker circuitBreaker = entryAddedEvent.getAddedEntry();
                log.info("CircuitBreaker '{}' registered with state: {}",
                    circuitBreaker.getName(), circuitBreaker.getState());

                circuitBreaker.getEventPublisher()
                    .onStateTransition(event ->
                        log.warn("CircuitBreaker '{}' state changed from {} to {}",
                            circuitBreaker.getName(), event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState()))
                    .onError(event ->
                        log.debug("CircuitBreaker '{}' recorded error: {}",
                            circuitBreaker.getName(), event.getThrowable().getMessage()))
                    .onSuccess(event ->
                        log.debug("CircuitBreaker '{}' call succeeded", circuitBreaker.getName()));
            }

            @Override
            public void onEntryRemoved(EntryRemovedEvent<CircuitBreaker> entryRemovedEvent) {
                CircuitBreaker circuitBreaker = entryRemovedEvent.getRemovedEntry();
                log.info("CircuitBreaker '{}' unregistered", circuitBreaker.getName());
            }

            @Override
            public void onEntryReplaced(EntryReplacedEvent<CircuitBreaker> entryReplacedEvent) {
                log.debug("CircuitBreaker '{}' configuration replaced",
                    entryReplacedEvent.getNewEntry().getName());
            }
        };
    }
}
