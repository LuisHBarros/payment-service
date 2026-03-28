package com.payment.payment_service.transfer.consumer;

import java.util.Objects;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.payment.payment_service.shared.event.TransferStatusChangedEvent;
import com.payment.payment_service.transfer.entity.TransferEntity;
import com.payment.payment_service.transfer.exception.TransferNotFoundException;
import com.payment.payment_service.transfer.repository.TransferRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferStatusConsumer {

    private final TransferRepository transferRepository;

    @KafkaListener(
        topics = "${kafka.topics.transfer-status}",
        groupId = "payment-service-transfer",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(TransferStatusChangedEvent event) {
        log.info("Received TransferStatusChangedEvent transferId={} status={}",
                 event.transferId(), event.status());

        TransferEntity transfer = transferRepository.findById(Objects.requireNonNull(event.transferId()))
            .orElseThrow(() -> new TransferNotFoundException(
                "transfer not found: " + event.transferId()));

        // Idempotência: só atualiza se status for diferente
        if (transfer.getStatus() != event.status()) {
            transfer.setStatus(event.status());
            transferRepository.save(transfer);
            log.info("Updated transfer status to {} for transferId={}",
                     event.status(), event.transferId());
        } else {
            log.info("Transfer status already {} for transferId={}, skipping update",
                     event.status(), event.transferId());
        }
    }
}
