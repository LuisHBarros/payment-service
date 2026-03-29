package com.payment.payment_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.payment.payment_service.config.CorsProperties;
import com.payment.payment_service.config.RateLimitProperties;


@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({ RateLimitProperties.class, CorsProperties.class })
public class PaymentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentServiceApplication.class, args);
	}

}
