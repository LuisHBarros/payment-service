package com.payment.payment_service.transaction.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

import com.payment.payment_service.shared.event.TransferStatusChangedEvent;
import com.payment.payment_service.shared.event.WalletCreditedEvent;
import com.payment.payment_service.shared.event.WalletDebitedEvent;
import com.payment.payment_service.shared.kafka.KafkaEventProducer;
import com.payment.payment_service.shared.type.TransferStatus;
import com.payment.payment_service.transaction.service.CreateTransactionService;

@ExtendWith(MockitoExtension.class)
class TransferConsumerTest {

    @Mock
    private CreateTransactionService createTransactionService;

    @Mock
    private KafkaEventProducer kafkaEventProducer;

    @InjectMocks
    private TransferConsumer transferConsumer;

    @SuppressWarnings("unchecked")
    private void mockProducerSuccess() {
        SendResult<String, Object> sendResult = mock(SendResult.class);
        when(kafkaEventProducer.publishTransferStatusChanged(any(TransferStatusChangedEvent.class)))
            .thenReturn(CompletableFuture.completedFuture(sendResult));
    }

    @Test
    @DisplayName("should create debit transaction and publish COMPLETED status")
    void shouldCreateDebitAndPublishCompleted() {
        UUID walletId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("50.00");
        WalletDebitedEvent event = new WalletDebitedEvent(walletId, transferId, amount);

        mockProducerSuccess();

        transferConsumer.consumeDebit(event);

        verify(createTransactionService).executeDebit(walletId, transferId, amount);
        verify(kafkaEventProducer).publishTransferStatusChanged(any(TransferStatusChangedEvent.class));
    }

    @Test
    @DisplayName("should create credit transaction and publish COMPLETED status")
    void shouldCreateCreditAndPublishCompleted() {
        UUID walletId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("50.00");
        WalletCreditedEvent event = new WalletCreditedEvent(walletId, transferId, amount);

        mockProducerSuccess();

        transferConsumer.consumeCredit(event);

        verify(createTransactionService).executeCredit(walletId, transferId, amount);
        verify(kafkaEventProducer).publishTransferStatusChanged(any(TransferStatusChangedEvent.class));
    }

    @Test
    @DisplayName("should publish FAILED status and rethrow on debit failure")
    void shouldPublishFailed_onDebitFailure() {
        UUID walletId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("50.00");
        WalletDebitedEvent event = new WalletDebitedEvent(walletId, transferId, amount);

        doThrow(new RuntimeException("debit failed"))
            .when(createTransactionService).executeDebit(walletId, transferId, amount);

        mockProducerSuccess();

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
            transferConsumer.consumeDebit(event)
        );

        verify(kafkaEventProducer).publishTransferStatusChanged(any(TransferStatusChangedEvent.class));
    }

    @Test
    @DisplayName("should publish FAILED status and rethrow on credit failure")
    void shouldPublishFailed_onCreditFailure() {
        UUID walletId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("50.00");
        WalletCreditedEvent event = new WalletCreditedEvent(walletId, transferId, amount);

        doThrow(new RuntimeException("credit failed"))
            .when(createTransactionService).executeCredit(walletId, transferId, amount);

        mockProducerSuccess();

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
            transferConsumer.consumeCredit(event)
        );

        verify(kafkaEventProducer).publishTransferStatusChanged(any(TransferStatusChangedEvent.class));
    }
}
