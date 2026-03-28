package com.payment.payment_service.wallet.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.payment.payment_service.wallet.entity.WalletEntity;
import com.payment.payment_service.wallet.exception.WalletNotFoundException;
import com.payment.payment_service.wallet.repository.WalletRepository;

@Service
public class GetWalletService {
    
    private final WalletRepository walletRepository;
    
    public GetWalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }
    
    public WalletEntity execute(UUID userId) {
        return walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user"));
    }
    
}
