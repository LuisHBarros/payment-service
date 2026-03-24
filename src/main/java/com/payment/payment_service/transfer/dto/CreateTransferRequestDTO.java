package com.payment.payment_service.transfer.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateTransferRequestDTO(
    @NotNull UUID sourceWalletId,
    @NotNull UUID destinationWalletId,
    @NotNull @Positive BigDecimal amount
) {}