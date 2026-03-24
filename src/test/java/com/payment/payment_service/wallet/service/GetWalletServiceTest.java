package com.payment.payment_service.wallet.service;

import static org.junit.jupiter.api.Assertions.*;
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
import com.payment.payment_service.wallet.repository.WalletRepository;

@ExtendWith(MockitoExtension.class)
class GetWalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private GetWalletService getWalletService;

    private final UUID userId = UUID.randomUUID();
    private final UUID walletId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reset(walletRepository);
    }

    @Test
    void execute_WithExistingUser_ShouldReturnWallet() {
        // Arrange
        WalletEntity expectedWallet = new WalletEntity();
        expectedWallet.setId(walletId);
        expectedWallet.setUserId(userId);
        expectedWallet.setBalance(BigDecimal.valueOf(100.50));

        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(expectedWallet));

        // Act
        WalletEntity result = getWalletService.execute(userId);

        // Assert
        assertNotNull(result);
        assertEquals(walletId, result.getId());
        assertEquals(userId, result.getUserId());
        assertEquals(BigDecimal.valueOf(100.50), result.getBalance());
    }

    @Test
    void execute_WithNonExistingUser_ShouldThrowRuntimeException() {
        // Arrange
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            getWalletService.execute(userId)
        );
        assertEquals("Wallet not found for user", exception.getMessage());
    }

    @Test
    void execute_ShouldReturnWalletWithCorrectAttributes() {
        // Arrange
        WalletEntity wallet = new WalletEntity();
        wallet.setId(walletId);
        wallet.setUserId(userId);
        wallet.setBalance(BigDecimal.valueOf(250.75));

        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        // Act
        WalletEntity result = getWalletService.execute(userId);

        // Assert
        assertNotNull(result);
        assertEquals(walletId, result.getId());
        assertEquals(userId, result.getUserId());
        assertEquals(BigDecimal.valueOf(250.75), result.getBalance());
    }
}
