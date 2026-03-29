package com.payment.payment_service.shared.kafka;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import com.payment.payment_service.shared.event.DepositCompletedEvent;
import com.payment.payment_service.shared.event.TransferStatusChangedEvent;
import com.payment.payment_service.shared.event.UserCreatedEvent;
import com.payment.payment_service.shared.event.WalletCreditedEvent;
import com.payment.payment_service.shared.event.WalletDebitedEvent;
import com.payment.payment_service.shared.tracing.KafkaTracingPropagator;
import com.payment.payment_service.shared.tracing.TracingUtils;
import com.payment.payment_service.transfer.event.TransferCreatedEvent;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTracingPropagator tracingPropagator;
    private final TracingUtils tracingUtils;
    private final Tracer tracer;

    @Value("${kafka.topics.users}")
    private String usersTopic;
    @Value("${kafka.topics.wallet-debits}")
    private String walletDebitsTopic;
    @Value("${kafka.topics.wallet-credits}")
    private String walletCreditsTopic;
    @Value("${kafka.topics.transfer-created}")
    private String transferCreatedTopic;
    @Value("${kafka.topics.transfer-status}")
    private String transferStatusTopic;
    @Value("${kafka.topics.deposit-completed}")
    private String depositCompletedTopic;

    public CompletableFuture<SendResult<String, Object>> publishUserCreated(UserCreatedEvent event) {
        return send(usersTopic, event.userId().toString(), event, "user.created");
    }

    public CompletableFuture<SendResult<String, Object>> publishWalletDebited(WalletDebitedEvent event) {
        return send(walletDebitsTopic, event.walletId().toString(), event, "wallet.debited");
    }

    public CompletableFuture<SendResult<String, Object>> publishWalletCredited(WalletCreditedEvent event) {
        return send(walletCreditsTopic, event.walletId().toString(), event, "wallet.credited");
    }

    public CompletableFuture<SendResult<String, Object>> publishTransferStatusChanged(TransferStatusChangedEvent event) {
        return send(transferStatusTopic, event.transferId().toString(), event, "transfer.status-changed");
    }

    public CompletableFuture<SendResult<String, Object>> publishTransferCreated(TransferCreatedEvent event) {
        return send(transferCreatedTopic, event.transferId().toString(), event, "transfer.created");
    }

    public CompletableFuture<SendResult<String, Object>> publishDepositCompleted(DepositCompletedEvent event) {
       return send(depositCompletedTopic, event.depositId().toString(), event, "deposit.completed");
    }

    private CompletableFuture<SendResult<String, Object>> send(String topic, String key, Object event, String eventType) {
        // Criar span para a operacao de producao Kafka
        Span span = tracer.nextSpan()
            .name("kafka.produce." + eventType)
            .tag("kafka.topic", topic)
            .tag("kafka.key", key)
            .tag("event.type", eventType)
            .tag("traceId", tracingUtils.currentTraceId())
            .start();

        // Criar headers e injetar contexto de trace
        RecordHeaders headers = new RecordHeaders();
        tracingPropagator.injectTraceContext(headers);
        span.event("Trace context injected into Kafka headers");

        // Criar ProducerRecord com headers
        ProducerRecord<String, Object> record = new ProducerRecord<>(
            Objects.requireNonNull(topic),
            null,  // partition
            null,  // timestamp
            Objects.requireNonNull(key),
            event,
            headers
        );

        CompletableFuture<SendResult<String, Object>> future =
            kafkaTemplate.send(record);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                span.tag("error", "true");
                span.tag("error.message", ex.getMessage());
                span.event("Kafka send failed");
                log.error(
                    "Failed to send event to Kafka. topic={}, key={}, traceId={}",
                    topic,
                    key,
                    tracingUtils.currentTraceId(),
                    ex
                );
            } else {
                span.tag("kafka.partition", String.valueOf(result.getRecordMetadata().partition()));
                span.tag("kafka.offset", String.valueOf(result.getRecordMetadata().offset()));
                span.event("Kafka send successful");
                log.info(
                    "Event sent to Kafka. topic={}, key={}, partition={}, offset={}, traceId={}",
                    topic,
                    key,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    tracingUtils.currentTraceId()
                );
            }
            span.end();
        });

        return future;
    }
}
