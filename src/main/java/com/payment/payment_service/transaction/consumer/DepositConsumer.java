package com.payment.payment_service.transaction.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.payment.payment_service.shared.event.DepositCompletedEvent;
import com.payment.payment_service.transaction.service.CreateTransactionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DepositConsumer {

    private final CreateTransactionService createTransactionService;

    @KafkaListener(
        topics = "${kafka.topics.deposit-completed}",
        groupId = "payment-service-transaction",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(DepositCompletedEvent event) {
        log.info("Received DepositCompletedEvent depositId={} walletId={} amount={}",
            event.depositId(), event.walletId(), event.amount());

        createTransactionService.executeCredit(
            event.walletId(),
            event.depositId(),  // usa depositId como referência — já tem idempotência por (walletId, transferId, type)
            event.amount()
        );
    }
}