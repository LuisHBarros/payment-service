package com.payment.payment_service.transaction.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.support.SendResult;

import com.payment.payment_service.shared.event.TransferStatusChangedEvent;
import com.payment.payment_service.shared.event.WalletCreditedEvent;
import com.payment.payment_service.shared.event.WalletDebitedEvent;
import com.payment.payment_service.shared.kafka.KafkaEventProducer;
import com.payment.payment_service.shared.tracing.KafkaTracingPropagator;
import com.payment.payment_service.shared.tracing.TracingUtils;
import com.payment.payment_service.shared.type.TransferStatus;
import com.payment.payment_service.transaction.service.CreateTransactionService;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferConsumerTest {

    @Mock
    private CreateTransactionService createTransactionService;

    @Mock
    private KafkaEventProducer kafkaEventProducer;

    @Mock
    private KafkaTracingPropagator tracingPropagator;

    @Mock
    private TracingUtils tracingUtils;

    @Mock
    private Tracer tracer;

    @InjectMocks
    private TransferConsumer transferConsumer;

    @SuppressWarnings("unchecked")
    private void mockProducerSuccess() {
        SendResult<String, Object> sendResult = mock(SendResult.class);
        when(kafkaEventProducer.publishTransferStatusChanged(any(TransferStatusChangedEvent.class)))
            .thenReturn(CompletableFuture.completedFuture(sendResult));
    }

    private void mockTracing() {
        Span mockSpan = mock(Span.class);
        when(tracingPropagator.extractAndCreateSpan(any(), anyString())).thenReturn(mockSpan);
        when(mockSpan.tag(anyString(), anyString())).thenReturn(mockSpan);
        when(mockSpan.event(anyString())).thenReturn(mockSpan);
        when(tracer.withSpan(mockSpan)).thenReturn(mock(Tracer.SpanInScope.class));
        when(tracingUtils.currentTraceId()).thenReturn("test-trace-id");
        org.mockito.Mockito.doNothing().when(tracingUtils).eventCurrentSpan(anyString());
    }

    private <T> ConsumerRecord<String, T> buildRecord(String topic, T event) {
        Headers headers = new RecordHeaders();
        return new ConsumerRecord<>(
            topic, 0, 0L, 0L, TimestampType.CREATE_TIME,
            0L, 0, 0, null, event, headers, null
        );
    }

    @Test
    @DisplayName("should create debit transaction and publish COMPLETED status")
    void shouldCreateDebitAndPublishCompleted() {
        UUID walletId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("50.00");
        WalletDebitedEvent event = new WalletDebitedEvent(walletId, transferId, amount);
        ConsumerRecord<String, WalletDebitedEvent> record = buildRecord("payment.wallet.debits", event);

        mockProducerSuccess();
        mockTracing();

        transferConsumer.consumeDebit(record);

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
        ConsumerRecord<String, WalletCreditedEvent> record = buildRecord("payment.wallet.credits", event);

        mockProducerSuccess();
        mockTracing();

        transferConsumer.consumeCredit(record);

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
        ConsumerRecord<String, WalletDebitedEvent> record = buildRecord("payment.wallet.debits", event);

        doThrow(new RuntimeException("debit failed"))
            .when(createTransactionService).executeDebit(walletId, transferId, amount);

        mockProducerSuccess();
        mockTracing();

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
            transferConsumer.consumeDebit(record)
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
        ConsumerRecord<String, WalletCreditedEvent> record = buildRecord("payment.wallet.credits", event);

        doThrow(new RuntimeException("credit failed"))
            .when(createTransactionService).executeCredit(walletId, transferId, amount);

        mockProducerSuccess();
        mockTracing();

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
            transferConsumer.consumeCredit(record)
        );

        verify(kafkaEventProducer).publishTransferStatusChanged(any(TransferStatusChangedEvent.class));
    }
}
