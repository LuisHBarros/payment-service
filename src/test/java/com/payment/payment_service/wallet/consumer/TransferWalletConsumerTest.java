package com.payment.payment_service.wallet.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.payment.payment_service.shared.event.TransferStatusChangedEvent;
import com.payment.payment_service.shared.kafka.KafkaEventProducer;
import com.payment.payment_service.shared.type.TransferStatus;
import com.payment.payment_service.transfer.event.TransferCreatedEvent;
import com.payment.payment_service.wallet.service.ProcessTransferService;

@ExtendWith(MockitoExtension.class)
class TransferWalletConsumerTest {

    @Mock
    private ProcessTransferService processTransferService;

    @Mock
    private KafkaEventProducer kafkaEventProducer;

    @InjectMocks
    private TransferWalletConsumer transferWalletConsumer;

    private TransferCreatedEvent buildEvent() {
        return new TransferCreatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00")
        );
    }

    @Test
    @DisplayName("should process transfer successfully on valid event")
    void shouldProcessTransfer_onSuccess() {
        TransferCreatedEvent event = buildEvent();

        transferWalletConsumer.consume(event);

        verify(processTransferService).execute(
            event.transferId(), event.sourceWalletId(), event.destinationWalletId(), event.amount()
        );
        verifyNoInteractions(kafkaEventProducer);
    }

    @Test
    @DisplayName("should publish FAILED status and rethrow on exception")
    void shouldPublishFailed_onException() {
        TransferCreatedEvent event = buildEvent();
        RuntimeException error = new RuntimeException("insufficient balance");

        doThrow(error).when(processTransferService).execute(
            any(), any(), any(), any()
        );

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
            transferWalletConsumer.consume(event)
        );

        verify(kafkaEventProducer).publishTransferStatusChanged(any(TransferStatusChangedEvent.class));
    }

    @Test
    @DisplayName("should rethrow original exception even if publish FAILED status also fails")
    void shouldRethrow_evenIfPublishFailedStatusFails() {
        TransferCreatedEvent event = buildEvent();
        RuntimeException serviceError = new RuntimeException("db error");

        doThrow(serviceError).when(processTransferService).execute(
            any(), any(), any(), any()
        );
        doThrow(new RuntimeException("kafka error")).when(kafkaEventProducer)
            .publishTransferStatusChanged(any(TransferStatusChangedEvent.class));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
            transferWalletConsumer.consume(event)
        );
    }
}
