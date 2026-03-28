package com.payment.payment_service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.payment.payment_service.shared.entity.OutboxEntity;
import com.payment.payment_service.shared.type.TransferStatus;
import com.payment.payment_service.transfer.entity.TransferEntity;
import com.payment.payment_service.transfer.event.TransferCreatedEvent;
import com.payment.payment_service.transaction.type.TransactionType;
import com.payment.payment_service.user.dto.CreateUserRequestDTO;
import com.payment.payment_service.wallet.entity.WalletEntity;

class TransferFlowIT extends AbstractIntegrationTest {

    private static final BigDecimal TRANSFER_AMOUNT = new BigDecimal("30.00");
    private static final BigDecimal SENDER_BALANCE = new BigDecimal("100.00");
    private static final BigDecimal RECEIVER_BALANCE = new BigDecimal("50.00");

    @Test
    @DisplayName("Full transfer flow: API -> Outbox -> Kafka -> Debit/Credit -> COMPLETED")
    void fullFlow_shouldCompleteSuccessfully() {
        UUID senderId = Objects.requireNonNull(testHelper.createCommonUser("Flow Sender", "flow.sender@example.com", "Pass123!"));
        UUID receiverId = Objects.requireNonNull(testHelper.createMerchantUser("Flow Receiver", "flow.receiver@example.com", "Pass123!"));
        UUID sourceWalletId = Objects.requireNonNull(testHelper.createWallet(senderId, SENDER_BALANCE));
        UUID destWalletId = Objects.requireNonNull(testHelper.createWallet(receiverId, RECEIVER_BALANCE));

        UUID transferId = Objects.requireNonNull(assertDoesNotThrow(() ->
            createTransferService.execute(sourceWalletId, destWalletId, TRANSFER_AMOUNT)
        ));
        
        await().untilAsserted(() -> {
            TransferEntity transfer = transferRepository.findById(transferId).orElseThrow();
            WalletEntity source = walletRepository.findById(sourceWalletId).orElseThrow();
            WalletEntity dest = walletRepository.findById(destWalletId).orElseThrow();

            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
            assertThat(source.getBalance()).isEqualByComparingTo(SENDER_BALANCE.subtract(TRANSFER_AMOUNT));
            assertThat(dest.getBalance()).isEqualByComparingTo(RECEIVER_BALANCE.add(TRANSFER_AMOUNT));
        });
    }

    @Test
    @DisplayName("User creation flow: API -> Kafka -> Wallet auto-created")
    void userCreation_shouldCreateWalletViaKafka() throws JsonProcessingException {
        CreateUserRequestDTO request = new CreateUserRequestDTO(
            "Kafka Wallet User", "kafka.wallet@example.com", "Pass123!", TestHelper.generateValidCpf()
        );

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/users", request, String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        UUID userId = objectMapper.readValue(Objects.requireNonNull(response.getBody()), UUID.class);
        assertThat(userId).isNotNull();

        await()
            .atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(300))
            .untilAsserted(() -> {
                var wallet = walletRepository.findByUserId(userId);
                assertThat(wallet).isPresent();
                assertThat(wallet.get().getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            });
    }

    @Test
    @DisplayName("Transfer with insufficient balance should be marked FAILED")
    void processingFailure_shouldMarkTransferFailed() throws JsonProcessingException {
        UUID senderId = Objects.requireNonNull(testHelper.createCommonUser("Fail Sender", "fail.sender@example.com", "Pass123!"));
        UUID receiverId = Objects.requireNonNull(testHelper.createMerchantUser("Fail Receiver", "fail.receiver@example.com", "Pass123!"));
        UUID sourceWalletId = Objects.requireNonNull(testHelper.createWallet(senderId, new BigDecimal("10.00")));
        UUID destWalletId = Objects.requireNonNull(testHelper.createWallet(receiverId, RECEIVER_BALANCE));

        TransferEntity transfer = new TransferEntity();
        transfer.setSourceWalletId(sourceWalletId);
        transfer.setDestinationWalletId(destWalletId);
        transfer.setAmount(new BigDecimal("100.00"));
        transfer.setStatus(TransferStatus.PENDING);
        transferRepository.save(transfer);

        TransferCreatedEvent event = new TransferCreatedEvent(
            transfer.getId(), sourceWalletId, destWalletId, new BigDecimal("100.00")
        );
        String payload = objectMapper.writeValueAsString(event);

        OutboxEntity outbox = new OutboxEntity();
        outbox.setAggregateType("transfer");
        outbox.setAggregateId(transfer.getId());
        outbox.setEventType("TRANSFER_CREATED");
        outbox.setPayload(payload);
        outboxRepository.save(outbox);

        await()
            .atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(300))
            .untilAsserted(() -> {
                TransferEntity t = transferRepository.findById(Objects.requireNonNull(transfer.getId())).orElseThrow();
                assertThat(t.getStatus()).isEqualTo(TransferStatus.FAILED);
            });

