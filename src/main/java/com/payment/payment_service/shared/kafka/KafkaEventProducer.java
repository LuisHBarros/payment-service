package com.payment.payment_service.shared.kafka;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import com.payment.payment_service.shared.event.DepositCompletedEvent;
import com.payment.payment_service.shared.event.TransferStatusChangedEvent;
import com.payment.payment_service.shared.event.UserCreatedEvent;
import com.payment.payment_service.shared.event.WalletCreditedEvent;
import com.payment.payment_service.shared.event.WalletDebitedEvent;
import com.payment.payment_service.transfer.event.TransferCreatedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

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
        return send(usersTopic, event.userId().toString(), event);
    }

    public CompletableFuture<SendResult<String, Object>> publishWalletDebited(WalletDebitedEvent event) {
        return send(walletDebitsTopic, event.walletId().toString(), event);
    }

    public CompletableFuture<SendResult<String, Object>> publishWalletCredited(WalletCreditedEvent event) {
        return send(walletCreditsTopic, event.walletId().toString(), event);
    }

    public CompletableFuture<SendResult<String, Object>> publishTransferStatusChanged(TransferStatusChangedEvent event) {
        return send(transferStatusTopic, event.transferId().toString(), event);
    }

    public CompletableFuture<SendResult<String, Object>> publishTransferCreated(TransferCreatedEvent event) {
        return send(transferCreatedTopic, event.transferId().toString(), event);
    }
    
    public CompletableFuture<SendResult<String, Object>> publishDepositCompleted(DepositCompletedEvent event) {
       return send(depositCompletedTopic, event.depositId().toString(), event);
    }

    private CompletableFuture<SendResult<String, Object>> send(String topic, String key, Object event) {
        CompletableFuture<SendResult<String, Object>> future =
            kafkaTemplate.send(Objects.requireNonNull(topic), Objects.requireNonNull(key), event);

            future.whenComplete((result, ex)-> {
                if (ex != null) {
                    log.error(
                        "Failed to send event to Kafka. topic={}, key={}, payload={}",
                        topic,
                        key,
                        event,
                        ex
                    );
                } else {
                    log.info(
                        "Event sent to Kafka. topic={}, key={}, partition={}, offset={}",
                        topic,
                        key,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset()
                    );
                }
            });
            return future;
    }
}
