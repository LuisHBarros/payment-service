package com.payment.payment_service.wallet.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import com.payment.payment_service.wallet.dto.CreateDepositRequestDTO;
import com.payment.payment_service.wallet.dto.DepositResponseDTO;
import com.payment.payment_service.wallet.entity.DepositEntity;
import com.payment.payment_service.wallet.entity.WalletEntity;
import com.payment.payment_service.wallet.exception.InvalidPaymentProviderException;
import com.payment.payment_service.wallet.exception.WalletNotFoundException;
import com.payment.payment_service.wallet.provider.PaymentProvider;
import com.payment.payment_service.wallet.provider.PaymentProviderResponse;
import com.payment.payment_service.wallet.repository.DepositRepository;
import com.payment.payment_service.wallet.repository.WalletRepository;
import com.payment.payment_service.wallet.type.DepositStatus;
import com.payment.payment_service.wallet.type.PaymentProviderName;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class CreateDepositServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private DepositRepository depositRepository;

    @Mock
    private Map<String, PaymentProvider> paymentProviders;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private CreateDepositService createDepositService;

    private final UUID userId = UUID.randomUUID();
    private final UUID walletId = UUID.randomUUID();
    private final BigDecimal amount = new BigDecimal("50.00");
    private CreateDepositRequestDTO request;

    @BeforeEach
    void setUp() {
        reset(walletRepository, depositRepository, paymentProviders, transactionTemplate);
        request = new CreateDepositRequestDTO(amount, walletId, PaymentProviderName.STRIPE);
    }

    @Test
    void execute_WithValidRequest_ShouldCreateDepositAndReturnResponse() {
        // Arrange
        WalletEntity wallet = new WalletEntity();
        wallet.setId(walletId);
        wallet.setUserId(userId);
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));

        PaymentProvider mockProvider = mock(PaymentProvider.class);
        when(paymentProviders.get("STRIPE")).thenReturn(mockProvider);
        PaymentProviderResponse providerResponse = new PaymentProviderResponse(
            "cs_test_secret", "pi_test_123", DepositStatus.PENDING, "{}");
        when(mockProvider.createDeposit(amount, userId, walletId)).thenReturn(providerResponse);

        when(depositRepository.save(any(DepositEntity.class))).thenAnswer(inv -> {
            DepositEntity deposit = inv.getArgument(0);
            deposit.setId(UUID.randomUUID());
            return deposit;
        });

        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            var callback = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return callback.doInTransaction(null);
        });

        // Act
        DepositResponseDTO result = createDepositService.execute(userId, request);

        // Assert
        assertNotNull(result);
        assertNotNull(result.depositId());
        assertEquals("cs_test_secret", result.clientSecret());
        assertEquals(DepositStatus.PENDING, result.status());
        assertEquals(amount, result.amount());

        verify(depositRepository).save(argThat(deposit -> {
            assertEquals(userId, deposit.getUserId());
            assertEquals(walletId, deposit.getWalletId());
            assertEquals("pi_test_123", deposit.getExternalPaymentReference());
            assertEquals(amount, deposit.getAmount());
            assertEquals(DepositStatus.PENDING, deposit.getStatus());
            assertEquals(PaymentProviderName.STRIPE, deposit.getPaymentProvider());
            assertEquals("{}", deposit.getProviderResponse());
            return true;
        }));
    }

    @Test
    void execute_WithNonexistentWallet_ShouldThrowWalletNotFoundException() {
        // Arrange
        when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(WalletNotFoundException.class, () ->
            createDepositService.execute(userId, request));

        verify(depositRepository, never()).save(any());
    }

    @Test
    void execute_WithOwnershipMismatch_ShouldThrowWalletNotFoundException() {
        // Arrange
        UUID otherUserId = UUID.randomUUID();
        WalletEntity wallet = new WalletEntity();
        wallet.setId(walletId);
        wallet.setUserId(otherUserId);
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));

        // Act & Assert
        assertThrows(WalletNotFoundException.class, () ->
            createDepositService.execute(userId, request));

        verify(depositRepository, never()).save(any());
    }

    @Test
    void execute_WithUnregisteredProvider_ShouldThrowInvalidPaymentProviderException() {
        // Arrange
        WalletEntity wallet = new WalletEntity();
        wallet.setId(walletId);
        wallet.setUserId(userId);
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(paymentProviders.get("STRIPE")).thenReturn(null);

        // Act & Assert
        assertThrows(InvalidPaymentProviderException.class, () ->
            createDepositService.execute(userId, request));
    }

    @Test
    void execute_WithNullUserId_ShouldThrowNullPointerException() {
        assertThrows(NullPointerException.class, () ->
            createDepositService.execute(null, request));
    }

    @Test
    void execute_WithNullRequest_ShouldThrowNullPointerException() {
        assertThrows(NullPointerException.class, () ->
            createDepositService.execute(userId, null));
    }
}
