package com.payment.payment_service.shared.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.payment.payment_service.shared.entity.OutboxEntity;

import java.time.LocalDateTime;
import java.util.List;



public interface OutboxRepository extends JpaRepository<OutboxEntity, UUID> {
    @Query(value = "SELECT * FROM outbox WHERE processed = false ORDER BY created_at ASC LIMIT 50 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEntity> findNextBatchForProcessing();
    @Modifying
    @Query("DELETE FROM OutboxEntity o WHERE o.processed = true AND o.processedAt < :cutoff")
    int deleteProcessedOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
