package com.payment.payment_service.transaction.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.lang.NonNull;

import com.payment.payment_service.transaction.entity.TransactionEntity;
import com.payment.payment_service.transaction.repository.TransactionRepository;
import com.payment.payment_service.transaction.type.TransactionType;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class CreateTransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private CreateTransactionService createTransactionService;

    private final UUID walletId = UUID.randomUUID();
    private final UUID transferId = UUID.randomUUID();
    private final BigDecimal amount = BigDecimal.valueOf(100.0);

    @BeforeEach
    void setUp() {
        reset(transactionRepository);
    }

    @Test
    void executeCredit_WithNewTransaction_ShouldCreateCreditTransaction() {
        // Arrange
        when(transactionRepository.existsByWalletIdAndTransferIdAndType(walletId, transferId, TransactionType.CREDIT))
            .thenReturn(false);
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0, TransactionEntity.class));

        // Act
        createTransactionService.executeCredit(walletId, transferId, amount);

        // Assert
        verify(transactionRepository).save(argThat((@NonNull TransactionEntity transaction) ->
            transaction.getWalletId().equals(walletId) &&
            transaction.getTransferId().equals(transferId) &&
            transaction.getType() == TransactionType.CREDIT &&
            transaction.getAmount().equals(amount)
        ));
    }

    @Test
    void executeDebit_WithNewTransaction_ShouldCreateDebitTransaction() {
        // Arrange
        when(transactionRepository.existsByWalletIdAndTransferIdAndType(walletId, transferId, TransactionType.DEBIT))
            .thenReturn(false);
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0, TransactionEntity.class));

        // Act
        createTransactionService.executeDebit(walletId, transferId, amount);

        // Assert
        verify(transactionRepository).save(argThat((@NonNull TransactionEntity transaction) ->
            transaction.getWalletId().equals(walletId) &&
            transaction.getTransferId().equals(transferId) &&
            transaction.getType() == TransactionType.DEBIT &&
            transaction.getAmount().equals(amount)
        ));
    }

    @Test
    void executeCredit_WithExistingTransaction_ShouldNotCreateDuplicate() {
        // Arrange
        when(transactionRepository.existsByWalletIdAndTransferIdAndType(walletId, transferId, TransactionType.CREDIT))
            .thenReturn(true);

        // Act
        createTransactionService.executeCredit(walletId, transferId, amount);

        // Assert
        verify(transactionRepository, never()).save(any(TransactionEntity.class));
    }

    @Test
    void executeDebit_WithExistingTransaction_ShouldNotCreateDuplicate() {
        // Arrange
        when(transactionRepository.existsByWalletIdAndTransferIdAndType(walletId, transferId, TransactionType.DEBIT))
            .thenReturn(true);

        // Act
        createTransactionService.executeDebit(walletId, transferId, amount);

        // Assert
        verify(transactionRepository, never()).save(any(TransactionEntity.class));
    }

    @Test
    void executeCredit_WithZeroAmount_ShouldCreateTransaction() {
        // Arrange
        when(transactionRepository.existsByWalletIdAndTransferIdAndType(walletId, transferId, TransactionType.CREDIT))
            .thenReturn(false);
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0, TransactionEntity.class));

        // Act
        createTransactionService.executeCredit(walletId, transferId, BigDecimal.ZERO);

        // Assert
        verify(transactionRepository).save(argThat((@NonNull TransactionEntity transaction) ->
            transaction.getWalletId().equals(walletId) &&
            transaction.getTransferId().equals(transferId) &&
            transaction.getType() == TransactionType.CREDIT &&
            transaction.getAmount().compareTo(BigDecimal.ZERO) == 0
        ));
    }

    @Test
    void executeCredit_WithLargeAmount_ShouldCreateTransaction() {
        // Arrange
        BigDecimal largeAmount = BigDecimal.valueOf(1000000.0);
        when(transactionRepository.existsByWalletIdAndTransferIdAndType(walletId, transferId, TransactionType.CREDIT))
            .thenReturn(false);
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0, TransactionEntity.class));

        // Act
        createTransactionService.executeCredit(walletId, transferId, largeAmount);

        // Assert
        verify(transactionRepository).save(argThat((@NonNull TransactionEntity transaction) ->
            transaction.getAmount().equals(largeAmount)
        ));
    }

    @Test
    void executeCredit_WithDifferentTransferIds_ShouldCreateSeparateTransactions() {
        // Arrange
        UUID transferId1 = UUID.randomUUID();
        UUID transferId2 = UUID.randomUUID();

        when(transactionRepository.existsByWalletIdAndTransferIdAndType(any(UUID.class), any(UUID.class), eq(TransactionType.CREDIT)))
            .thenReturn(false);
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0, TransactionEntity.class));

        // Act
        createTransactionService.executeCredit(walletId, transferId1, amount);
        createTransactionService.executeCredit(walletId, transferId2, amount);

        // Assert
        verify(transactionRepository, times(2)).save(any(TransactionEntity.class));
    }

    @Test
    void executeDebit_ShouldSetCorrectTransactionType() {
        // Arrange
        when(transactionRepository.existsByWalletIdAndTransferIdAndType(walletId, transferId, TransactionType.DEBIT))
            .thenReturn(false);
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0, TransactionEntity.class));

        // Act
        createTransactionService.executeDebit(walletId, transferId, amount);

        // Assert
        verify(transactionRepository).save(argThat((@NonNull TransactionEntity transaction) ->
            transaction.getType() == TransactionType.DEBIT
        ));
    }

    @Test
    void executeCredit_ShouldSetCorrectTransactionType() {
        // Arrange
        when(transactionRepository.existsByWalletIdAndTransferIdAndType(walletId, transferId, TransactionType.CREDIT))
            .thenReturn(false);
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0, TransactionEntity.class));

        // Act
        createTransactionService.executeCredit(walletId, transferId, amount);

        // Assert
        verify(transactionRepository).save(argThat((@NonNull TransactionEntity transaction) ->
            transaction.getType() == TransactionType.CREDIT
        ));
    }

    @Test
    void executeCredit_WithMultipleCalls_ShouldBeIdempotent() {
        // Arrange
        when(transactionRepository.existsByWalletIdAndTransferIdAndType(walletId, transferId, TransactionType.CREDIT))
            .thenReturn(false, true);
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0, TransactionEntity.class));

        // Act
        createTransactionService.executeCredit(walletId, transferId, amount);
        createTransactionService.executeCredit(walletId, transferId, amount);

        // Assert - should only save once due to idempotency
        verify(transactionRepository, times(1)).save(any(TransactionEntity.class));
    }

    @Test
    void executeDebit_WithMultipleCalls_ShouldBeIdempotent() {
        // Arrange
        when(transactionRepository.existsByWalletIdAndTransferIdAndType(walletId, transferId, TransactionType.DEBIT))
            .thenReturn(false, true);
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0, TransactionEntity.class));

        // Act
        createTransactionService.executeDebit(walletId, transferId, amount);
        createTransactionService.executeDebit(walletId, transferId, amount);

        // Assert - should only save once due to idempotency
        verify(transactionRepository, times(1)).save(any(TransactionEntity.class));
    }
}