        WalletEntity sourceWallet = walletRepository.findById(sourceWalletId).orElseThrow();
        WalletEntity destWallet = walletRepository.findById(destWalletId).orElseThrow();

        assertThat(sourceWallet.getBalance()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(destWallet.getBalance()).isEqualByComparingTo(RECEIVER_BALANCE);
        assertThat(transactionRepository.existsByWalletIdAndTransferIdAndType(
            sourceWalletId, transfer.getId(), TransactionType.DEBIT
        )).isFalse();
        assertThat(transactionRepository.existsByWalletIdAndTransferIdAndType(
            destWalletId, transfer.getId(), TransactionType.CREDIT
        )).isFalse();
    }

    @Test
    @DisplayName("Duplicate outbox entries should not double-debit wallet")
    void idempotentProcessing_shouldNotDoubleDebit() throws JsonProcessingException {
        UUID senderId = Objects.requireNonNull(testHelper.createCommonUser("Idem Sender", "idem.sender@example.com", "Pass123!"));
        UUID receiverId = Objects.requireNonNull(testHelper.createMerchantUser("Idem Receiver", "idem.receiver@example.com", "Pass123!"));
        UUID sourceWalletId = Objects.requireNonNull(testHelper.createWallet(senderId, SENDER_BALANCE));
        UUID destWalletId = Objects.requireNonNull(testHelper.createWallet(receiverId, RECEIVER_BALANCE));

        TransferEntity transfer = new TransferEntity();
        transfer.setSourceWalletId(sourceWalletId);
        transfer.setDestinationWalletId(destWalletId);
        transfer.setAmount(TRANSFER_AMOUNT);
        transfer.setStatus(TransferStatus.PENDING);
        var response = transferRepository.save(transfer);

        TransferCreatedEvent event = new TransferCreatedEvent(
            response.getId(), sourceWalletId, destWalletId, TRANSFER_AMOUNT
        );
        String payload = objectMapper.writeValueAsString(event);

        OutboxEntity outbox1 = new OutboxEntity();
        outbox1.setAggregateType("transfer");
        outbox1.setAggregateId(response.getId());
        outbox1.setEventType("TRANSFER_CREATED");
        outbox1.setPayload(payload);
        outboxRepository.save(outbox1);

        OutboxEntity outbox2 = new OutboxEntity();
        outbox2.setAggregateType("transfer");
        outbox2.setAggregateId(response.getId());
        outbox2.setEventType("TRANSFER_CREATED");
        outbox2.setPayload(payload);
        outboxRepository.save(outbox2);

        await()
            .atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(300))
            .untilAsserted(() -> {
                TransferEntity t = transferRepository.findById(Objects.requireNonNull(response.getId())).orElseThrow();
                assertThat(t.getStatus()).isEqualTo(TransferStatus.COMPLETED);
            });

        WalletEntity sourceWallet = walletRepository.findById(sourceWalletId).orElseThrow();
        WalletEntity destWallet = walletRepository.findById(destWalletId).orElseThrow();

        assertThat(sourceWallet.getBalance()).isEqualByComparingTo(SENDER_BALANCE.subtract(TRANSFER_AMOUNT));
        assertThat(destWallet.getBalance()).isEqualByComparingTo(RECEIVER_BALANCE.add(TRANSFER_AMOUNT));
    }
}
