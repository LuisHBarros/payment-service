package com.payment.payment_service.transaction.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.payment.payment_service.transaction.entity.TransactionEntity;
import com.payment.payment_service.transaction.exception.TransactionNotFoundException;
import com.payment.payment_service.transaction.repository.TransactionRepository;
import com.payment.payment_service.transaction.type.TransactionType;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetTransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionEntity findById(@NonNull UUID id) {
        return transactionRepository.findById(id)
            .orElseThrow(() -> new TransactionNotFoundException("transaction not found"));
    }

    public Page<TransactionEntity> findByWalletId(@NonNull UUID walletId, Pageable pageable,
            TransactionType type, LocalDateTime startDate, LocalDateTime endDate) {
        if (type != null && startDate != null && endDate != null) {
            return transactionRepository.findByWalletIdAndTypeAndCreatedAtBetween(
                walletId, type, startDate, endDate, pageable);
        }
        if (type != null) {
            return transactionRepository.findByWalletIdAndType(walletId, type, pageable);
        }
        if (startDate != null && endDate != null) {
            return transactionRepository.findByWalletIdAndCreatedAtBetween(
                walletId, startDate, endDate, pageable);
        }
        return transactionRepository.findByWalletId(walletId, pageable);
    }

    public Page<TransactionEntity> findByTransferId(@NonNull UUID transferId, Pageable pageable) {
        return transactionRepository.findByTransferId(transferId, pageable);
    }
}
