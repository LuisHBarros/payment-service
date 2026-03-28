package com.payment.payment_service.transfer.service;

import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.payment.payment_service.shared.dto.UserSummary;
import com.payment.payment_service.shared.dto.WalletSummary;
import com.payment.payment_service.shared.query.UserQueryService;
import com.payment.payment_service.shared.query.WalletQueryService;
import com.payment.payment_service.transfer.exception.UnauthorizedTransferException;
import com.payment.payment_service.user.type.UserType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransferAuthorizationService {

    private final UserQueryService userQueryService;
    private final WalletQueryService walletQueryService;


    public void authorize(UUID sourceWalletId, UUID destinationWalletId, BigDecimal amount) {
        WalletSummary sourceWallet = walletQueryService.getSummary(Objects.requireNonNull(sourceWalletId));
        WalletSummary destinationWallet = walletQueryService.getSummary(Objects.requireNonNull(destinationWalletId));
        
        UserSummary sender = userQueryService.getSummary(sourceWallet.userId());
        UserSummary receiver = userQueryService.getSummary(destinationWallet.userId());

        if (sender.type().equals(UserType.MERCHANT)) {
            throw new AccessDeniedException("Merchants cannot initiate transfers");
        }

        if (sender.id().equals(receiver.id())) {
            throw new UnauthorizedTransferException("sender and receiver must be different users");
        }
        if (!sender.canSend()) {
            throw new UnauthorizedTransferException("only COMMON users can send transfers");
        }
        if (!receiver.canReceive()) {
            throw new UnauthorizedTransferException("only MERCHANT users can receive transfers");
        }
        if (sourceWallet.balance().compareTo(amount) < 0) {
            throw new UnauthorizedTransferException("insufficient balance");
        }
    }
}
