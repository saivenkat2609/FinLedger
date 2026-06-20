package com.ledger.api.config;

import com.ledger.api.filter.IdempotencyFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<IdempotencyFilter> idempotencyFilterRegistration(IdempotencyFilter idempotencyFilter) {
        FilterRegistrationBean<IdempotencyFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(idempotencyFilter);
        registrationBean.addUrlPatterns("/api/transactions/*");
        registrationBean.setOrder(1);
        return registrationBean;
    }
}
