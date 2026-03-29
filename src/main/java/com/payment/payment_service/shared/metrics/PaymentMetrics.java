package com.payment.payment_service.shared.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class PaymentMetrics {

    private final MeterRegistry registry;

    // --- Transfers ---
    public void recordTransferCreated(BigDecimal amount) {
        registry.counter("payment.transfers.created.total").increment();
        registry.summary("payment.transfers.amount").record(amount.doubleValue());
    }

    public void recordTransferCompleted() {
        registry.counter("payment.transfers.completed.total").increment();
    }

    public void recordTransferFailed(String reason) {
        registry.counter("payment.transfers.failed.total",
            "reason", reason 
        ).increment();
    }

    // --- Outbox ---
    public void recordOutboxPublished(String eventType) {
        registry.counter("payment.outbox.published.total",
            "event_type", eventType
        ).increment();
    }

    public void recordOutboxFailed(String eventType) {
        registry.counter("payment.outbox.failed.total",
            "event_type", eventType
        ).increment();
    }

    public Timer outboxLatencyTimer() {
        return registry.timer("payment.outbox.latency");
    }

    // --- Users ---
    public void recordUserCreated(String userType) {
        registry.counter("payment.users.created.total",
            "type", userType
        ).increment();
    }
}
