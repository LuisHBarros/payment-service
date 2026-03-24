package com.payment.payment_service.wallet.consumer;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.payment.payment_service.shared.event.TransferStatusChangedEvent;
import com.payment.payment_service.shared.event.WalletCreditedEvent;
import com.payment.payment_service.shared.event.WalletDebitedEvent;
import com.payment.payment_service.shared.kafka.KafkaEventProducer;
import com.payment.payment_service.shared.type.TransferStatus;
import com.payment.payment_service.transfer.event.TransferCreatedEvent;
import com.payment.payment_service.wallet.entity.ProcessedTransferEntity;
import com.payment.payment_service.wallet.repository.ProcessedTransferRepository;
import com.payment.payment_service.wallet.service.CreditWalletService;
import com.payment.payment_service.wallet.service.DebitWalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferWalletConsumer {

    private final DebitWalletService debitWalletService;
    private final CreditWalletService creditWalletService;
    private final KafkaEventProducer kafkaEventProducer;
    private final ProcessedTransferRepository processedTransferRepository;

    @KafkaListener(
        topics = "${kafka.topics.transfers}",
        groupId = "payment-service-wallet",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(Object event) {
        try {
            if (event instanceof TransferCreatedEvent e) {
                log.info("Received TransferCreatedEvent transferId={} sourceWallet={} destWallet={}",
                         e.transferId(), e.sourceWalletId(), e.destinationWalletId());
                processTransfer(e);
            }
        } catch (Exception ex) {
            log.error("Error processing TransferCreatedEvent for transferId={}", event, ex);
            throw ex; // Deixa Kafka retry (3 tentativas antes do DLT)
        }
    }

    @Transactional
    private void processTransfer(TransferCreatedEvent event) {
        UUID transferId = event.transferId();

        // Verificar se transferência já foi processada (idempotência)
        if (processedTransferRepository.existsById(transferId)) {
            log.info("Transfer {} already processed, skipping", transferId);
            return;
        }

        try {
            // Processar debit e credit
            debitWalletService.execute(event.sourceWalletId(), event.amount());
            creditWalletService.execute(event.destinationWalletId(), event.amount());

            // Marcar como processado
            var processedTransfer = new ProcessedTransferEntity();
            processedTransfer.setTransferId(transferId);
            processedTransfer.setProcessedAt(LocalDateTime.now());
            processedTransferRepository.save(processedTransfer);

            // Publicar eventos do contexto Wallet para o contexto Transaction
            kafkaEventProducer.publishWalletDebited(
                new WalletDebitedEvent(event.sourceWalletId(), transferId, event.amount())
            );

            kafkaEventProducer.publishWalletCredited(
                new WalletCreditedEvent(event.destinationWalletId(), transferId, event.amount())
            );

            log.info("Successfully processed transferId={}", transferId);
        } catch (Exception e) {
            log.error("Failed to process transferId={}", transferId, e);

            // Publicar status FAILED em caso de erro
            try {
                kafkaEventProducer.publishTransferStatusChanged(
                    new TransferStatusChangedEvent(transferId, TransferStatus.FAILED)
                );
            } catch (Exception ex) {
                log.error("Failed to publish FAILED status for transferId={}", transferId, ex);
            }

            throw e;
        }
    }
}
