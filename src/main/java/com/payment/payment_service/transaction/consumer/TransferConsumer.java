package com.payment.payment_service.transaction.consumer;

import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.payment.payment_service.shared.event.TransferStatusChangedEvent;
import com.payment.payment_service.shared.event.WalletCreditedEvent;
import com.payment.payment_service.shared.event.WalletDebitedEvent;
import com.payment.payment_service.shared.kafka.KafkaEventProducer;
import com.payment.payment_service.shared.type.TransferStatus;
import com.payment.payment_service.transaction.service.CreateTransactionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferConsumer {

    private final CreateTransactionService createTransactionService;
    private final KafkaEventProducer kafkaEventProducer;

    @KafkaListener(
        topics = "${kafka.topics.wallets}",
        groupId = "payment-service-transaction",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(Object event) {
        try {
            if (event instanceof WalletDebitedEvent e) {
                log.info("Received WalletDebitedEvent walletId={} transferId={}", e.walletId(), e.transferId());
                createTransactionService.executeDebit(e.walletId(), e.transferId(), e.amount());
                publishTransferCompletion(e.transferId());
            } else if (event instanceof WalletCreditedEvent e) {
                log.info("Received WalletCreditedEvent walletId={} transferId={}", e.walletId(), e.transferId());
                createTransactionService.executeCredit(e.walletId(), e.transferId(), e.amount());
                publishTransferCompletion(e.transferId());
            }
        } catch (Exception ex) {
            log.error("Error processing wallet event for transferId={}",
                     event instanceof WalletDebitedEvent ? ((WalletDebitedEvent) event).transferId() :
                     event instanceof WalletCreditedEvent ? ((WalletCreditedEvent) event).transferId() : "unknown", ex);
            // Publicar FAILED status se a criação de transação falhar
            if (event instanceof WalletDebitedEvent e) {
                publishTransferFailure(e.transferId());
            } else if (event instanceof WalletCreditedEvent e) {
                publishTransferFailure(e.transferId());
            }
            throw ex;
        }
    }

    private void publishTransferCompletion(UUID transferId) {
        try {
            kafkaEventProducer.publishTransferStatusChanged(
                new TransferStatusChangedEvent(transferId, TransferStatus.COMPLETED)
            );
            log.info("Published TransferStatusChangedEvent(COMPLETED) for transferId={}", transferId);
        } catch (Exception e) {
            log.error("Failed to publish COMPLETED status for transferId={}", transferId, e);
            throw e;
        }
    }

    private void publishTransferFailure(UUID transferId) {
        try {
            kafkaEventProducer.publishTransferStatusChanged(
                new TransferStatusChangedEvent(transferId, TransferStatus.FAILED)
            );
            log.info("Published TransferStatusChangedEvent(FAILED) for transferId={}", transferId);
        } catch (Exception e) {
            log.error("Failed to publish FAILED status for transferId={}", transferId, e);
        }
    }
}