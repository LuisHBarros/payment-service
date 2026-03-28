package com.payment.payment_service.shared.query;

import java.util.UUID;

import org.springframework.lang.NonNull;

import com.payment.payment_service.shared.dto.WalletSummary;

public interface WalletQueryService {
    WalletSummary getSummary(@NonNull UUID id);
}