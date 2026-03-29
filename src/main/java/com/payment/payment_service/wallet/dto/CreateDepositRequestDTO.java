package com.payment.payment_service.wallet.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.payment.payment_service.wallet.type.PaymentProviderName;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(name = "CreateDepositRequest", description = "Payload used to create a deposit intent")
public record CreateDepositRequestDTO(
    @Schema(description = "Deposit amount", example = "250.00")
    @NotNull @Positive BigDecimal amount,
    @Schema(description = "Target wallet identifier")
    @NotNull UUID walletId,
    @Schema(description = "Payment provider used to create the deposit")
    @NotNull PaymentProviderName paymentProvider
) {}
