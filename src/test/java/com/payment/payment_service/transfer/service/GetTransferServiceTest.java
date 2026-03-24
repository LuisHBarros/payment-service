package com.payment.payment_service.transfer.service;

import java.util.UUID;

import org.springframework.boot.autoconfigure.data.web.SpringDataWebProperties.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.payment.payment_service.transfer.entity.TransferEntity;
import com.payment.payment_service.transfer.exception.TransferNotFoundException;
import com.payment.payment_service.transfer.repository.TransferRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetTransferServiceTest {

    private final TransferRepository transferRepository;

    public TransferEntity execute(UUID id) {
        return transferRepository.findById(id)
            .orElseThrow(() -> new TransferNotFoundException("transfer not found"));
    }

    public Page<TransferEntity> findByWalletId(UUID walletId, Pageable pageable) {
        return transferRepository.findBySourceWalletIdOrDestinationWalletId(walletId, walletId, pageable);
    }
}