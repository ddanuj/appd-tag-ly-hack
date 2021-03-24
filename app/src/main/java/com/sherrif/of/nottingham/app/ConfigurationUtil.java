package com.sherrif.of.nottingham.app;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

public class ConfigurationUtil {

    public static OpenTelemetry initOpenTelemetry() {

        // Set to process the spans with the LoggingSpanExporter
        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(OtlpGrpcSpanExporter.builder().setEndpoint("http://otel-agent:4317").build()).build())
                .build();

        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();

        // it's always a good idea to shutdown the SDK when your process exits.
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    System.err.println(
                                            "*** forcing the Span Exporter to shutdown and process the remaining spans");
                                    sdkTracerProvider.shutdown();
                                    System.err.println("*** Trace Exporter shut down");
                                }));

        return openTelemetry;
    }

}
