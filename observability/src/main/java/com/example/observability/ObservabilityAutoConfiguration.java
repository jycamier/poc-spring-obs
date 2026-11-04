package com.example.observability;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.BaggageTaggingSpanProcessor;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.trace.SpanProcessor;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ObservabilityAutoConfiguration {

    /**
     * Connects the OTEL appender declared in logback-spring.xml to the OpenTelemetry SDK built by Spring Boot.
     */
    @Bean
    InitializingBean openTelemetryAppenderInstaller(OpenTelemetry openTelemetry) {
        return () -> OpenTelemetryAppender.install(openTelemetry);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    BaggageServerSpanTagger baggageServerSpanTagger(Tracer tracer,
                                                    @Value("${management.tracing.baggage.tag-fields}") List<String> fields) {
        return new BaggageServerSpanTagger(tracer, fields);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    ServerErrorRecorder serverErrorRecorder(ObservationRegistry observationRegistry) {
        return new ServerErrorRecorder(observationRegistry);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    UnhandledExceptionResolver unhandledExceptionResolver() {
        return new UnhandledExceptionResolver();
    }

    /**
     * {@code management.tracing.baggage.tag-fields} only tags the span that is current when the baggage is created.
     * This processor copies the baggage onto every span started afterwards (client calls, custom spans...).
     */
    @Bean
    SpanProcessor baggageTaggingSpanProcessor(@Value("${management.tracing.baggage.tag-fields}") List<String> fields) {
        return new BaggageTaggingSpanProcessor(fields);
    }
}
