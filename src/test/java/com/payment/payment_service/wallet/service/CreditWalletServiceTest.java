package com.payment.payment_service.wallet.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
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
class CreditWalletServiceTest {

    @Mock
    private GetWalletService getWalletService;

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private CreditWalletService creditWalletService;

    private final UUID walletId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reset(getWalletService, walletRepository);
    }

    @Test
    void execute_WithValidAmount_ShouldCreditWallet() {
        // Arrange
        WalletEntity wallet = new WalletEntity();
        wallet.setId(walletId);
        wallet.setUserId(UUID.randomUUID());
        wallet.setBalance(BigDecimal.valueOf(100.0));

        when(getWalletService.execute(walletId)).thenReturn(wallet);
        when(walletRepository.save(any(WalletEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        creditWalletService.execute(walletId, BigDecimal.valueOf(50.0));

        // Assert
        verify(walletRepository).save(argThat(savedWallet ->
            savedWallet.getBalance().compareTo(BigDecimal.valueOf(150.0)) == 0
        ));
    }

    @Test
    void execute_WithZeroAmount_ShouldMaintainBalance() {
        // Arrange
        WalletEntity wallet = new WalletEntity();
        wallet.setId(walletId);
        wallet.setUserId(UUID.randomUUID());
        wallet.setBalance(BigDecimal.valueOf(100.0));

        when(getWalletService.execute(walletId)).thenReturn(wallet);
        when(walletRepository.save(any(WalletEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        creditWalletService.execute(walletId, BigDecimal.ZERO);

        // Assert
        verify(walletRepository).save(argThat(savedWallet ->
            savedWallet.getBalance().compareTo(BigDecimal.valueOf(100.0)) == 0
        ));
    }

    @Test
    void execute_WithLargeAmount_ShouldCreditCorrectly() {
        // Arrange
        WalletEntity wallet = new WalletEntity();
        wallet.setId(walletId);
        wallet.setUserId(UUID.randomUUID());
        wallet.setBalance(BigDecimal.valueOf(1000000.0));

        when(getWalletService.execute(walletId)).thenReturn(wallet);
        when(walletRepository.save(any(WalletEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        creditWalletService.execute(walletId, BigDecimal.valueOf(500000.0));

        // Assert
        verify(walletRepository).save(argThat(savedWallet ->
            savedWallet.getBalance().compareTo(BigDecimal.valueOf(1500000.0)) == 0
        ));
    }

    @Test
    void execute_ShouldCallGetWalletService() {
        // Arrange
        WalletEntity wallet = new WalletEntity();
        wallet.setId(walletId);
        wallet.setUserId(UUID.randomUUID());
        wallet.setBalance(BigDecimal.valueOf(100.0));

        when(getWalletService.execute(walletId)).thenReturn(wallet);
        when(walletRepository.save(any(WalletEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        creditWalletService.execute(walletId, BigDecimal.valueOf(25.0));

        // Assert
        verify(getWalletService).execute(walletId);
    }
}
