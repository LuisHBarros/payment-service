package com.payment.payment_service.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestRedisConfig {

    private RedisClient redisClient;

    @Bean
    public RedisClient redisClient(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port) {
        // Create a RedisClient using the actual container connection details
        this.redisClient = RedisClient.create(RedisURI.create("redis://" + host + ":" + port));
        return redisClient;
    }

    @PreDestroy
    public void destroy() {
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }
}