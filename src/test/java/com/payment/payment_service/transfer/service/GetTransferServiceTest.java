package com.payment.payment_service.transfer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.payment.payment_service.shared.type.TransferStatus;
import com.payment.payment_service.shared.type.TransferType;
import com.payment.payment_service.transfer.dto.TransferFilterDTO;
import com.payment.payment_service.transfer.entity.TransferEntity;
import com.payment.payment_service.transfer.exception.TransferNotFoundException;
import com.payment.payment_service.transfer.repository.TransferRepository;

import java.time.LocalDate;
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
        UUID transferId = UUID.randomUUID();
        TransferEntity transfer = new TransferEntity();
        transfer.setId(transferId);
        when(transferRepository.findById(transferId)).thenReturn(Optional.of(transfer));

        TransferEntity result = getTransferService.execute(transferId);

        assertNotNull(result);
        assertEquals(transferId, result.getId());
        verify(transferRepository).findById(transferId);
    }

    @Test
    void execute_shouldThrowException_whenTransferNotFound() {
        UUID transferId = UUID.randomUUID();
        when(transferRepository.findById(transferId)).thenReturn(Optional.empty());

        assertThrows(TransferNotFoundException.class, () -> getTransferService.execute(transferId));
        verify(transferRepository).findById(transferId);
    }

    @Test
    void execute_shouldThrowException_whenIdIsNull() {
        assertThrows(NullPointerException.class, () -> getTransferService.execute(null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByWalletId_withNoFilters_shouldReturnPageOfTransfers() {
        UUID walletId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        TransferFilterDTO filter = new TransferFilterDTO(null, null, null, null);
        TransferEntity transfer = new TransferEntity();
        transfer.setId(UUID.randomUUID());
        Page<TransferEntity> expectedPage = new PageImpl<>(List.of(transfer));

        when(transferRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(expectedPage);

        Page<TransferEntity> result = getTransferService.findByWalletId(walletId, pageable, filter);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(transferRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByWalletId_withStatusFilter_shouldPassToSpecification() {
        UUID walletId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        TransferFilterDTO filter = new TransferFilterDTO(TransferStatus.COMPLETED, null, null, null);
        TransferEntity transfer = new TransferEntity();
        transfer.setId(UUID.randomUUID());
        Page<TransferEntity> expectedPage = new PageImpl<>(List.of(transfer));

        when(transferRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(expectedPage);

        Page<TransferEntity> result = getTransferService.findByWalletId(walletId, pageable, filter);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(transferRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByWalletId_withDebitTypeFilter_shouldPassToSpecification() {
        UUID walletId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        TransferFilterDTO filter = new TransferFilterDTO(null, null, null, TransferType.DEBIT);
        TransferEntity transfer = new TransferEntity();
        transfer.setId(UUID.randomUUID());
        Page<TransferEntity> expectedPage = new PageImpl<>(List.of(transfer));

        when(transferRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(expectedPage);

        Page<TransferEntity> result = getTransferService.findByWalletId(walletId, pageable, filter);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(transferRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByWalletId_withDateRangeFilter_shouldPassToSpecification() {
        UUID walletId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        TransferFilterDTO filter = new TransferFilterDTO(null, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), null);
        TransferEntity transfer = new TransferEntity();
        transfer.setId(UUID.randomUUID());
        Page<TransferEntity> expectedPage = new PageImpl<>(List.of(transfer));

        when(transferRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(expectedPage);

        Page<TransferEntity> result = getTransferService.findByWalletId(walletId, pageable, filter);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(transferRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByWalletId_withAllFilters_shouldPassToSpecification() {
        UUID walletId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        TransferFilterDTO filter = new TransferFilterDTO(
            TransferStatus.COMPLETED,
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 12, 31),
            TransferType.CREDIT
        );
        TransferEntity transfer = new TransferEntity();
        transfer.setId(UUID.randomUUID());
        Page<TransferEntity> expectedPage = new PageImpl<>(List.of(transfer));

        when(transferRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(expectedPage);

        Page<TransferEntity> result = getTransferService.findByWalletId(walletId, pageable, filter);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(transferRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findByWalletId_shouldThrowException_whenWalletIdIsNull() {
        Pageable pageable = PageRequest.of(0, 10);
        TransferFilterDTO filter = new TransferFilterDTO(null, null, null, null);

        assertThrows(NullPointerException.class, () -> getTransferService.findByWalletId(null, pageable, filter));
    }
}
