package com.payment.payment_service.transfer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.payment.payment_service.transfer.entity.TransferEntity;
import com.payment.payment_service.transfer.exception.TransferNotFoundException;
import com.payment.payment_service.transfer.repository.TransferRepository;

import java.util.List;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class GetTransferServiceTest {

    @Mock
    private TransferRepository transferRepository;

    private GetTransferService getTransferService;

    @BeforeEach
    void setUp() {
        getTransferService = new GetTransferService(transferRepository);
    }

    @Test
    void execute_shouldReturnTransfer_whenTransferExists() {
        // Arrange
        UUID transferId = UUID.randomUUID();
        TransferEntity transfer = new TransferEntity();
        transfer.setId(transferId);
        when(transferRepository.findById(transferId)).thenReturn(Optional.of(transfer));

        // Act
        TransferEntity result = getTransferService.execute(transferId);

        // Assert
        assertNotNull(result);
        assertEquals(transferId, result.getId());
        verify(transferRepository).findById(transferId);
    }

    @Test
    void execute_shouldThrowException_whenTransferNotFound() {
        // Arrange
        UUID transferId = UUID.randomUUID();
        when(transferRepository.findById(transferId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(TransferNotFoundException.class, () -> getTransferService.execute(transferId));
        verify(transferRepository).findById(transferId);
    }

    @Test
    void execute_shouldThrowException_whenIdIsNull() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> getTransferService.execute(null));
    }

    @Test
    void findByWalletId_shouldReturnPageOfTransfers() {
        // Arrange
        UUID walletId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        TransferEntity transfer = new TransferEntity();
        transfer.setId(UUID.randomUUID());
        Page<TransferEntity> expectedPage = new PageImpl<>(List.of(transfer));
        
        when(transferRepository.findBySourceWalletIdOrDestinationWalletId(walletId, walletId, pageable))
            .thenReturn(expectedPage);

        // Act
        Page<TransferEntity> result = getTransferService.findByWalletId(walletId, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(transferRepository).findBySourceWalletIdOrDestinationWalletId(walletId, walletId, pageable);
    }

    @Test
    void findByWalletId_shouldThrowException_whenWalletIdIsNull() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);

        // Act & Assert
        assertThrows(NullPointerException.class, () -> getTransferService.findByWalletId(null, pageable));
    }
}