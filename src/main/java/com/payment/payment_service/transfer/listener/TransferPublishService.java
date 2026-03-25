package com.payment.payment_service.transfer.listener;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import com.payment.payment_service.shared.kafka.KafkaEventProducer;
import com.payment.payment_service.shared.type.TransferStatus;
import com.payment.payment_service.transfer.event.TransferCreatedEvent;
import com.payment.payment_service.transfer.service.TransferStatusUpdateService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferPublishService {
    
    private final KafkaEventProducer kafkaEventProducer;
    private final TransferStatusUpdateService transferStatusUpdateService;


    @Retryable(
        retryFor = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void publish(TransferCreatedEvent event) {
        log.info("Attempting to publish TransferCreatedEvent for transferId={}", event.transferId());
        kafkaEventProducer.publishTransferCreated(event);
        log.info("Successfully published TransferCreatedEvent for transferId={}", event.transferId());
    }

    @Recover
    public void recover(Exception e, TransferCreatedEvent event) {
        transferStatusUpdateService.execute(event.transferId(), TransferStatus.FAILED);
    }
}
