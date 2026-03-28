package com.payment.payment_service.wallet.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "processed_transfers")
@Getter
@Setter
public class ProcessedTransferEntity {

    @Id
    private UUID id;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Version
    private Long version;

}
