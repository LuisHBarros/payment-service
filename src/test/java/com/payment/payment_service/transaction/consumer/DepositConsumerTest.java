package com.payment.payment_service.transaction.consumer;

import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.payment.payment_service.shared.event.DepositCompletedEvent;
import com.payment.payment_service.transaction.service.CreateTransactionService;

@ExtendWith(MockitoExtension.class)
class DepositConsumerTest {

    @Mock
    private CreateTransactionService createTransactionService;

    @InjectMocks
    private DepositConsumer depositConsumer;

    @Test
    @DisplayName("should create credit transaction on DepositCompletedEvent")
    void shouldCreateCreditTransaction_onDepositCompleted() {
        UUID depositId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("200.00");
        DepositCompletedEvent event = new DepositCompletedEvent(
            depositId, walletId, userId, amount, "STRIPE"
        );

        depositConsumer.consume(event);

        verify(createTransactionService).executeCredit(walletId, depositId, amount);
    }
}
