package com.payment.payment_service.wallet.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import org.springframework.lang.NonNull;

import com.payment.payment_service.shared.dto.WalletSummary;
import com.payment.payment_service.shared.query.WalletQueryService;
import com.payment.payment_service.wallet.exception.WalletNotFoundException;
import com.payment.payment_service.wallet.repository.WalletRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WalletQueryServiceImpl implements WalletQueryService {

    private final WalletRepository walletRepository;

    @Override
    public WalletSummary getSummary(@NonNull UUID walletId) {
        var wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));
        return new WalletSummary(wallet.getId(), wallet.getUserId(), wallet.getBalance());
    }
}
