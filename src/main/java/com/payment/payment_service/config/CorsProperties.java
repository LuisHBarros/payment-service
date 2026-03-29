package com.payment.payment_service.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {
    private List<String> allowedOrigins = new ArrayList<>(List.of(
        "http://localhost:3000",
        "http://localhost:3333"
    ));
    private List<String> allowedMethods = new ArrayList<>(List.of(
        "GET",
        "POST",
        "PUT",
        "DELETE",
        "OPTIONS"
    ));
    private List<String> allowedHeaders = new ArrayList<>(List.of("*"));
    private boolean allowCredentials = true;
}
