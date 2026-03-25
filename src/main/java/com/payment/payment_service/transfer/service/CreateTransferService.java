package com.payment.payment_service.transfer.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payment.payment_service.shared.type.TransferStatus;
import com.payment.payment_service.transfer.entity.TransferEntity;
import com.payment.payment_service.transfer.event.TransferCreatedEvent;
import com.payment.payment_service.transfer.exception.TransferException;
import com.payment.payment_service.transfer.repository.TransferRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateTransferService {

    private final TransferRepository transferRepository;
    private final TransferAuthorizationService transferAuthorizationService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public UUID execute(UUID sourceWalletId, UUID destinationWalletId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransferException("amount must be greater than zero");
        }

        // Autorizar transferência antes de criar
        transferAuthorizationService.authorize(sourceWalletId, destinationWalletId, amount);

        // Criar transferência com status PENDING
        var transfer = new TransferEntity();
        transfer.setSourceWalletId(sourceWalletId);
        transfer.setDestinationWalletId(destinationWalletId);
        transfer.setAmount(amount);
        transfer.setStatus(TransferStatus.PENDING);
        transferRepository.save(transfer);

        // Publicar evento após commit da transação
        eventPublisher.publishEvent(
            new TransferCreatedEvent(
                transfer.getId(),
                transfer.getSourceWalletId(),
                transfer.getDestinationWalletId(),
                transfer.getAmount()
            )
        );
        return transfer.getId();
    }
}