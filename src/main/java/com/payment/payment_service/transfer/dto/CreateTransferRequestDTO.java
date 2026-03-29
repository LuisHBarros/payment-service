package com.payment.payment_service.transfer.dto;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(name = "CreateTransferRequest", description = "Payload used to create a wallet transfer")
public record CreateTransferRequestDTO(
    @Schema(description = "Wallet initiating the transfer")
    @NotNull UUID sourceWalletId,
    @Schema(description = "Wallet receiving the transfer")
    @NotNull UUID destinationWalletId,
    @Schema(description = "Transfer amount", example = "150.00")
    @NotNull @Positive BigDecimal amount
) {}
