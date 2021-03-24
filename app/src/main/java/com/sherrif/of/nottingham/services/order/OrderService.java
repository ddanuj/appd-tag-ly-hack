package com.sherrif.of.nottingham.services.order;

import com.sherrif.of.nottingham.app.ConfigurationUtil;
import com.sherrif.of.nottingham.dto.EquityOrder;
import com.sherrif.of.nottingham.dto.StockQuote;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.BoundLongCounter;
import io.opentelemetry.api.metrics.GlobalMetricsProvider;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.common.Labels;
import io.opentelemetry.api.metrics.common.LabelsBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@RestController
@RequestMapping("/orderService")
public class OrderService {

    // it is important to initialize the OpenTelemetry SDK as early as possible in your application's
    // lifecycle.
    private static final OpenTelemetry openTelemetry = ConfigurationUtil.initOpenTelemetry();
    private static final Meter meter =
            GlobalMetricsProvider.getMeter("io.opentelemetry.example.metrics", "0.13.1");

    @Autowired
    TagGenerator tagGenerator;

    @Autowired
    RestTemplate restTemplate;

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
    // Tell OpenTelemetry to inject the context in the HTTP headers
    TextMapSetter<HttpEntity> setter =
            new TextMapSetter<HttpEntity>() {
                @Override
                public void set(HttpEntity carrier, String key, String value) {
                    // Insert the context as Header
                    carrier.getHeaders().add(key, value);
                }
            };
    Logger logger = LoggerFactory.getLogger(OrderService.class);

    @GetMapping("/getQuote")
    public StockQuote getQuote(@RequestParam(value = "ticker", defaultValue = "$GME") String ticker) {
        long startTime = System.currentTimeMillis();
        StockQuote stockQuote = null;
        // OTel Tracing API
        final Tracer tracer = openTelemetry.getTracer("com.sherrif.of.nottingham.order.service.services.OrderService");
        // Start a span
        Span span = tracer.spanBuilder("orderService/getQuote").setSpanKind(SpanKind.CLIENT).startSpan();

        try (Scope scope = span.makeCurrent()) {
            try {
                // CPM
                BoundLongCounter cpmRecorder = callsPerMinute.bind(Labels.of("stock", ticker));
                cpmRecorder.add(1);
                HttpHeaders headers = new HttpHeaders();
                headers.set("Header", "value");
                headers.set("Other-Header", "othervalue");
                HttpEntity entity = new HttpEntity(headers);
                ResponseEntity<StockQuote> response = restTemplate.exchange(
                        "http://localhost:7071/subscriptionService/subscribe", HttpMethod.GET, entity, StockQuote.class);

                stockQuote = response.getBody();
                openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), entity, setter);
                logger.info(stockQuote.toString());
                // Latency
                BoundLongCounter latencyRecorder = requestLatency.bind(Labels.of("stock", ticker));
                latencyRecorder.add(System.currentTimeMillis() - startTime);
            } catch (Throwable e) {
                // EPM
                BoundLongCounter epmRecorder = errorsPerMinute.bind(Labels.of("stock", ticker));
                epmRecorder.add(1);
                span.setStatus(StatusCode.ERROR, e.getMessage());
            }
        } finally {
            span.end();
        }

        return stockQuote;
    }

    @GetMapping("/equityOrder")
    public EquityOrder equityOrder(@RequestParam EquityOrder order) {
        long startTime = System.currentTimeMillis();
        // CPM
        BoundLongCounter cpmRecorder = callsPerMinute.bind(Labels.of("stock", order.getTicker(), "region", order.getRegion()));
        cpmRecorder.add(1);

        // OTel Tracing API
        final Tracer tracer = openTelemetry.getTracer("com.sherrif.of.nottingham.order.service.services.OrderService");
        // Start a span
        Span span = tracer.spanBuilder("orderService/equityOrder").setSpanKind(SpanKind.CLIENT).startSpan();

        // Set the context with the current span
        try (Scope scope = span.makeCurrent()) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Header", "value");
                headers.set("Other-Header", "othervalue");
                HttpEntity entity = new HttpEntity(headers);
                ResponseEntity<EquityOrder> response = restTemplate.exchange(
                        "http://localhost:7072/orderProcessor/process", HttpMethod.POST, entity, EquityOrder.class, order);

                order = response.getBody();
                openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), entity, setter);
                logger.info(order.toString());
                // Latency
                BoundLongCounter latencyRecorder = requestLatency.bind(Labels.of("stock", order.getTicker(), "region", order.getRegion()));
                latencyRecorder.add(System.currentTimeMillis() - startTime);
            } catch (Throwable e) {
                span.setStatus(StatusCode.ERROR, e.getMessage());
                // EPM
                BoundLongCounter epmRecorder = errorsPerMinute.bind(Labels.of("stock", order.getTicker(), "region", order.getRegion()));
                epmRecorder.add(1);
            }
        } finally {
            span.end();
        }
        return order;
    }

    @GetMapping("/shoutout")
    public void shoutOut(@RequestParam String text) {
        StockQuote stockQuote = null;
        // OTel Tracing API
        final Tracer tracer = openTelemetry.getTracer("com.sherrif.of.nottingham.order.service.services.OrderService");
        // Start a span
        Span span = tracer.spanBuilder("orderService/shoutOut").setSpanKind(SpanKind.CLIENT).startSpan();
        //Generate tag
        List<String> tags = tagGenerator.generateTagsFromUnstructuredInput(text, "ORGANIZATION");
        LabelsBuilder labelsBuilder = Labels.builder();
        // CPM
        for (String tag : tags) {
            labelsBuilder.put("ORGANIZATION", tag);
        }
        Labels labels = labelsBuilder.build();
        BoundLongCounter cpmRecorder = callsPerMinute.bind(labels);
        cpmRecorder.add(1);

        // Set the context with the current span
        try (Scope scope = span.makeCurrent()) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Header", "value");
                headers.set("Other-Header", "othervalue");
                HttpEntity entity = new HttpEntity(headers);
                ResponseEntity<StockQuote> response = restTemplate.exchange(
                        "http://localhost:7071/subscriptionService/subscribe", HttpMethod.GET, entity, StockQuote.class);

                stockQuote = response.getBody();
                openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), entity, setter);
                logger.info(stockQuote.toString());
            } catch (Throwable e) {
                span.setStatus(StatusCode.ERROR, e.getMessage());
            }
        } finally {
            span.end();
        }
    }
}
