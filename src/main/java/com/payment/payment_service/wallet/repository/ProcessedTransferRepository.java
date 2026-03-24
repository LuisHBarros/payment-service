package com.payment.payment_service.wallet.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payment.payment_service.wallet.entity.ProcessedTransferEntity;

public interface ProcessedTransferRepository extends JpaRepository<ProcessedTransferEntity, UUID> {
}
