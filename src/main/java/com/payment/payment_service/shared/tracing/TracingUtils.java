package com.payment.payment_service.shared.tracing;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Utilitários para criação e gerenciamento de spans de tracing.
 * Facilita a instrumentação de métodos de serviço com spans customizados.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TracingUtils {

    private final Tracer tracer;

    /**
     * Executa uma operação dentro de um novo span de tracing.
     * O span é automaticamente finalizado após a execução.
     *
     * @param spanName Nome do span
     * @param operation Operação a ser executada
     * @param <T> Tipo de retorno
     * @return Resultado da operação
     */
    public <T> T withSpan(String spanName, Supplier<T> operation) {
        Span span = tracer.nextSpan().name(spanName).start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            return operation.get();
        } catch (Exception e) {
            span.tag("error", "true");
            span.tag("error.message", e.getMessage());
            span.event("Exception: " + e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Executa uma operação void dentro de um novo span de tracing.
     */
    public void withSpan(String spanName, Runnable operation) {
        Span span = tracer.nextSpan().name(spanName).start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            operation.run();
        } catch (Exception e) {
            span.tag("error", "true");
            span.tag("error.message", e.getMessage());
            span.event("Exception: " + e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Cria um span filho do span atual.
     */
    public Span createChildSpan(String spanName) {
        return tracer.nextSpan().name(spanName).start();
    }

    /**
     * Adiciona tags ao span atual.
     */
    public void tagCurrentSpan(String key, String value) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.tag(key, value);
        }
    }

    /**
     * Adiciona evento ao span atual.
     */
    public void eventCurrentSpan(String event) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.event(event);
        }
    }

    /**
     * Retorna o trace ID atual ou "unknown" se não houver contexto.
     */
    public String currentTraceId() {
        Span currentSpan = tracer.currentSpan();
        return currentSpan != null ? currentSpan.context().traceId() : "unknown";
    }

    /**
     * Retorna o span ID atual ou "unknown" se não houver contexto.
     */
    public String currentSpanId() {
        Span currentSpan = tracer.currentSpan();
        return currentSpan != null ? currentSpan.context().spanId() : "unknown";
    }
}
