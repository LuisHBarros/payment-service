package com.payment.payment_service.shared.tracing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.extension.trace.propagation.B3Propagator;

import org.springframework.beans.factory.annotation.Value;

/**
 * Configuração do OpenTelemetry para tracing distribuído.
 * Integra com Micrometer Tracing para propagação de contexto entre serviços.
 */
@Configuration
public class OtelConfig {

    @Value("${spring.application.name:payment-service}")
    private String serviceName;

    @Value("${management.otlp.tracing.endpoint:http://localhost:4317}")
    private String otlpEndpoint;

    /**
     * Configura o SDK do OpenTelemetry com exportador OTLP para Jaeger/Tempo.
     */
    @Bean
    public OpenTelemetry openTelemetry() {
        Resource resource = Resource.getDefault()
            .merge(Resource.create(Attributes.of(
                AttributeKey.stringKey("service.name"), serviceName,
                AttributeKey.stringKey("service.version"), "1.0.0",
                AttributeKey.stringKey("deployment.environment"), "production"
            )));

        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint(otlpEndpoint)
            .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .setResource(resource)
            .build();

        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(
                TextMapPropagator.composite(
                    io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator.getInstance(),
                    B3Propagator.injectingSingleHeader()
                )
            ))
            .build();
    }

    /**
     * Cria o ObservationHandler para Micrometer Tracing.
     */
    @Bean
    public DefaultTracingObservationHandler defaultTracingObservationHandler(Tracer tracer) {
        return new DefaultTracingObservationHandler(tracer);
    }

    /**
     * Aspect para observação automática de métodos anotados com @Observed.
     */
    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }
}
