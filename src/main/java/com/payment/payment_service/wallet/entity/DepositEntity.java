package com.payment.payment_service.wallet.entity;

import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.payment.payment_service.shared.entity.BaseEntity;
import com.payment.payment_service.wallet.type.DepositStatus;
import com.payment.payment_service.wallet.type.PaymentProviderName;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "deposits")
@Getter
@Setter
@NoArgsConstructor
public class DepositEntity extends BaseEntity {
    
    @Column(nullable = false)
    private UUID userId;
    
    @Column(nullable = false)
    private UUID walletId;
    
    @Column(nullable = false)
    private String externalPaymentReference;
    
    @Column(nullable = false)
    private BigDecimal amount;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DepositStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProviderName paymentProvider;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String providerResponse;
    
}
