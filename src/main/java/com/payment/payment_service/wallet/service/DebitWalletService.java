package com.payment.payment_service.wallet.service;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.payment.payment_service.wallet.entity.WalletEntity;
import com.payment.payment_service.wallet.exception.InsufficientBalanceException;
import com.payment.payment_service.wallet.exception.WalletNotFoundException;
import com.payment.payment_service.wallet.repository.WalletRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DebitWalletService {
    
    private final WalletRepository walletRepository;

    @Transactional
    public void execute(UUID walletId, BigDecimal amount) {
        UUID nonNullWalletId = Objects.requireNonNull(walletId, "walletId");
        WalletEntity wallet = walletRepository.findById(nonNullWalletId).orElseThrow(() -> new WalletNotFoundException("Wallet not found"));

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("The wallet does not have enough balance");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);
    }

}
