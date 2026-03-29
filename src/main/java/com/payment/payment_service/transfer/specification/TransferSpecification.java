package com.payment.payment_service.transfer.specification;

import java.time.LocalDateTime;
import java.util.UUID;

import com.payment.payment_service.shared.type.TransferType;
import com.payment.payment_service.transfer.entity.TransferEntity;
import com.payment.payment_service.transfer.dto.TransferFilterDTO;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

public class TransferSpecification {

    public static Specification<TransferEntity> withFilters(UUID walletId, TransferFilterDTO filter) {
        return (root, query, cb) -> {
            Predicate predicate = cb.disjunction();

            if (filter.type() == null || filter.type() == TransferType.DEBIT) {
                predicate = cb.or(predicate, cb.equal(root.get("sourceWalletId"), walletId));
            }
            if (filter.type() == null || filter.type() == TransferType.CREDIT) {
                predicate = cb.or(predicate, cb.equal(root.get("destinationWalletId"), walletId));
            }

            if (filter.status() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), filter.status()));
            }

            if (filter.startDate() != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("createdAt"), filter.startDate().atStartOfDay()));
            }

            if (filter.endDate() != null) {
                predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.get("createdAt"), filter.endDate().atTime(23, 59, 59)));
            }

            return predicate;
        };
    }
}
