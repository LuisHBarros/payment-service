package com.payment.payment_service.wallet.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payment.payment_service.wallet.entity.WalletEntity;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<WalletEntity, UUID> {
    
    Optional<WalletEntity> findByUserId(UUID userId);
}
