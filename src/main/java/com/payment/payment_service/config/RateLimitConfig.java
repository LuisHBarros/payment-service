package com.payment.payment_service.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.lettuce.core.RedisClient;

@Configuration
public class RateLimitConfig {

    @Bean
    @ConditionalOnBean(RedisClient.class)
    @ConditionalOnProperty(prefix = "rate-limit", name = "enabled", havingValue = "true")
    public RateLimitFilter rateLimitFilter(RateLimitProperties properties, RedisClient redisClient) {
        return new RateLimitFilter(properties, redisClient);
    }
}
