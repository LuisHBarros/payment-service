package com.payment.payment_service.shared.kafka;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.payment_service.shared.entity.OutboxEntity;
import com.payment.payment_service.shared.event.WalletCreditedEvent;
import com.payment.payment_service.shared.event.WalletDebitedEvent;
import com.payment.payment_service.shared.repository.OutboxRepository;
import com.payment.payment_service.shared.type.TransferStatus;
import com.payment.payment_service.transfer.event.TransferCreatedEvent;
import com.payment.payment_service.transfer.service.TransferStatusUpdateService;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.List;


@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {
    private final OutboxRepository outboxRepository;
    private final KafkaEventProducer kafkaEventProducer;
    private final ObjectMapper objectMapper;
    private final TransferStatusUpdateService transferStatusUpdateService;
    @Value("${outbox.cleanup.retention-days:7}")
    private int retentionDays;

    @Value("${outbox.max-attempts:10}")
    private int maxAttempts;

    @Scheduled(fixedDelayString = "${outbox.poll-interval:500}")
    public void publishPendingEvents(){
        List<OutboxEntity> entries = outboxRepository
            .findNextBatchForProcessing();
        for (OutboxEntity entry : entries) {
            try {
                dispatch(entry);
                entry.setProcessed(true);
                entry.setProcessedAt(LocalDateTime.now());
                outboxRepository.save(entry);
             } catch (Exception e) {
            entry.setAttempts(entry.getAttempts() + 1);
            if (entry.getAttempts() >= maxAttempts) {
                log.error("Outbox entry id={} type={} exceeded max attempts ({}), giving up",
                          entry.getId(), entry.getEventType(), maxAttempts, e);
                try {
                    handleRecovery(entry);
                } catch (JsonProcessingException ex) {
                    log.error("Failed to handle recovery for entry id={}", entry.getId(), ex);
                }
                entry.setProcessed(true);
                entry.setProcessedAt(LocalDateTime.now());
            }
            outboxRepository.save(entry);
            }
        }
    }
    
    private void dispatch(OutboxEntity entry) {
        try {
            switch (entry.getEventType()) {
            case "WALLET_DEBITED" -> {
                var event = objectMapper.readValue(entry.getPayload(), WalletDebitedEvent.class);
                kafkaEventProducer.publishWalletDebited(event);
            }
            case "WALLET_CREDITED" -> {
                var event = objectMapper.readValue(entry.getPayload(), WalletCreditedEvent.class);
                kafkaEventProducer.publishWalletCredited(event);
            }
            case "TRANSFER_CREATED" -> {
                var event = objectMapper.readValue(entry.getPayload(), TransferCreatedEvent.class);
                kafkaEventProducer.publishTransferCreated(event);
            }
            default -> log.warn("Unknown outbox event type: {}", entry.getEventType());
        }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse outbox payload", e);
        }
    }
    
    private void handleRecovery(OutboxEntity entry) throws JsonMappingException, JsonProcessingException {
        switch (entry.getEventType()) {
            case "TRANSFER_CREATED" -> {
                // Transfer was saved as PENDING but event never reached Kafka
                // Mark it FAILED so it doesn't stay stuck forever
                var event = objectMapper.readValue(entry.getPayload(), TransferCreatedEvent.class);
                transferStatusUpdateService.execute(event.transferId(), TransferStatus.FAILED);
            }
            case "WALLET_DEBITED", "WALLET_CREDITED" -> {
                // Debit/credit already committed but downstream wasn't notified
                // Manual reconciliation required
                log.error("CRITICAL: Wallet event for transferId={} never published after {} attempts. " +
                        "Manual reconciliation required.", entry.getAggregateId(), maxAttempts);
            }
            default -> log.warn("Unknown event type {} in recovery", entry.getEventType());
        }
    }
    @Scheduled(fixedDelayString = "${outbox.cleanup-interval:60000}")
    @Transactional
    public void cleanupProcessedEntries() {
        int deleted = outboxRepository.deleteProcessedOlderThan(
            LocalDateTime.now().minusDays(retentionDays)
        );
        if (deleted > 0) {
            log.info("Cleaned up {} processed outbox entries", deleted);
        }
    }
}
