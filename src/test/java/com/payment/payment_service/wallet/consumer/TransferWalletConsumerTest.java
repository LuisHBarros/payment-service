package com.payment.payment_service.wallet.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Supplier;

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

import com.payment.payment_service.shared.event.TransferStatusChangedEvent;
import com.payment.payment_service.shared.kafka.KafkaEventProducer;
import com.payment.payment_service.shared.tracing.KafkaTracingPropagator;
import com.payment.payment_service.shared.tracing.TracingUtils;
import com.payment.payment_service.transfer.event.TransferCreatedEvent;
import com.payment.payment_service.wallet.service.ProcessTransferService;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferWalletConsumerTest {

    @Mock
    private ProcessTransferService processTransferService;

    @Mock
    private KafkaEventProducer kafkaEventProducer;

    @Mock
    private KafkaTracingPropagator tracingPropagator;

    @Mock
    private TracingUtils tracingUtils;

    @Mock
    private Tracer tracer;

    @InjectMocks
    private TransferWalletConsumer transferWalletConsumer;

    private TransferCreatedEvent buildEvent() {
        return new TransferCreatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00")
        );
    }

    private ConsumerRecord<String, TransferCreatedEvent> buildRecord(TransferCreatedEvent event) {
        Headers headers = new RecordHeaders();
        return new ConsumerRecord<>(
            "transfer-created", 0, 0L, 0L, TimestampType.CREATE_TIME,
            0L, 0, 0, null, event, headers, null
        );
    }

    private void mockTracing() {
        Span mockSpan = mock(Span.class);
        when(tracingPropagator.extractAndCreateSpan(any(), anyString())).thenReturn(mockSpan);
        when(mockSpan.tag(anyString(), anyString())).thenReturn(mockSpan);
        when(mockSpan.event(anyString())).thenReturn(mockSpan);
        when(tracer.withSpan(mockSpan)).thenReturn(mock(Tracer.SpanInScope.class));
        when(tracingUtils.currentTraceId()).thenReturn("test-trace-id");
        // Mock withSpan to execute the Supplier directly
        doAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            supplier.get();
            return null;
        }).when(tracingUtils).withSpan(anyString(), any(Supplier.class));
    }

    @Test
    @DisplayName("should process transfer successfully on valid event")
    void shouldProcessTransfer_onSuccess() {
        TransferCreatedEvent event = buildEvent();
        ConsumerRecord<String, TransferCreatedEvent> record = buildRecord(event);
        mockTracing();

        transferWalletConsumer.consume(record);

        verify(processTransferService).execute(
            event.transferId(), event.sourceWalletId(), event.destinationWalletId(), event.amount()
        );
        verifyNoInteractions(kafkaEventProducer);
    }

    @Test
    @DisplayName("should publish FAILED status and rethrow on exception")
    void shouldPublishFailed_onException() {
        TransferCreatedEvent event = buildEvent();
        ConsumerRecord<String, TransferCreatedEvent> record = buildRecord(event);
        RuntimeException error = new RuntimeException("insufficient balance");
        mockTracing();

        doThrow(error).when(processTransferService).execute(
            any(), any(), any(), any()
        );

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
            transferWalletConsumer.consume(record)
        );

        verify(kafkaEventProducer).publishTransferStatusChanged(any(TransferStatusChangedEvent.class));
    }

    @Test
    @DisplayName("should rethrow original exception even if publish FAILED status also fails")
    void shouldRethrow_evenIfPublishFailedStatusFails() {
        TransferCreatedEvent event = buildEvent();
        ConsumerRecord<String, TransferCreatedEvent> record = buildRecord(event);
        RuntimeException serviceError = new RuntimeException("db error");
        mockTracing();

        doThrow(serviceError).when(processTransferService).execute(
            any(), any(), any(), any()
        );
        doThrow(new RuntimeException("kafka error")).when(kafkaEventProducer)
            .publishTransferStatusChanged(any(TransferStatusChangedEvent.class));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
            transferWalletConsumer.consume(record)
        );
    }
}
