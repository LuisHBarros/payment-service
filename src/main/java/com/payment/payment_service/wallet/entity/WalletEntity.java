package com.payment.payment_service.wallet.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.payment.payment_service.shared.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
public class WalletEntity extends BaseEntity {
    
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;
    
    @Column(name = "balance", nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

}
