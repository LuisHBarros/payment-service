package com.payment.payment_service.transaction.repository;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.payment.payment_service.transaction.entity.TransactionEntity;
import com.payment.payment_service.transaction.type.TransactionType;


public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    boolean existsByWalletIdAndTransferIdAndType(UUID walletId, UUID transferId, TransactionType type);

    Page<TransactionEntity> findByWalletId(UUID walletId, Pageable pageable);

    Page<TransactionEntity> findByWalletIdAndType(UUID walletId, TransactionType type, Pageable pageable);

    Page<TransactionEntity> findByWalletIdAndCreatedAtBetween(UUID walletId, LocalDateTime start, LocalDateTime end, Pageable pageable);

    Page<TransactionEntity> findByWalletIdAndTypeAndCreatedAtBetween(UUID walletId, TransactionType type, LocalDateTime start, LocalDateTime end, Pageable pageable);

    Page<TransactionEntity> findByTransferId(UUID transferId, Pageable pageable);
}
