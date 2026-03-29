package com.payment.payment_service.wallet.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.payment.payment_service.wallet.entity.WalletEntity;
import com.payment.payment_service.wallet.exception.WalletAlreadyExistsException;
import com.payment.payment_service.wallet.repository.WalletRepository;

import lombok.extern.slf4j.Slf4j;

import org.springframework.transaction.annotation.Transactional;


import lombok.RequiredArgsConstructor;

@Service
@Slf4j
@RequiredArgsConstructor
public class CreateWalletService {

    private final WalletRepository walletRepository;

    @Transactional
    public void execute(UUID userId) {
        if(walletRepository.findByUserId(userId).isPresent()) {
            log.info("Wallet already exists for user: {}, skipping", userId);
            throw new WalletAlreadyExistsException("Wallet already exists for user");
        }

        try {
            WalletEntity wallet = new WalletEntity();
            wallet.setUserId(userId);
            walletRepository.save(wallet);
            walletRepository.flush();
        } catch (DataIntegrityViolationException e) {
            log.info("Wallet already created for user: {}, skipping", userId);
        }
    }
    
}
