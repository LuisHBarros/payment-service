package com.payment.payment_service.wallet.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.payment.payment_service.shared.event.TransferStatusChangedEvent;
import com.payment.payment_service.shared.kafka.KafkaEventProducer;
import com.payment.payment_service.shared.tracing.KafkaTracingPropagator;
import com.payment.payment_service.shared.tracing.TracingUtils;
import com.payment.payment_service.shared.type.TransferStatus;
import com.payment.payment_service.transfer.event.TransferCreatedEvent;
import com.payment.payment_service.wallet.service.ProcessTransferService;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferWalletConsumer {

    private final ProcessTransferService processTransferService;
    private final KafkaEventProducer kafkaEventProducer;
    private final KafkaTracingPropagator tracingPropagator;
    private final TracingUtils tracingUtils;
    private final Tracer tracer;

    @KafkaListener(
        topics = "${kafka.topics.transfer-created}",
        groupId = "payment-service-wallet",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, TransferCreatedEvent> record) {
        TransferCreatedEvent event = record.value();

        // Extrair ou criar span de trace
        Span span = tracingPropagator.extractAndCreateSpan(record.headers(), "kafka.consume.transfer-created");
        if (span == null) {
            span = tracingPropagator.createNewSpan("kafka.consume.transfer-created");
        }

        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            span.tag("transfer.id", event.transferId().toString());
            span.tag("transfer.source_wallet", event.sourceWalletId().toString());
            span.tag("transfer.destination_wallet", event.destinationWalletId().toString());
            span.tag("transfer.amount", event.amount().toString());
            span.tag("kafka.topic", record.topic());
            span.tag("kafka.partition", String.valueOf(record.partition()));
            span.tag("kafka.offset", String.valueOf(record.offset()));

            log.info("Received TransferCreatedEvent transferId={} sourceWallet={} destWallet={} traceId={}",
                     event.transferId(), event.sourceWalletId(), event.destinationWalletId(),
                     tracingUtils.currentTraceId());

            tracingUtils.eventCurrentSpan("Processing transfer");
            processTransfer(event);
            tracingUtils.eventCurrentSpan("Transfer processed successfully");

        } catch (Exception ex) {
            span.tag("error", "true");
            span.tag("error.message", ex.getMessage());
            span.event("Exception: " + ex.getClass().getSimpleName());
            log.error("Error processing TransferCreatedEvent for transferId={} traceId={}",
                     event.transferId(), tracingUtils.currentTraceId(), ex);
            throw ex;
        } finally {
            span.end();
        }
    }

    private void processTransfer(TransferCreatedEvent event) {
        try {
            // Process transfer with lock ordering to prevent deadlocks
            tracingUtils.withSpan("wallet.process-transfer", () -> {
                processTransferService.execute(
                    event.transferId(),
                    event.sourceWalletId(),
                    event.destinationWalletId(),
                    event.amount()
                );
                return null;
            });

            log.info("Successfully processed transferId={} traceId={}",
                    event.transferId(), tracingUtils.currentTraceId());
        } catch (Exception e) {
            log.error("Failed to process transferId={} traceId={}",
                    event.transferId(), tracingUtils.currentTraceId(), e);

            // Publish FAILED status on error
            try {
                kafkaEventProducer.publishTransferStatusChanged(
                    new TransferStatusChangedEvent(event.transferId(), TransferStatus.FAILED)
                );
            } catch (Exception ex) {
                log.error("Failed to publish FAILED status for transferId={}", event.transferId(), ex);
            }

            throw e;
        }
    }
}
