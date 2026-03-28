package com.payment.payment_service.wallet.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.payment.payment_service.shared.event.TransferStatusChangedEvent;
import com.payment.payment_service.shared.kafka.KafkaEventProducer;
import com.payment.payment_service.shared.type.TransferStatus;
import com.payment.payment_service.transfer.event.TransferCreatedEvent;
import com.payment.payment_service.wallet.service.ProcessTransferService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferWalletConsumer {

    private final ProcessTransferService processTransferService;
    private final KafkaEventProducer kafkaEventProducer;

    @KafkaListener(
        topics = "${kafka.topics.transfer-created}",
        groupId = "payment-service-wallet",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(TransferCreatedEvent event) {
        try {
            log.info("Received TransferCreatedEvent transferId={} sourceWallet={} destWallet={}",
                     event.transferId(), event.sourceWalletId(), event.destinationWalletId());
            processTransfer(event);
        } catch (Exception ex) {
            log.error("Error processing TransferCreatedEvent for transferId={}", event.transferId(), ex);
            throw ex; // Deixa Kafka retry (3 tentativas antes do DLT)
        }
    }

    @Transactional
    private void processTransfer(TransferCreatedEvent event) {
        try {
            // Process transfer with lock ordering to prevent deadlocks
            processTransferService.execute(
                event.transferId(),
                event.sourceWalletId(),
                event.destinationWalletId(),
                event.amount()
            );

            log.info("Successfully processed transferId={}", event.transferId());
        } catch (Exception e) {
            log.error("Failed to process transferId={}", event.transferId(), e);

            // Publish FAILED status on error
            try {
                kafkaEventProducer.publishTransferStatusChanged(
                    new TransferStatusChangedEvent(event.transferId(), TransferStatus.FAILED)
                );
            } catch (Exception ex) {
                log.error("Failed to publish FAILED status for transferId={}", event.transferId(), ex);
            }

            throw e;
        }
    }
}
