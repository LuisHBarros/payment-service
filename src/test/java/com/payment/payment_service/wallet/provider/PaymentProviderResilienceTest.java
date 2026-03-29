package com.payment.payment_service.wallet.provider;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.payment.payment_service.wallet.exception.InvalidPaymentProviderException;
import com.payment.payment_service.wallet.exception.PaymentProviderException;
import com.payment.payment_service.wallet.exception.WebhookSignatureException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

class PaymentProviderResilienceTest {

    @Test
    void retry_shouldRetryThreeTimes_onPaymentProviderException() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Retry retry = Retry.of("paymentProvider", RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(50))
            .retryOnException(e -> e instanceof PaymentProviderException)
            .failAfterMaxAttempts(true)
            .build());

        assertThrows(PaymentProviderException.class, () ->
            Retry.decorateSupplier(retry, () -> {
                attemptCount.incrementAndGet();
                throw new PaymentProviderException("test", null);
            }).get());

        assertEquals(3, attemptCount.get());
    }

    @Test
    void retry_shouldNotRetry_onWebhookSignatureException() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Retry retry = Retry.of("paymentProvider", RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(50))
            .retryOnException(e -> e instanceof PaymentProviderException)
            .ignoreExceptions(WebhookSignatureException.class)
            .build());

        assertThrows(WebhookSignatureException.class, () ->
            Retry.decorateSupplier(retry, () -> {
                attemptCount.incrementAndGet();
                throw new WebhookSignatureException("test");
            }).get());

        assertEquals(1, attemptCount.get());
    }

    @Test
    void retry_shouldNotRetry_onInvalidPaymentProviderException() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Retry retry = Retry.of("paymentProvider", RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(50))
            .retryOnException(e -> e instanceof PaymentProviderException)
            .ignoreExceptions(InvalidPaymentProviderException.class)
            .build());

        assertThrows(InvalidPaymentProviderException.class, () ->
            Retry.decorateSupplier(retry, () -> {
                attemptCount.incrementAndGet();
                throw new InvalidPaymentProviderException("test");
            }).get());

        assertEquals(1, attemptCount.get());
    }

    @Test
    void circuitBreaker_shouldOpen_afterFailureThreshold() {
        CircuitBreaker cb = CircuitBreaker.of("paymentProvider", CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .minimumNumberOfCalls(2)
            .slidingWindowType(SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(2)
            .waitDurationInOpenState(Duration.ofMillis(200))
            .build());

        try { CircuitBreaker.decorateSupplier(cb,
            () -> { throw new PaymentProviderException("test", null); }).get(); } catch (Exception ignored) {}

        try { CircuitBreaker.decorateSupplier(cb,
            () -> { throw new PaymentProviderException("test", null); }).get(); } catch (Exception ignored) {}

        assertEquals(State.OPEN, cb.getState());

        assertThrows(CallNotPermittedException.class, () ->
            CircuitBreaker.decorateSupplier(cb, () -> "ok").get());
    }

    @Test
    void circuitBreaker_shouldHalfOpen_afterWaitDuration() throws InterruptedException {
        CircuitBreaker cb = CircuitBreaker.of("paymentProvider", CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .minimumNumberOfCalls(2)
            .slidingWindowType(SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(2)
            .waitDurationInOpenState(Duration.ofMillis(100))
            .permittedNumberOfCallsInHalfOpenState(1)
            .build());

        try { CircuitBreaker.decorateSupplier(cb,
            () -> { throw new PaymentProviderException("test", null); }).get(); } catch (Exception ignored) {}
        try { CircuitBreaker.decorateSupplier(cb,
            () -> { throw new PaymentProviderException("test", null); }).get(); } catch (Exception ignored) {}

        assertEquals(State.OPEN, cb.getState());

        Thread.sleep(200);

        CircuitBreaker.decorateSupplier(cb, () -> "ok").get();
        assertEquals(State.CLOSED, cb.getState());
    }
}
