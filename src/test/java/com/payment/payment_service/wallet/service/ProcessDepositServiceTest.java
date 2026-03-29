package com.payment.payment_service.wallet.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.payment_service.shared.entity.OutboxEntity;
import com.payment.payment_service.shared.repository.OutboxRepository;
import com.payment.payment_service.wallet.entity.DepositEntity;
import com.payment.payment_service.wallet.entity.WalletEntity;
import com.payment.payment_service.wallet.exception.WalletNotFoundException;
import com.payment.payment_service.wallet.repository.DepositRepository;
import com.payment.payment_service.wallet.repository.WalletRepository;
import com.payment.payment_service.wallet.type.DepositStatus;
import com.payment.payment_service.wallet.type.PaymentProviderName;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ProcessDepositServiceTest {

    @Mock
    private DepositRepository depositRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ProcessDepositService processDepositService;

    private final UUID walletId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final BigDecimal amount = new BigDecimal("100.00");
    private final String paymentIntentId = "pi_test_123";

    @BeforeEach
    void setUp() {
        reset(depositRepository, walletRepository, outboxRepository, objectMapper);
    }

    @Test
    void execute_WithSuccessStatus_ShouldCreditWalletAndUpdateDepositAndSaveOutbox() throws Exception {
        // Arrange
        DepositEntity deposit = createPendingDeposit();
        WalletEntity wallet = createWallet();

        when(depositRepository.findByPaymentProviderAndExternalPaymentReference(
            PaymentProviderName.STRIPE, paymentIntentId)).thenReturn(Optional.of(deposit));
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // Act
        processDepositService.execute(paymentIntentId, "STRIPE", DepositStatus.SUCCESS);

        // Assert
        assertEquals(new BigDecimal("200.00"), wallet.getBalance());
        assertEquals(DepositStatus.SUCCESS, deposit.getStatus());
        verify(walletRepository).save(wallet);
        verify(depositRepository).save(deposit);
        verify(outboxRepository).save(any(OutboxEntity.class));
    }

    @Test
    void execute_WithFailedStatus_ShouldUpdateDepositWithoutCreditingWallet() {
        // Arrange
        DepositEntity deposit = createPendingDeposit();

        when(depositRepository.findByPaymentProviderAndExternalPaymentReference(
            PaymentProviderName.STRIPE, paymentIntentId)).thenReturn(Optional.of(deposit));

        // Act
        processDepositService.execute(paymentIntentId, "STRIPE", DepositStatus.FAILED);

        // Assert
        assertEquals(DepositStatus.FAILED, deposit.getStatus());
        verify(walletRepository, never()).findByIdForUpdate(any());
        verify(walletRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void execute_WithCanceledStatus_ShouldUpdateDepositWithoutCreditingWallet() {
        // Arrange
        DepositEntity deposit = createPendingDeposit();

        when(depositRepository.findByPaymentProviderAndExternalPaymentReference(
            PaymentProviderName.STRIPE, paymentIntentId)).thenReturn(Optional.of(deposit));

        // Act
        processDepositService.execute(paymentIntentId, "STRIPE", DepositStatus.CANCELED);

        // Assert
        assertEquals(DepositStatus.CANCELED, deposit.getStatus());
        verify(walletRepository, never()).findByIdForUpdate(any());
        verify(walletRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void execute_WithAlreadyFinalizedDeposit_ShouldSkipProcessing() {
        // Arrange
        DepositEntity deposit = new DepositEntity();
        deposit.setStatus(DepositStatus.SUCCESS);

        when(depositRepository.findByPaymentProviderAndExternalPaymentReference(
            PaymentProviderName.STRIPE, paymentIntentId)).thenReturn(Optional.of(deposit));

        // Act
        processDepositService.execute(paymentIntentId, "STRIPE", DepositStatus.FAILED);

        // Assert
        verify(walletRepository, never()).findByIdForUpdate(any());
        verify(walletRepository, never()).save(any());
        verify(depositRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void execute_WithNonexistentDeposit_ShouldThrowRuntimeException() {
        // Arrange
        when(depositRepository.findByPaymentProviderAndExternalPaymentReference(
            PaymentProviderName.STRIPE, paymentIntentId)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            processDepositService.execute(paymentIntentId, "STRIPE", DepositStatus.SUCCESS));

        assertTrue(exception.getMessage().contains(paymentIntentId));
    }

    @Test
    void execute_WithInvalidProvider_ShouldThrowRuntimeException() {
        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            processDepositService.execute(paymentIntentId, "INVALID", DepositStatus.SUCCESS));

        assertTrue(exception.getMessage().contains("Invalid payment provider"));
    }

    @Test
    void execute_WithNonexistentWallet_ShouldThrowWalletNotFoundException() {
        // Arrange
        DepositEntity deposit = createPendingDeposit();

        when(depositRepository.findByPaymentProviderAndExternalPaymentReference(
            PaymentProviderName.STRIPE, paymentIntentId)).thenReturn(Optional.of(deposit));
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(WalletNotFoundException.class, () ->
            processDepositService.execute(paymentIntentId, "STRIPE", DepositStatus.SUCCESS));
    }

    private DepositEntity createPendingDeposit() {
        DepositEntity deposit = new DepositEntity();
        deposit.setId(UUID.randomUUID());
        deposit.setUserId(userId);
        deposit.setWalletId(walletId);
        deposit.setExternalPaymentReference(paymentIntentId);
        deposit.setAmount(amount);
        deposit.setStatus(DepositStatus.PENDING);
        deposit.setPaymentProvider(PaymentProviderName.STRIPE);
        return deposit;
    }

    private WalletEntity createWallet() {
        WalletEntity wallet = new WalletEntity();
        wallet.setId(walletId);
        wallet.setUserId(userId);
        wallet.setBalance(new BigDecimal("100.00"));
        return wallet;
    }
}
