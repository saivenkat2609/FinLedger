package com.ledger.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Development Server (via API Gateway)"),
                        new Server()
                                .url("http://localhost:8082")
                                .description("Direct Ledger Service Access")
                ))
                .info(new Info()
                        .title("FinLedger API")
                        .description("Production-grade microservices payment ledger system with double-entry bookkeeping, " +
                                    "Kafka event streaming, reconciliation engine, and distributed tracing.\n\n" +
                                    "**Key Features:**\n" +
                                    "- Double-entry accounting (debits always equal credits)\n" +
                                    "- Atomic transaction settlement with idempotency protection\n" +
                                    "- Async event streaming via Kafka\n" +
                                    "- Daily reconciliation with discrepancy detection\n" +
                                    "- Redis-cached balance queries (<50ms)\n" +
                                    "- Correlation ID tracing across all services\n" +
                                    "- JSON structured logging for production monitoring\n" +
                                    "- Health checks for database, Redis, and Kafka\n\n" +
                                    "**Endpoints are organized by domain:**\n" +
                                    "- `/api/accounts` - Account management\n" +
                                    "- `/api/transactions` - Transaction posting\n" +
                                    "- `/api/reconciliation` - Balance verification and reporting")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("FinLedger Development")
                                .email("support@finledger.com")
                                .url("https://github.com/finledger/finledger"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")));
    }
}
