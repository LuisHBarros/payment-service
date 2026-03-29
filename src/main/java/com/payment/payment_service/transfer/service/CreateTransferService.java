package com.payment.payment_service.transfer.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.payment_service.shared.entity.OutboxEntity;
import com.payment.payment_service.shared.metrics.PaymentMetrics;
import com.payment.payment_service.shared.repository.OutboxRepository;
import com.payment.payment_service.shared.type.TransferStatus;
import com.payment.payment_service.transfer.entity.TransferEntity;
import com.payment.payment_service.transfer.event.TransferCreatedEvent;
import com.payment.payment_service.transfer.exception.TransferException;
import com.payment.payment_service.transfer.repository.TransferRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateTransferService {

    private final TransferRepository transferRepository;
    private final TransferAuthorizationService transferAuthorizationService;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final PaymentMetrics metrics;

    @Transactional
    public UUID execute(UUID sourceWalletId, UUID destinationWalletId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransferException("amount must be greater than zero");
        }

        // Autorizar transferência antes de criar
        transferAuthorizationService.authorize(sourceWalletId, destinationWalletId, amount);

        // Criar transferência com status PENDING
        var transfer = new TransferEntity();
        transfer.setSourceWalletId(sourceWalletId);
        transfer.setDestinationWalletId(destinationWalletId);
        transfer.setAmount(amount);
        transfer.setStatus(TransferStatus.PENDING);
        transferRepository.save(transfer);
        
        // Publicar evento após commit da transação
        var event = new TransferCreatedEvent(
            transfer.getId(),
            transfer.getSourceWalletId(),
            transfer.getDestinationWalletId(),
            transfer.getAmount()
        );
        saveOutbox("TRANSFER_CREATED", transfer.getId(), event);
        metrics.recordTransferCreated(amount);
        return transfer.getId();
    }
    
    private void saveOutbox(String eventType, UUID aggregateId, Object event) {
        try {
            var outbox = new OutboxEntity();
            outbox.setAggregateId(aggregateId);
            outbox.setAggregateType("transfer");
            outbox.setEventType(eventType);
            outbox.setPayload(objectMapper.writeValueAsString(event));
            outboxRepository.save(outbox);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize transfer event", e);
            throw new RuntimeException("Failed to serialize transfer event", e);
        }
    }
}