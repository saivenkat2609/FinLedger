package com.ledger.gateway.config;

import com.ledger.gateway.exception.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.result.view.ViewResolver;

import java.util.List;

@Configuration
public class ErrorHandlingConfig {

    @Bean
    @Order(-2)
    public GlobalExceptionHandler globalExceptionHandler(ErrorAttributes errorAttributes,
                                                         WebProperties webProperties,
                                                         ApplicationContext applicationContext,
                                                         ServerCodecConfigurer serverCodecConfigurer,
                                                         List<ViewResolver> viewResolvers) {
        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler(
                errorAttributes,
                webProperties.getResources(),
                applicationContext
        );
        exceptionHandler.setViewResolvers(viewResolvers);
        exceptionHandler.setServerCodecConfigurer(serverCodecConfigurer);
        return exceptionHandler;
    }
}
