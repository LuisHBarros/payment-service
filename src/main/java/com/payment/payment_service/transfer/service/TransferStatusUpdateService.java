package com.payment.payment_service.transfer.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;

import com.payment.payment_service.shared.type.TransferStatus;
import com.payment.payment_service.transfer.entity.TransferEntity;
import com.payment.payment_service.transfer.exception.TransferNotFoundException;
import com.payment.payment_service.transfer.repository.TransferRepository;

import org.springframework.transaction.annotation.Transactional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransferStatusUpdateService {
    
    private final TransferRepository transferRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(@NonNull UUID transferID, @NonNull TransferStatus newStatus){
        TransferEntity transfer = transferRepository.findById(transferID)
            .orElseThrow(() -> new TransferNotFoundException("Transfer not found"));
        transfer.setStatus(newStatus);
        transferRepository.save(transfer);
    }
}
