/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package com.appdynamics.tagly;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.*;
import io.opentelemetry.api.metrics.common.Labels;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class App {
    public static final Logger LOGGER = LoggerFactory.getLogger(App.class);
    public String getGreeting() {return "Hello World";}
    public static void main(String[] args) throws InterruptedException {
        // this will make sure that a proper service.name attribute is set on all the spans/metrics.
        // note: this is not something you should generally do in code, but should be provided on the
        // command-line. This is here to make the example more self-contained.
        System.setProperty("otel.resource.attributes", "service.name=OtlpExporterExample");

        // it is important to initialize your SDK as early as possible in your application's lifecycle
        OpenTelemetry openTelemetry = Configuration.initOpenTelemetry();
        // note: currently metrics is alpha and the configuration story is still unfolding. This will
        // definitely change in the future.
        MeterProvider meterProvider = Configuration.initOpenTelemetryMetrics();

        Tracer tracer = openTelemetry.getTracer("io.opentelemetry.example");

        Meter meter = meterProvider.get("io.opentelemetry.example");

        // Calls per minute
        LongCounter callsPerMinute = meter
                .longCounterBuilder("calls.per.minute")
                .setDescription("Calls Per Minute")
                .setUnit("1").build();

        // Errors per minute
        LongCounter errorsPerMinute = meter
                .longCounterBuilder("errors.per.minute")
                .setDescription("Errors Per Minute")
                .setUnit("1").build();

        // Request Latency
        LongCounter requestLatency = meter
                .longCounterBuilder("request.latency.ms")
                .setDescription("Latency in ms")
                .setUnit("ms").build();

        Random rand = new Random();
        int cnt = 0;
        while (true) {
            Span parentSpan = tracer.spanBuilder("parentSpan").startSpan();
            try (Scope ignored = parentSpan.makeCurrent()) {
                String stock = getStock(cnt);
                String region = getRegion(cnt);

                // CPM
                BoundLongCounter cpmRecorder = callsPerMinute.bind(Labels.of("stock", stock, "region", region));
                cpmRecorder.add(getCallsPerMinute(stock, region));

                // EPM
                BoundLongCounter epmRecorder = errorsPerMinute.bind(Labels.of("stock", stock, "region", region));
                epmRecorder.add(getErrorsPerMinute(stock, region));

                // Latency
                BoundLongCounter latencyRecorder = requestLatency.bind(Labels.of("stock", stock, "region", region));
                latencyRecorder.add(getLatency(stock, region));

                parentSpan.setAttribute("good", "true");
                parentSpan.setAttribute("exampleNumber", rand.nextInt());
            } finally {
                parentSpan.end();
                LOGGER.info("Reported Span: " + parentSpan);
            }
            Thread.sleep(60000);
            cnt ++;
        }

    }

    static String getStock(int n) {
        int m = new Double(Math.random() * n * 100).intValue();
        if (m % 15 == 0) return "Grayberry";
        else if (m % 5 == 0) return "Anc";
        else if (m % 2 == 0) return "Gamestart";
        else return "Yeskia";
    }

    static String getRegion(int n) {
        if (n % 15 == 0) return "Africa";
        else if (n % 5 == 0) return "Asia";
        else if (n % 2 == 0) return "North America";
        else return "Europe";
    }

    static Long getCallsPerMinute(String stock, String region) {
        if (stock.equals("Gamestart") && region.equals("North America")) return 1000L;
        else if (stock.equals("Gamestart") && region.equals("Europe")) return 500L;
        else if (stock.equals("Gamestart") && region.equals("Asia")) return 250L;
        else if (stock.equals("Gamestart") && region.equals("Africa")) return 100L;
        else if (region.equals("North America")) return new Double(Math.random() * 1000).longValue();
        else if (region.equals("Europe")) return new Double(Math.random() * 500).longValue();
        else if (region.equals("Asia")) return new Double(Math.random() * 250).longValue();
        else if (region.equals("Africa")) return new Double(Math.random() * 100).longValue();
        else return 1000L;
    }

    static Long getLatency(String stock, String region) {
        if (stock.equals("Gamestart") && region.equals("North America")) return 10L;
        else if (stock.equals("Gamestart") && region.equals("Europe")) return 5L;
        else if (stock.equals("Gamestart") && region.equals("Asia")) return 250L;
        else if (stock.equals("Gamestart") && region.equals("Africa")) return 1000L;
        else if (region.equals("North America")) return new Double(Math.random() * 10).longValue();
        else if (region.equals("Europe")) return new Double(Math.random() * 5).longValue();
        else if (region.equals("Asia")) return new Double(Math.random() * 250).longValue();
        else if (region.equals("Africa")) return new Double(Math.random() * 1000).longValue();
        else return 10L;
    }

    static Long getErrorsPerMinute(String stock, String region) {
        if (stock.equals("Gamestart") && region.equals("North America")) return 1000L;
        else if (stock.equals("Gamestart") && region.equals("Europe")) return 500L;
        else if (stock.equals("Gamestart") && region.equals("Asia")) return 250L;
        else if (stock.equals("Gamestart") && region.equals("Africa")) return 100L;
        else if (stock.equals("Yeskia") && region.equals("North America")) return 500L;
        else if (stock.equals("Yeskia") && region.equals("Europe")) return 250L;
        else if (stock.equals("Yeskia") && region.equals("Asia")) return 100L;
        else if (stock.equals("Yeskia") && region.equals("Africa")) return 50L;
        else if (region.equals("North America")) return new Double(Math.random() * 1000).longValue();
        else if (region.equals("Europe")) return new Double(Math.random() * 500).longValue();
        else if (region.equals("Asia")) return new Double(Math.random() * 250).longValue();
        else if (region.equals("Africa")) return new Double(Math.random() * 100).longValue();
        else return 10L;
    }
}
