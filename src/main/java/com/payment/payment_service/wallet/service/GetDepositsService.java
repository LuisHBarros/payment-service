package com.payment.payment_service.wallet.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.payment.payment_service.wallet.dto.DepositListDTO;
import com.payment.payment_service.wallet.entity.DepositEntity;
import com.payment.payment_service.wallet.repository.DepositRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetDepositsService {

    private final DepositRepository depositRepository;

    public List<DepositListDTO> execute(UUID userId) {
        Objects.requireNonNull(userId, "userId");

        List<DepositEntity> deposits = depositRepository.findByUserId(userId);

        log.info("Retrieved {} deposits for userId={}", deposits.size(), userId);

        return deposits.stream()
            .map(this::toDepositListDTO)
            .collect(Collectors.toList());
    }

    private DepositListDTO toDepositListDTO(DepositEntity entity) {
        return new DepositListDTO(
            entity.getId(),
            entity.getUserId(),
            entity.getWalletId(),
            entity.getExternalPaymentReference(),
            entity.getAmount(),
            entity.getStatus(),
            entity.getPaymentProvider(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
