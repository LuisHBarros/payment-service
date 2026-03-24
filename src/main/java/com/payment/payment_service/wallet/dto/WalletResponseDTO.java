package com.payment.payment_service.wallet.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record WalletResponseDTO(
    UUID id,
    UUID userId,
    BigDecimal balance,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    
}
