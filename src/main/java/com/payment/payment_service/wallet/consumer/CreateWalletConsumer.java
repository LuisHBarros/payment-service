package com.payment.payment_service.wallet.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.payment.payment_service.shared.event.UserCreatedEvent;
import com.payment.payment_service.wallet.service.CreateWalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreateWalletConsumer {

    private final CreateWalletService createWalletService;

    @KafkaListener(
        topics = "${kafka.topics.users}",
        groupId = "payment-service-wallet",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(UserCreatedEvent event) {
        log.info("Received UserCreatedEvent for userId={}", event.userId());
        createWalletService.execute(event.userId());
    }
}