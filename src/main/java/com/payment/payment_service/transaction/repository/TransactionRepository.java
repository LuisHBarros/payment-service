package com.payment.payment_service.transaction.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payment.payment_service.transaction.entity.TransactionEntity;
import com.payment.payment_service.transaction.type.TransactionType;


public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    boolean existsByWalletIdAndTransferIdAndType(UUID walletId, UUID transferId, TransactionType type);
}
