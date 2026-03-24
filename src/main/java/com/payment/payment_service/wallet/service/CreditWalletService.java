
package com.payment.payment_service.wallet.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payment.payment_service.wallet.repository.WalletRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CreditWalletService {

    private final GetWalletService getWalletService;
    private final WalletRepository walletRepository;

    @Transactional
    public void execute(UUID walletId, BigDecimal amount) {
        var wallet = getWalletService.execute(walletId);
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);
    }
}
