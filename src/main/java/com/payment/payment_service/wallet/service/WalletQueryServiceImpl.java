package com.payment.payment_service.wallet.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.payment.payment_service.shared.dto.WalletSummary;
import com.payment.payment_service.shared.query.WalletQueryService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WalletQueryServiceImpl implements WalletQueryService {

    private final GetWalletService getWalletService;

    @Override
    public WalletSummary getSummary(UUID id) {
        var wallet = getWalletService.execute(id);
        return new WalletSummary(wallet.getId(), wallet.getUserId(), wallet.getBalance());
    }
}