package com.payment.payment_service.shared.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.payment.payment_service.shared.event.TransferStatusChangedEvent;
import com.payment.payment_service.shared.event.UserCreatedEvent;
import com.payment.payment_service.shared.event.WalletCreditedEvent;
import com.payment.payment_service.shared.event.WalletDebitedEvent;
import com.payment.payment_service.transfer.event.TransferCreatedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KafkaEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.users}")
    private String usersTopic;

    @Value("${kafka.topics.wallets}")
    private String walletsTopic;

    @Value("${kafka.topics.transfers}")
    private String transfersTopic;

    public void publishUserCreated(UserCreatedEvent event) {
        kafkaTemplate.send(usersTopic, event.userId().toString(), event);
    }

    public void publishWalletDebited(WalletDebitedEvent event) {
        kafkaTemplate.send(walletsTopic, event.walletId().toString(), event);
    }

    public void publishWalletCredited(WalletCreditedEvent event) {
        kafkaTemplate.send(walletsTopic, event.walletId().toString(), event);
    }

    public void publishTransferStatusChanged(TransferStatusChangedEvent event) {
        kafkaTemplate.send(transfersTopic, event.transferId().toString(), event);
    }

    public void publishTransferCreated(TransferCreatedEvent event) {
        kafkaTemplate.send(transfersTopic, event.transferId().toString(), event);
    }
}