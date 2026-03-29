package com.payment.payment_service.transaction.consumer;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.payment.payment_service.shared.event.TransferStatusChangedEvent;
import com.payment.payment_service.shared.event.WalletCreditedEvent;
import com.payment.payment_service.shared.event.WalletDebitedEvent;
import com.payment.payment_service.shared.kafka.KafkaEventProducer;
import com.payment.payment_service.shared.tracing.KafkaTracingPropagator;
import com.payment.payment_service.shared.tracing.TracingUtils;
import com.payment.payment_service.shared.type.TransferStatus;
import com.payment.payment_service.transaction.service.CreateTransactionService;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferConsumer {

    private final CreateTransactionService createTransactionService;
    private final KafkaEventProducer kafkaEventProducer;
    private final KafkaTracingPropagator tracingPropagator;
    private final TracingUtils tracingUtils;
    private final Tracer tracer;

    @KafkaListener(
        topics = "${kafka.topics.wallet-debits}",
        groupId = "payment-service-transaction",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeDebit(ConsumerRecord<String, WalletDebitedEvent> record) {
        WalletDebitedEvent event = record.value();
        processWalletEvent(record, event, "debit");
    }

    @KafkaListener(
        topics = "${kafka.topics.wallet-credits}",
        groupId = "payment-service-transaction",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeCredit(ConsumerRecord<String, WalletCreditedEvent> record) {
        WalletCreditedEvent event = record.value();
        processWalletEvent(record, event, "credit");
    }

    private void processWalletEvent(ConsumerRecord<?, ?> record, Object event, String eventType) {
        UUID walletId = event instanceof WalletDebitedEvent ? ((WalletDebitedEvent) event).walletId() : ((WalletCreditedEvent) event).walletId();
        UUID transferId = event instanceof WalletDebitedEvent ? ((WalletDebitedEvent) event).transferId() : ((WalletCreditedEvent) event).transferId();

        Span span = tracingPropagator.extractAndCreateSpan(record.headers(), "kafka.consume.wallet-" + eventType);
        if (span == null) {
            span = tracingPropagator.createNewSpan("kafka.consume.wallet-" + eventType);
        }

        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            span.tag("wallet.id", walletId.toString());
            span.tag("transfer.id", transferId.toString());
            span.tag("transaction.type", eventType);
            span.tag("kafka.topic", record.topic());
            span.tag("kafka.partition", String.valueOf(record.partition()));
            span.tag("kafka.offset", String.valueOf(record.offset()));

            log.info("Received Wallet{}Event walletId={} transferId={} traceId={}",
                    eventType.equals("debit") ? "Debited" : "Credited",
                    walletId, transferId, tracingUtils.currentTraceId());

            tracingUtils.eventCurrentSpan("Processing " + eventType + " transaction");

            if (event instanceof WalletDebitedEvent) {
                WalletDebitedEvent debitEvent = (WalletDebitedEvent) event;
                createTransactionService.executeDebit(debitEvent.walletId(), debitEvent.transferId(), debitEvent.amount());
            } else {
                WalletCreditedEvent creditEvent = (WalletCreditedEvent) event;
                createTransactionService.executeCredit(creditEvent.walletId(), creditEvent.transferId(), creditEvent.amount());
            }

            tracingUtils.eventCurrentSpan("Transaction created, publishing completion");
            publishTransferCompletion(transferId);

        } catch (Exception ex) {
            span.tag("error", "true");
            span.tag("error.message", ex.getMessage());
            span.event("Exception: " + ex.getClass().getSimpleName());
            log.error("Error processing Wallet{}Event transferId={} traceId={}",
                    eventType.equals("debit") ? "Debited" : "Credited",
                    transferId, tracingUtils.currentTraceId(), ex);
            publishTransferFailure(transferId);
            throw ex;
        } finally {
            span.end();
        }
    }

    private void publishTransferCompletion(UUID transferId) {
        try {
            var future = kafkaEventProducer.publishTransferStatusChanged(
                new TransferStatusChangedEvent(transferId, TransferStatus.COMPLETED)
            );
            future.get(10, TimeUnit.SECONDS);

            log.info("Published TransferStatusChangedEvent(COMPLETED) transferId={} traceId={}",
                    transferId, tracingUtils.currentTraceId());

        } catch (Exception ex) {
            log.error("Failed to publish COMPLETED status transferId={} traceId={}",
                    transferId, tracingUtils.currentTraceId(), ex);
            throw new RuntimeException("Failed to publish transfer completion", ex);
        }
    }

    private void publishTransferFailure(UUID transferId) {
        try {
            var future = kafkaEventProducer.publishTransferStatusChanged(
                new TransferStatusChangedEvent(transferId, TransferStatus.FAILED)
            );
            future.get(10, TimeUnit.SECONDS);

            log.info("Published TransferStatusChangedEvent(FAILED) transferId={} traceId={}",
                    transferId, tracingUtils.currentTraceId());

        } catch (Exception ex) {
            log.error("Failed to publish FAILED status transferId={} traceId={}",
                    transferId, tracingUtils.currentTraceId(), ex);
        }
    }
}