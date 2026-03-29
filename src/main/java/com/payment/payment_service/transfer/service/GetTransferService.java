package com.payment.payment_service.transfer.service;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.payment.payment_service.transfer.dto.TransferFilterDTO;
import com.payment.payment_service.transfer.entity.TransferEntity;
import com.payment.payment_service.transfer.exception.TransferNotFoundException;
import com.payment.payment_service.transfer.repository.TransferRepository;
import com.payment.payment_service.transfer.specification.TransferSpecification;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetTransferService {

    private final TransferRepository transferRepository;

    public TransferEntity execute(@NonNull UUID id) {
        return transferRepository.findById(id)
            .orElseThrow(() -> new TransferNotFoundException("transfer not found"));
    }

    public Page<TransferEntity> findByWalletId(@NonNull UUID walletId, Pageable pageable, TransferFilterDTO filter) {
        Specification<TransferEntity> spec = TransferSpecification.withFilters(walletId, filter);
        return transferRepository.findAll(spec, pageable);
    }
}
