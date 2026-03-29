package com.payment.payment_service.wallet.service;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.payment.payment_service.wallet.dto.CreateDepositRequestDTO;
import com.payment.payment_service.wallet.dto.DepositResponseDTO;
import com.payment.payment_service.wallet.entity.DepositEntity;
import com.payment.payment_service.wallet.entity.WalletEntity;
import com.payment.payment_service.wallet.exception.WalletNotFoundException;
import com.payment.payment_service.wallet.provider.PaymentProvider;
import com.payment.payment_service.wallet.provider.PaymentProviderResponse;
import com.payment.payment_service.wallet.repository.DepositRepository;
import com.payment.payment_service.wallet.repository.WalletRepository;
import com.payment.payment_service.wallet.type.DepositStatus;
import com.payment.payment_service.wallet.exception.InvalidPaymentProviderException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreateDepositService {

    private final WalletRepository walletRepository;
    private final DepositRepository depositRepository;
    private final Map<String, PaymentProvider> paymentProviders;
    private final TransactionTemplate transactionTemplate;

    public DepositResponseDTO execute(UUID userId, @Valid CreateDepositRequestDTO request) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(request.amount(), "amount");
        Objects.requireNonNull(request.walletId(), "walletId");
        Objects.requireNonNull(request.paymentProvider(), "paymentProvider");

        WalletEntity wallet = walletRepository.findById(request.walletId())
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + request.walletId()));

        if (!wallet.getUserId().equals(userId)) {
            throw new WalletNotFoundException("Wallet not found: " + request.walletId());
        }

        PaymentProvider provider = paymentProviders.get(request.paymentProvider().name());
        if (provider == null) {
            throw new InvalidPaymentProviderException(
                "No payment provider registered for: " + request.paymentProvider().name());
        }

        PaymentProviderResponse response = provider.createDeposit(
            request.amount(), userId, request.walletId());
         return transactionTemplate.execute(status -> {
            DepositEntity deposit = new DepositEntity();
            deposit.setUserId(userId);
            deposit.setWalletId(wallet.getId());
            deposit.setExternalPaymentReference(response.externalPaymentReference());
            deposit.setAmount(request.amount());
            deposit.setStatus(DepositStatus.PENDING);
            deposit.setPaymentProvider(request.paymentProvider());
            deposit.setProviderResponse(response.rawResponse());
            depositRepository.save(deposit);

            log.info("Created deposit depositId={} walletId={} amount={} provider={}",
                deposit.getId(), request.walletId(), request.amount(), request.paymentProvider());

            return new DepositResponseDTO(
                deposit.getId(),
                response.clientSecret(),
                deposit.getStatus(),
                request.amount()
            );
        });
    }
}
