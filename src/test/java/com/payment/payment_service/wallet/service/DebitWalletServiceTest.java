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

import com.payment.payment_service.wallet.entity.WalletEntity;
import com.payment.payment_service.wallet.exception.InsufficientBalanceException;
import com.payment.payment_service.wallet.exception.WalletNotFoundException;
import com.payment.payment_service.wallet.repository.WalletRepository;

@ExtendWith(MockitoExtension.class)
class DebitWalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private DebitWalletService debitWalletService;

    private final UUID walletId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reset(walletRepository);
    }

    @Test
    void execute_WithSufficientBalance_ShouldDebitWallet() {
        // Arrange
        WalletEntity wallet = new WalletEntity();
        wallet.setId(walletId);
        wallet.setUserId(UUID.randomUUID());
        wallet.setBalance(BigDecimal.valueOf(100.0));

        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(WalletEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        debitWalletService.execute(walletId, BigDecimal.valueOf(50.0));

        // Assert
        verify(walletRepository).save(argThat(savedWallet ->
            savedWallet.getBalance().compareTo(BigDecimal.valueOf(50.0)) == 0
        ));
    }

    @Test
    void execute_WithExactBalance_ShouldDebitAndZeroOut() {
        // Arrange
        WalletEntity wallet = new WalletEntity();
        wallet.setId(walletId);
        wallet.setUserId(UUID.randomUUID());
        wallet.setBalance(BigDecimal.valueOf(100.0));

        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(WalletEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        debitWalletService.execute(walletId, BigDecimal.valueOf(100.0));

        // Assert
        verify(walletRepository).save(argThat(savedWallet ->
            savedWallet.getBalance().compareTo(BigDecimal.ZERO) == 0
        ));
    }

    @Test
    void execute_WithInsufficientBalance_ShouldThrowInsufficientBalanceException() {
        // Arrange
        WalletEntity wallet = new WalletEntity();
        wallet.setId(walletId);
        wallet.setUserId(UUID.randomUUID());
        wallet.setBalance(BigDecimal.valueOf(50.0));

        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));

        // Act & Assert
        InsufficientBalanceException exception = assertThrows(InsufficientBalanceException.class, () ->
            debitWalletService.execute(walletId, BigDecimal.valueOf(100.0))
        );
        assertEquals("The wallet does not have enough balance", exception.getMessage());
        verify(walletRepository, never()).save(any(WalletEntity.class));
    }

    @Test
    void execute_WithNonExistingWallet_ShouldThrowWalletNotFoundException() {
        // Arrange
        when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

        // Act & Assert
        WalletNotFoundException exception = assertThrows(WalletNotFoundException.class, () ->
            debitWalletService.execute(walletId, BigDecimal.valueOf(50.0))
        );
        assertEquals("Wallet not found", exception.getMessage());
        verify(walletRepository, never()).save(any(WalletEntity.class));
    }

    @Test
    void execute_WithNullWalletId_ShouldThrowNullPointerException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
            debitWalletService.execute(null, BigDecimal.valueOf(50.0))
        );
        verify(walletRepository, never()).findById(any());
        verify(walletRepository, never()).save(any(WalletEntity.class));
    }

    @Test
    void execute_ShouldNotAllowNegativeBalance() {
        // Arrange
        WalletEntity wallet = new WalletEntity();
        wallet.setId(walletId);
        wallet.setUserId(UUID.randomUUID());
        wallet.setBalance(BigDecimal.valueOf(10.0));

        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));

        // Act & Assert
        InsufficientBalanceException exception = assertThrows(InsufficientBalanceException.class, () ->
            debitWalletService.execute(walletId, BigDecimal.valueOf(15.0))
        );
        assertEquals("The wallet does not have enough balance", exception.getMessage());
    }

    @Test
    void execute_ShouldNotModifyWalletWhenExceptionThrown() {
        // Arrange
        WalletEntity wallet = new WalletEntity();
        wallet.setId(walletId);
        wallet.setUserId(UUID.randomUUID());
        wallet.setBalance(BigDecimal.valueOf(10.0));

        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));

        // Act & Assert
        assertThrows(InsufficientBalanceException.class, () ->
            debitWalletService.execute(walletId, BigDecimal.valueOf(20.0))
        );

        // Assert - wallet should not be saved
        verify(walletRepository, never()).save(any(WalletEntity.class));
    }
}
