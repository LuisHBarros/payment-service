package com.payment.payment_service.wallet.repository;

import com.payment.payment_service.wallet.entity.DepositEntity;
import com.payment.payment_service.wallet.type.PaymentProviderName;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DepositRepository extends JpaRepository<DepositEntity, UUID> {
    Optional<DepositEntity> findByPaymentProviderAndExternalPaymentReference(PaymentProviderName paymentProvider, String externalPaymentReference);
}
