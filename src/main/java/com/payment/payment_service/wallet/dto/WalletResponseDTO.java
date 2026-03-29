package com.payment.payment_service.wallet.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "WalletResponse", description = "Wallet returned by the API")
public record WalletResponseDTO(
    @Schema(description = "Wallet identifier")
    UUID id,
    @Schema(description = "Owner user identifier")
    UUID userId,
    @Schema(description = "Current wallet balance")
    BigDecimal balance,
    @Schema(description = "Creation timestamp")
    LocalDateTime createdAt,
    @Schema(description = "Last update timestamp")
    LocalDateTime updatedAt
) {
    
}
