package com.payment.payment_service.transfer.repository;

import java.util.UUID;

import org.springframework.boot.autoconfigure.data.web.SpringDataWebProperties.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;

import com.payment.payment_service.transfer.entity.TransferEntity;

public interface TransferRepository extends JpaRepository<TransferEntity, UUID> {

    Page<TransferEntity> findBySourceWalletIdOrDestinationWalletId(
        UUID sourceWalletId,
        UUID destinationWalletId,
        Pageable pageable
    );
    
}
