package com.payment.payment_service.wallet.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.payment.payment_service.wallet.type.DepositStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DepositResponse", description = "Deposit intent response")
public record DepositResponseDTO(
    @Schema(description = "Deposit identifier")
    UUID depositId,
    @Schema(description = "Client secret returned by the payment provider")
    String clientSecret,
    @Schema(description = "Current deposit status")
    DepositStatus status,
    @Schema(description = "Requested amount")
    BigDecimal amount
) {
}
