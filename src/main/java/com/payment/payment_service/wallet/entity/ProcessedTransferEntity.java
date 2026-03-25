package com.payment.payment_service.wallet.entity;

import java.util.UUID;

import com.payment.payment_service.shared.entity.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "processed_transfers")
public class ProcessedTransferEntity extends BaseEntity {

}
