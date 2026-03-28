package com.payment.payment_service.transaction.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.payment.payment_service.transaction.type.TransactionType;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.payment.payment_service.transaction.entity.TransactionEntity;
import com.payment.payment_service.transaction.repository.TransactionRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateTransactionService {

    private final TransactionRepository transactionRepository;


    @Transactional
    public void executeCredit(UUID walletId, UUID transferId, BigDecimal amount) {
        save(walletId, transferId, TransactionType.CREDIT, amount);
    }


    @Transactional
    public void executeDebit(UUID walletId, UUID transferId, BigDecimal amount) {
        save(walletId, transferId, TransactionType.DEBIT, amount);
    }

    private void save(UUID walletID, UUID transferID, TransactionType type, BigDecimal amount) {
        // Idempotência: verificar se transação já existe
        boolean transactionExists = transactionRepository
            .existsByWalletIdAndTransferIdAndType(walletID, transferID, type);

        if (transactionExists) {
            log.info("Transaction already exists for walletId={}, transferId={}, type={}, skipping creation",
                     walletID, transferID, type);
            return;
        }

        TransactionEntity transaction = new TransactionEntity();
        transaction.setWalletId(walletID);
        transaction.setTransferId(transferID);
        transaction.setType(type);
        transaction.setAmount(amount);
        transactionRepository.save(transaction);
        log.info("Created transaction for walletId={}, transferId={}, type={}",
                 walletID, transferID, type);
    }
}
