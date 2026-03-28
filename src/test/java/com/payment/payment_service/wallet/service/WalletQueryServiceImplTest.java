package com.payment.payment_service.wallet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

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
import com.payment.payment_service.wallet.exception.WalletNotFoundException;
import com.payment.payment_service.wallet.repository.WalletRepository;

@ExtendWith(MockitoExtension.class)
class WalletQueryServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private WalletQueryServiceImpl walletQueryService;

    @BeforeEach
    void setUp() {
        reset(walletRepository);
    }

    @Test
    @SuppressWarnings("null")
    void getSummary_shouldReturnWalletSummaryForWalletId() {
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        WalletEntity wallet = new WalletEntity();
        wallet.setId(walletId);
        wallet.setUserId(userId);
        wallet.setBalance(new BigDecimal("100.00"));

        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));

        var summary = walletQueryService.getSummary(walletId);

        assertEquals(walletId, summary.id());
        assertEquals(userId, summary.userId());
        assertEquals(new BigDecimal("100.00"), summary.balance());
    }

    @Test
    @SuppressWarnings("null")
    void getSummary_shouldThrowWhenWalletDoesNotExist() {
        UUID walletId = UUID.randomUUID();

        when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

        WalletNotFoundException exception = assertThrows(
            WalletNotFoundException.class,
            () -> walletQueryService.getSummary(walletId)
        );

        assertEquals("Wallet not found: " + walletId, exception.getMessage());
    }
}
