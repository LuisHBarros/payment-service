package com.payment.payment_service.wallet.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.payment.payment_service.wallet.entity.WalletEntity;
import com.payment.payment_service.wallet.exception.WalletAlreadyExistsException;
import com.payment.payment_service.wallet.repository.WalletRepository;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class CreateWalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private CreateWalletService createWalletService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reset(walletRepository);
    }

    @Test
    void execute_WithNonExistingUser_ShouldCreateWallet() {
        // Arrange
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(walletRepository.save(any(WalletEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        createWalletService.execute(userId);

        // Assert
        verify(walletRepository).save(argThat(wallet -> {
            assertNotNull(wallet);
            assertEquals(userId, wallet.getUserId());
            assertEquals(java.math.BigDecimal.ZERO, wallet.getBalance());
            return true;
        }));
    }

    @Test
    void execute_WithExistingWallet_ShouldThrowWalletAlreadyExistsException() {
        // Arrange
        WalletEntity existingWallet = new WalletEntity();
        existingWallet.setUserId(userId);
        existingWallet.setBalance(java.math.BigDecimal.valueOf(100.0));

        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(existingWallet));

        // Act & Assert
        WalletAlreadyExistsException exception = assertThrows(WalletAlreadyExistsException.class, () ->
            createWalletService.execute(userId)
        );
        assertEquals("Wallet already exists for user", exception.getMessage());
        verify(walletRepository, never()).save(any(WalletEntity.class));
    }

    @Test
    void execute_ShouldInitializeBalanceToZero() {
        // Arrange
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(walletRepository.save(any(WalletEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        createWalletService.execute(userId);

        // Assert
        verify(walletRepository).save(argThat(wallet ->
            wallet.getBalance() != null && wallet.getBalance().compareTo(java.math.BigDecimal.ZERO) == 0
        ));
    }
}
