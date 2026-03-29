package com.payment.payment_service.wallet.consumer;

import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.payment.payment_service.shared.event.UserCreatedEvent;
import com.payment.payment_service.wallet.service.CreateWalletService;

@ExtendWith(MockitoExtension.class)
class CreateWalletConsumerTest {

    @Mock
    private CreateWalletService createWalletService;

    @InjectMocks
    private CreateWalletConsumer createWalletConsumer;

    @Test
    @DisplayName("should create wallet on UserCreatedEvent")
    void shouldCreateWallet_onUserCreatedEvent() {
        UUID userId = UUID.randomUUID();
        UserCreatedEvent event = new UserCreatedEvent(userId);

        createWalletConsumer.consume(event);

        verify(createWalletService).execute(userId);
    }
}
