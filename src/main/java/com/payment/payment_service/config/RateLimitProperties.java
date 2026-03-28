package com.payment.payment_service.config;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private Map<String, EndpointLimit> endpoints = new HashMap<>();
    
    @Data
    public static class EndpointLimit {
        private int capacity;
        private int refillTokens;
        private int periodMilliseconds;
    }

    public EndpointLimit getEndpointLimit(String method, String path) {
        return endpoints.get(method + ":" + path);
    }
}
