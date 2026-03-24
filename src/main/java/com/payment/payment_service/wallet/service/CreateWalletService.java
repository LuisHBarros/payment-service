package com.payment.payment_service.wallet.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.payment.payment_service.wallet.entity.WalletEntity;
import com.payment.payment_service.wallet.exception.WalletAlreadyExistsException;
import com.payment.payment_service.wallet.repository.WalletRepository;

import org.springframework.transaction.annotation.Transactional;


@Service
public class CreateWalletService {

    private final WalletRepository walletRepository;

    public CreateWalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }
    

    @Transactional
    public void execute(UUID userId) {
        if(walletRepository.findByUserId(userId).isPresent()) {
            throw new WalletAlreadyExistsException("Wallet already exists for user");
        }

        WalletEntity wallet = new WalletEntity();
        wallet.setUserId(userId);
        walletRepository.save(wallet);
    }
    
}
