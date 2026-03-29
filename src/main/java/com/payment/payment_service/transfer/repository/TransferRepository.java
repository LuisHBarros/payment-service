package com.payment.payment_service.transfer.repository;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.payment.payment_service.transfer.entity.TransferEntity;

public interface TransferRepository extends JpaRepository<TransferEntity, UUID>, JpaSpecificationExecutor<TransferEntity> {

    Page<TransferEntity> findBySourceWalletIdOrDestinationWalletId(
        UUID sourceWalletId,
        UUID destinationWalletId,
        Pageable pageable
    );
    
}
