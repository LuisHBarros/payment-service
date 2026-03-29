package com.payment.payment_service.transfer.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.payment.payment_service.shared.event.TransferStatusChangedEvent;
import com.payment.payment_service.shared.type.TransferStatus;
import com.payment.payment_service.transfer.entity.TransferEntity;
import com.payment.payment_service.transfer.exception.TransferNotFoundException;
import com.payment.payment_service.transfer.repository.TransferRepository;

@ExtendWith(MockitoExtension.class)
class TransferStatusConsumerTest {

    @Mock
    private TransferRepository transferRepository;

    @InjectMocks
    private TransferStatusConsumer transferStatusConsumer;

    @Test
    @DisplayName("should update status when transfer status differs from event")
    void shouldUpdateStatus_whenDifferent() {
        UUID transferId = UUID.randomUUID();
        TransferEntity transfer = new TransferEntity();
        transfer.setId(transferId);
        transfer.setAmount(new BigDecimal("100.00"));
        transfer.setStatus(TransferStatus.PENDING);
        transfer.setSourceWalletId(UUID.randomUUID());
        transfer.setDestinationWalletId(UUID.randomUUID());

        when(transferRepository.findById(transferId)).thenReturn(Optional.of(transfer));

        TransferStatusChangedEvent event = new TransferStatusChangedEvent(transferId, TransferStatus.COMPLETED);
        transferStatusConsumer.consume(event);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        verify(transferRepository).save(transfer);
    }

    @Test
    @DisplayName("should skip update when status already matches (idempotency)")
    void shouldSkipUpdate_whenStatusAlreadyMatches() {
        UUID transferId = UUID.randomUUID();
        TransferEntity transfer = new TransferEntity();
        transfer.setId(transferId);
        transfer.setAmount(new BigDecimal("100.00"));
        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setSourceWalletId(UUID.randomUUID());
        transfer.setDestinationWalletId(UUID.randomUUID());

        when(transferRepository.findById(transferId)).thenReturn(Optional.of(transfer));

        TransferStatusChangedEvent event = new TransferStatusChangedEvent(transferId, TransferStatus.COMPLETED);
        transferStatusConsumer.consume(event);

        verify(transferRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw TransferNotFoundException when transfer does not exist")
    void shouldThrow_whenTransferNotFound() {
        UUID transferId = UUID.randomUUID();
        when(transferRepository.findById(transferId)).thenReturn(Optional.empty());

        TransferStatusChangedEvent event = new TransferStatusChangedEvent(transferId, TransferStatus.COMPLETED);

        assertThatThrownBy(() -> transferStatusConsumer.consume(event))
            .isInstanceOf(TransferNotFoundException.class);
    }
}
