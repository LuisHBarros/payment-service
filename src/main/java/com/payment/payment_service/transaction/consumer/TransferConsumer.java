package com.payment.payment_service.transaction.consumer;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
        topics = "${kafka.topics.wallet-debits}",
        groupId = "payment-service-transaction",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeDebit(WalletDebitedEvent event) {
        try {
            log.info("Received WalletDebitedEvent walletId={} transferId={}",
                event.walletId(),
                event.transferId()
            );

            createTransactionService.executeDebit(
                event.walletId(),
                event.transferId(),
                event.amount()
            );

            publishTransferCompletion(event.transferId());

        } catch (Exception ex) {
            log.error(
                "Error processing WalletDebitedEvent transferId={}",
                event.transferId(),
                ex
            );

            publishTransferFailure(event.transferId());
            throw ex;
        }
    }

    @KafkaListener(
        topics = "${kafka.topics.wallet-credits}",
        groupId = "payment-service-transaction",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeCredit(WalletCreditedEvent event) {
        try {
            log.info("Received WalletCreditedEvent walletId={} transferId={}",
                event.walletId(),
                event.transferId()
            );

            createTransactionService.executeCredit(
                event.walletId(),
                event.transferId(),
                event.amount()
            );

            publishTransferCompletion(event.transferId());

        } catch (Exception ex) {
            log.error(
                "Error processing WalletCreditedEvent transferId={}",
                event.transferId(),
                ex
            );

            publishTransferFailure(event.transferId());
            throw ex;
        }
    }

    private void publishTransferCompletion(UUID transferId) {
        try {
            var future = kafkaEventProducer.publishTransferStatusChanged(
                new TransferStatusChangedEvent(transferId, TransferStatus.COMPLETED)
            );
            future.get(10, TimeUnit.SECONDS);

            log.info(
                "Published TransferStatusChangedEvent(COMPLETED) transferId={}",
                transferId
            );

        } catch (Exception ex) {
            log.error(
                "Failed to publish COMPLETED status transferId={}",
                transferId,
                ex
            );
            throw new RuntimeException("Failed to publish transfer completion", ex);
        }
    }

    private void publishTransferFailure(UUID transferId) {
        try {
            var future = kafkaEventProducer.publishTransferStatusChanged(
                new TransferStatusChangedEvent(transferId, TransferStatus.FAILED)
            );
            future.get(10, TimeUnit.SECONDS);

            log.info(
                "Published TransferStatusChangedEvent(FAILED) transferId={}",
                transferId
            );

        } catch (Exception ex) {
            log.error(
                "Failed to publish FAILED status transferId={}",
                transferId,
                ex
            );
        }
    }
}