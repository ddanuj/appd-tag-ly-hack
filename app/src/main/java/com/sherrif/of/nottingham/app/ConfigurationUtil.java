package com.sherrif.of.nottingham.app;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

public class ConfigurationUtil {

    public static OpenTelemetry initOpenTelemetry() {
        Logger logger = LoggerFactory.getLogger(ConfigurationUtil.class);

        // Set to process the spans with the LoggingSpanExporter
        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(OtlpGrpcSpanExporter.builder().setEndpoint("http://localhost:4317").build()).build())
                .setResource(OpenTelemetrySdkAutoConfiguration.getResource())
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
                                   logger.info(
                                            "*** forcing the Span Exporter to shutdown and process the remaining spans");
                                    sdkTracerProvider.shutdown();
                                    System.err.println("*** Trace Exporter shut down");
                                }));

        return openTelemetry;
    }

    /**
     * Initializes a Metrics SDK with a OtlpGrpcMetricExporter and an IntervalMetricReader.
     *
     * @return a ready-to-use {@link MeterProvider} instance
     */
    public static MeterProvider initOpenTelemetryMetrics() {
        // set up the metric exporter and wire it into the SDK and a timed reader.
        OtlpGrpcMetricExporter metricExporter = OtlpGrpcMetricExporter.builder()
                .setEndpoint("http://localhost:4317").build();

        SdkMeterProvider meterProvider = SdkMeterProvider.builder().buildAndRegisterGlobal();
        IntervalMetricReader intervalMetricReader =
                IntervalMetricReader.builder()
                        .setMetricExporter(metricExporter)
                        .setMetricProducers(Collections.singleton(meterProvider))
                        .setExportIntervalMillis(1000)
                        .build();

        Runtime.getRuntime().addShutdownHook(new Thread(intervalMetricReader::shutdown));

        return meterProvider;
    }

}
