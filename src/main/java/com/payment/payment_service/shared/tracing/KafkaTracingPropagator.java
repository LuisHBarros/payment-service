package com.payment.payment_service.shared.tracing;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.stereotype.Component;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Propagador de contexto de tracing para eventos Kafka.
 * Injeta e extrai trace context dos headers das mensagens Kafka.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaTracingPropagator {

    private final Tracer tracer;

    // Headers W3C trace context
    private static final String TRACEPARENT_HEADER = "traceparent";
    // Headers B3
    private static final String B3_TRACE_ID = "X-B3-TraceId";
    private static final String B3_SPAN_ID = "X-B3-SpanId";
    private static final String B3_SAMPLED = "X-B3-Sampled";

    /**
     * Injeta o contexto de trace atual nos headers do Kafka.
     */
    public void injectTraceContext(Headers headers) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan == null) {
            return;
        }

        // W3C traceparent
        String traceId = currentSpan.context().traceId();
        String spanId = currentSpan.context().spanId();
        String traceFlags = currentSpan.context().sampled() ? "01" : "00";
        String traceparent = String.format("00-%s-%s-%s", traceId, spanId, traceFlags);

        headers.add(new RecordHeader(TRACEPARENT_HEADER, traceparent.getBytes(StandardCharsets.UTF_8)));

        // B3 headers (para compatibilidade)
        headers.add(new RecordHeader(B3_TRACE_ID, traceId.getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader(B3_SPAN_ID, spanId.getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader(B3_SAMPLED, currentSpan.context().sampled() ? "1".getBytes(StandardCharsets.UTF_8) : "0".getBytes(StandardCharsets.UTF_8)));

        log.debug("Injected trace context: traceId={}, spanId={}", traceId, spanId);
    }

    /**
     * Extrai o trace context dos headers do Kafka e cria um novo span filho.
     * Retorna null se não encontrar contexto de trace.
     */
    public Span extractAndCreateSpan(Headers headers, String spanName) {
        Header traceparentHeader = headers.lastHeader(TRACEPARENT_HEADER);

        if (traceparentHeader == null) {
            // Tentar B3
            return extractB3AndCreateSpan(headers, spanName);
        }

        String traceparent = new String(traceparentHeader.value(), StandardCharsets.UTF_8);
        String[] parts = traceparent.split("-");

        if (parts.length != 4) {
            log.warn("Invalid traceparent format: {}", traceparent);
            return null;
        }

        String traceId = parts[1];
        String parentSpanId = parts[2];
        boolean sampled = "01".equals(parts[3]);

        Span childSpan = tracer.nextSpan()
            .name(spanName)
            .tag("kafka.consumed", "true")
            .tag("traceId", traceId)
            .tag("parentSpanId", parentSpanId);

        if (!sampled) {
            childSpan.tag("sampled", "false");
        }

        log.debug("Extracted W3C trace context: traceId={}, parentSpanId={}", traceId, parentSpanId);
        return childSpan.start();
    }

    private Span extractB3AndCreateSpan(Headers headers, String spanName) {
        Header traceIdHeader = headers.lastHeader(B3_TRACE_ID);
        Header spanIdHeader = headers.lastHeader(B3_SPAN_ID);

        if (traceIdHeader == null || spanIdHeader == null) {
            return null;
        }

        String traceId = new String(traceIdHeader.value(), StandardCharsets.UTF_8);
        String parentSpanId = new String(spanIdHeader.value(), StandardCharsets.UTF_8);

        Span childSpan = tracer.nextSpan()
            .name(spanName)
            .tag("kafka.consumed", "true")
            .tag("traceId", traceId)
            .tag("parentSpanId", parentSpanId);

        log.debug("Extracted B3 trace context: traceId={}, parentSpanId={}", traceId, parentSpanId);
        return childSpan.start();
    }

    /**
     * Cria um novo span root quando não há contexto de trace nos headers.
     */
    public Span createNewSpan(String spanName) {
        return tracer.nextSpan()
            .name(spanName)
            .tag("kafka.consumed", "true")
            .tag("trace.root", "true")
            .start();
    }
}
