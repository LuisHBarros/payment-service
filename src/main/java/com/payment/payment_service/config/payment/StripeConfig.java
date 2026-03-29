package com.payment.payment_service.config.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import com.stripe.Stripe;

@Configuration
public class StripeConfig {
    
    @Value("${stripe.secret-key}")
    private String secretKey;
    
    @PostConstruct
    public void configure() {
        Stripe.apiKey = secretKey;
    }
}
