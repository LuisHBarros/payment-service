package com.payment.payment_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.payment.payment_service.config.RateLimitProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;


@SpringBootApplication
@EnableRetry
@EnableScheduling
@EnableConfigurationProperties(RateLimitProperties.class)
public class PaymentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentServiceApplication.class, args);
	}

}
