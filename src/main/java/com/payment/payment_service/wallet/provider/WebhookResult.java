package com.payment.payment_service.wallet.provider;

import com.payment.payment_service.wallet.type.DepositStatus;

public record WebhookResult(String externalReference, DepositStatus status) {
}
