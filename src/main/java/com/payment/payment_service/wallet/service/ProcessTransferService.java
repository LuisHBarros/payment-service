package com.payment.payment_service.wallet.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.payment_service.shared.entity.OutboxEntity;
import com.payment.payment_service.shared.event.WalletCreditedEvent;
import com.payment.payment_service.shared.event.WalletDebitedEvent;
import com.payment.payment_service.shared.repository.OutboxRepository;
import com.payment.payment_service.wallet.entity.ProcessedTransferEntity;
import com.payment.payment_service.wallet.entity.WalletEntity;
import com.payment.payment_service.wallet.exception.InsufficientBalanceException;
import com.payment.payment_service.wallet.exception.WalletNotFoundException;
import com.payment.payment_service.wallet.repository.ProcessedTransferRepository;
import com.payment.payment_service.wallet.repository.WalletRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessTransferService {

    private final WalletRepository walletRepository;
    private final ProcessedTransferRepository processedTransferRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void execute(UUID transferId, UUID sourceWalletId, UUID destinationWalletId, BigDecimal amount) {
        Objects.requireNonNull(transferId, "transferId");
        Objects.requireNonNull(sourceWalletId, "sourceWalletId");
        Objects.requireNonNull(destinationWalletId, "destinationWalletId");
        Objects.requireNonNull(amount, "amount");

        // Check idempotency - skip if already processed
        if (processedTransferRepository.existsById(transferId)) {
            log.info("Transfer {} already processed, skipping", transferId);
            return;
        }

        log.debug("Processing transfer transferId={} source={} dest={} amount={}",
                 transferId, sourceWalletId, destinationWalletId, amount);

        // Determine lock order: always lock smaller UUID first
        // This ensures a total order across all wallets, preventing circular wait (deadlock)
        UUID firstWalletId, secondWalletId;
        boolean sourceIsFirst = sourceWalletId.compareTo(destinationWalletId) <= 0;

        if (sourceIsFirst) {
            firstWalletId = sourceWalletId;
            secondWalletId = destinationWalletId;
        } else {
            firstWalletId = destinationWalletId;
            secondWalletId = sourceWalletId;
        }

        log.debug("Lock order: first={} second={}", firstWalletId, secondWalletId);

        // Acquire locks in deterministic order
        WalletEntity firstWallet = walletRepository.findByIdForUpdate(firstWalletId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + firstWalletId));
        WalletEntity secondWallet = walletRepository.findByIdForUpdate(secondWalletId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + secondWalletId));

        // Perform debit and credit based on actual transfer direction
        if (sourceIsFirst) {
            // Source wallet is first: debit source, then credit destination
            performDebit(firstWallet, amount, transferId);
            performCredit(secondWallet, amount, transferId);
        } else {
            // Destination wallet is first: credit destination, then debit source
            performCredit(firstWallet, amount, transferId);
            performDebit(secondWallet, amount, transferId);
        }

        // Mark transfer as processed
        var processedTransfer = new ProcessedTransferEntity();
        processedTransfer.setId(transferId);
        processedTransfer.setCreatedAt(LocalDateTime.now());
        processedTransferRepository.save(processedTransfer);

        // Publish events to transaction context
        saveOutbox("WALLET_DEBITED", transferId, new WalletDebitedEvent(sourceWalletId, transferId, amount));
        saveOutbox("WALLET_CREDITED", transferId, new WalletCreditedEvent(destinationWalletId, transferId, amount));

        log.info("Successfully processed transfer transferId={}", transferId);
    }

    private void performDebit(WalletEntity wallet, BigDecimal amount, UUID transferId) {
        log.debug("Debiting wallet={} amount={} transferId={}", wallet.getId(), amount, transferId);

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                String.format("Insufficient balance in wallet %s. Required: %s, Available: %s",
                    wallet.getId(), amount, wallet.getBalance())
            );
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
    }

    private void performCredit(WalletEntity wallet, BigDecimal amount, UUID transferId) {
        log.debug("Crediting wallet={} amount={} transferId={}", wallet.getId(), amount, transferId);
        wallet.setBalance(wallet.getBalance().add(amount));
    }
    
    private void saveOutbox(String eventType, UUID aggregateId, Object event) {
        try {
            OutboxEntity outboxEntity = new OutboxEntity();
            outboxEntity.setAggregateType("wallet");
            outboxEntity.setEventType(eventType);
            outboxEntity.setAggregateId(aggregateId);
            outboxEntity.setPayload(objectMapper.writeValueAsString(event));
            outboxRepository.save(outboxEntity);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event", e);
            throw new RuntimeException("Failed to serialize outbox event", e);
        }
    }
}
