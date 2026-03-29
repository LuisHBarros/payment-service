package com.payment.payment_service.wallet.service;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.payment_service.shared.entity.OutboxEntity;
import com.payment.payment_service.shared.event.DepositCompletedEvent;
import com.payment.payment_service.shared.repository.OutboxRepository;
import com.payment.payment_service.wallet.entity.DepositEntity;
import com.payment.payment_service.wallet.entity.WalletEntity;
import com.payment.payment_service.wallet.exception.WalletNotFoundException;
import com.payment.payment_service.wallet.repository.DepositRepository;
import com.payment.payment_service.wallet.repository.WalletRepository;
import com.payment.payment_service.wallet.type.DepositStatus;
import com.payment.payment_service.wallet.type.PaymentProviderName;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessDepositService {
    private final DepositRepository depositRepository;
    private final WalletRepository walletRepository;
    private final OutboxRepository outboxRepository;   // novo
    private final ObjectMapper objectMapper;

    @Transactional
    public void execute(String paymentIntentId, String paymentProvider, DepositStatus newStatus){
        Objects.requireNonNull(paymentIntentId, "Payment intent ID cannot be null");
        Objects.requireNonNull(paymentProvider, "Payment provider cannot be null");
        Objects.requireNonNull(newStatus, "Deposit status cannot be null");

        PaymentProviderName provider;
        try {
            provider = PaymentProviderName.valueOf(paymentProvider.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid payment provider: " + paymentProvider);
        }
        
        var deposit = depositRepository.findByPaymentProviderAndExternalPaymentReference(provider, paymentIntentId)
            .orElseThrow(() -> new RuntimeException("Deposit not found for payment intent ID: " + paymentIntentId));

        if (deposit.getStatus() != DepositStatus.PENDING) {
            log.info("Deposit already finalized, skipping paymentIntentId={}, currentStatus={}",
                paymentIntentId,
                deposit.getStatus());
            return;
        }
        if (newStatus == DepositStatus.SUCCESS) {
            WalletEntity wallet = walletRepository.findByIdForUpdate(Objects.requireNonNull(deposit.getWalletId(), "Wallet ID cannot be null"))
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found for ID: " + deposit.getWalletId()));
            
            wallet.setBalance(wallet.getBalance().add(deposit.getAmount()));
            walletRepository.save(wallet);
            saveOutbox(deposit);
        }
        deposit.setStatus(newStatus);
        depositRepository.save(deposit);


        log.info("Deposit updated, paymentIntentId={}, status={}, amount={}, walletId={}",
            paymentIntentId,
            newStatus,
            deposit.getAmount(),
            deposit.getWalletId());
    }

    private void saveOutbox(DepositEntity deposit) {
        try {
            var event = new DepositCompletedEvent(
                deposit.getId(),
                deposit.getWalletId(),
                deposit.getUserId(),
                deposit.getAmount(),
                deposit.getPaymentProvider().name()
            );
            OutboxEntity outbox = new OutboxEntity();
            outbox.setAggregateType("deposit");
            outbox.setAggregateId(deposit.getId());
            outbox.setEventType("DEPOSIT_COMPLETED");
            outbox.setPayload(objectMapper.writeValueAsString(event));
            outboxRepository.save(outbox);
            
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize deposit outbox event", e);
        }
    }
}
