package com.payment.payment_service.transfer.dto;

import java.time.LocalDate;

import com.payment.payment_service.shared.type.TransferStatus;
import com.payment.payment_service.shared.type.TransferType;

public record TransferFilterDTO(
    TransferStatus status,
    LocalDate startDate,
    LocalDate endDate,
    TransferType type
) {
    
}
