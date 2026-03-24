package com.payment.payment_service.transfer.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payment.payment_service.shared.type.TransferStatus;
import com.payment.payment_service.transfer.entity.TransferEntity;
import com.payment.payment_service.transfer.exception.TransferException;
import com.payment.payment_service.transfer.repository.TransferRepository;
import com.payment.payment_service.wallet.service.CreditWalletService;
import com.payment.payment_service.wallet.service.DebitWalletService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CreateTransferServiceTest {

    private final TransferRepository transferRepository;
    private final TransferAuthorizationService transferAuthorizationService;
    private final DebitWalletService debitWalletService;
    private final CreditWalletService creditWalletService;

    @Transactional
    public UUID execute(UUID sourceWalletId, UUID destinationWalletId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransferException("amount must be greater than zero");
        }

        var transfer = new TransferEntity();
        transfer.setSourceWalletId(sourceWalletId);
        transfer.setDestinationWalletId(destinationWalletId);
        transfer.setAmount(amount);
        transferRepository.save(transfer);

        try {
            transferAuthorizationService.authorize(sourceWalletId, destinationWalletId, amount);
            debitWalletService.execute(sourceWalletId, amount);
            creditWalletService.execute(destinationWalletId, amount);
            transfer.setStatus(TransferStatus.COMPLETED);
        } catch (Exception e) {
            transfer.setStatus(TransferStatus.FAILED);
            throw e;
        } finally {
            transferRepository.save(transfer);
        }

        return transfer.getId();
    }
}