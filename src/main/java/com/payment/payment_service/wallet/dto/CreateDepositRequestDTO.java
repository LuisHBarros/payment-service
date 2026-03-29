package com.payment.payment_service.wallet.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.payment.payment_service.wallet.type.PaymentProviderName;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateDepositRequestDTO(
    @NotNull @Positive BigDecimal amount,
    @NotNull UUID walletId,
    @NotNull PaymentProviderName paymentProvider
) {}
