package com.sherrif.of.nottingham.services.order;

import com.sherrif.of.nottingham.app.ConfigurationUtil;
import com.sherrif.of.nottingham.dto.EquityOrder;
import com.sherrif.of.nottingham.dto.StockQuote;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Nullable;
import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/orderService")
public class OrderService {

    Logger logger = LoggerFactory.getLogger(OrderService.class);
    // it is important to initialize the OpenTelemetry SDK as early as possible in your application's
    // lifecycle.
    private static final OpenTelemetry openTelemetry = ConfigurationUtil.initOpenTelemetry();
    private static final Meter meter = ConfigurationUtil
            .initOpenTelemetryMetrics()
            .get("io.opentelemetry.example.metrics", "0.13.1");

    @Autowired
    TagGenerator tagGenerator;

    @Autowired
    RestTemplate restTemplate;

    // Calls per minute
    LongCounter callsPerMinute = meter
            .longCounterBuilder("calls.per.minute")
            .setDescription("Calls Per Minute")
            .setUnit("1").build();
    // Mentions per minute
    LongCounter mentionsPerMinute = meter
            .longCounterBuilder("mentions.per.minute")
            .setDescription("Mentions Per Minute")
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
    TextMapSetter<HttpHeaders> setter = new TextMapSetter<HttpHeaders>() {
        @Override
        public void set(@Nullable HttpHeaders carrier, String key, String value) {
            carrier.set(key, value);
        }
    };

    @GetMapping(value="/getQuote/{ticker}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<StockQuote> getQuote(@PathVariable("ticker") String ticker) {
        long startTime = System.currentTimeMillis();
        StockQuote stockQuote = null;
        // OTel Tracing API
        final Tracer tracer = openTelemetry.getTracer("com.sherrif.of.nottingham.order.service.services.OrderService");
        // Start a span
        Span span = tracer.spanBuilder("orderService/getQuote").setSpanKind(SpanKind.CLIENT).startSpan();
        try (Scope scope = span.makeCurrent()) {
            try {
                span.setAttribute("Good", "true");
                // CPM
                BoundLongCounter cpmRecorder = callsPerMinute.bind(Labels.of("stock", ticker));
                cpmRecorder.add(1);
                HttpHeaders headers = new HttpHeaders();
                headers.set("Header", "value");
                headers.set("Other-Header", "othervalue");
                HttpEntity entity = new HttpEntity(ticker,headers);
                ResponseEntity<StockQuote> response = restTemplate.getForEntity(
                        "http://localhost:7072/subscriptionService/subscribe?ticker=" + ticker, StockQuote.class);

                stockQuote = response.getBody();
                openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), headers, setter);
                logger.info("Stock quote {}", stockQuote.toString());
                // Latency
                BoundLongCounter latencyRecorder = requestLatency.bind(Labels.of("stock", ticker));
                latencyRecorder.add(System.currentTimeMillis() - startTime);
            } catch (Throwable e) {
                // EPM
                logger.error("traceId {} - Exception during the /placeOrder with the exception {}", span.getSpanContext().getTraceId(), String.valueOf(e));
                BoundLongCounter epmRecorder = errorsPerMinute.bind(Labels.of("stock", ticker));
                epmRecorder.add(1);
                span.setAttribute("Stack trace", String.valueOf(e.getStackTrace()));
                span.setStatus(StatusCode.ERROR, String.valueOf(e.getStackTrace()));
            }
        } finally {
            span.end();
        }

        return ResponseEntity.ok(stockQuote);
    }

    @PostMapping(path = "/placeOrder", consumes = "application/json", produces = "application/json")
    public ResponseEntity<EquityOrder> placeOrder(@RequestBody EquityOrder order) {
        long startTime = System.currentTimeMillis();
        // CPM
        BoundLongCounter cpmRecorder = callsPerMinute.bind(Labels.of("stock", order.getTicker(), "region", order.getRegion()));
        cpmRecorder.add(1);

        // OTel Tracing API
        final Tracer tracer = openTelemetry.getTracer("com.sherrif.of.nottingham.order.service.services.OrderService");
        // Start a span
        Span span = tracer.spanBuilder("orderService/placeOrder").setSpanKind(SpanKind.SERVER).startSpan();
        span.setAttribute("tags.stock",order.getTicker());
        span.setAttribute("tags.region",order.getRegion());

        // Set the context with the current span
        EquityOrder equityOrder;
        try (Scope scope = span.makeCurrent()) {
            try {
                logger.info("Calling downstream with order = " + order);
                equityOrder = downstreamCall(order, tracer);
                // Latency
                BoundLongCounter latencyRecorder = requestLatency.bind(Labels.of("stock", order.getTicker(), "region", order.getRegion()));
                latencyRecorder.add(System.currentTimeMillis() - startTime);
            } catch (Throwable e) {
                logger.error("Exception during the /placeOrder with the exception {}", String.valueOf(e), e);
                span.setAttribute("Stack trace", String.valueOf(e.getStackTrace()));
                span.setStatus(StatusCode.ERROR, String.valueOf(e.getStackTrace()));
                // EPM
                BoundLongCounter epmRecorder = errorsPerMinute.bind(Labels.of("stock", order.getTicker(), "region", order.getRegion()));
                epmRecorder.add(1);
                return ResponseEntity.badRequest().build();
            }
        } finally {
            span.end();
        }
        return ResponseEntity.ok(equityOrder);
    }

    private EquityOrder downstreamCall(EquityOrder order, Tracer tracer) {
        Span downstreamCallSpan =
                tracer
                        .spanBuilder("orderProcessor/process/outgoingCall")
                        .setSpanKind(SpanKind.CLIENT)
                        .startSpan();
        downstreamCallSpan.setAttribute("tags.stock",order.getTicker());
        downstreamCallSpan.setAttribute("tags.region",order.getRegion());

        try (Scope scope = downstreamCallSpan.makeCurrent()) {

            HttpHeaders headers = new HttpHeaders();
            headers.set("Header1", "value1");
            headers.set("Other-Header", "othervalue");
            HttpEntity entity = new HttpEntity(order, headers);

            openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), headers, setter);
            ResponseEntity<EquityOrder> response = restTemplate.postForEntity(
                    "http://order-processor:7071/orderProcessor/process", entity, EquityOrder.class);

            order = response.getBody();
            logger.info("Order processed {}", order.toString());
        } finally {
            downstreamCallSpan.end();
        }
        return order;
    }

    @PostMapping("/shoutout")
    public ResponseEntity<Object> shoutOut(@RequestBody String text) {
        StockQuote stockQuote = null;
        // OTel Tracing API
        final Tracer tracer = openTelemetry.getTracer("com.sherrif.of.nottingham.order.service.services.OrderService");
        // Start a span
        Span span = tracer.spanBuilder("orderService/shoutOut").setSpanKind(SpanKind.CLIENT).startSpan();
        //Generate tag
        List<String> tags = tagGenerator.generateTagsFromUnstructuredInput(text, "ORGANIZATION");
        span.setAttribute("intelli.tags",tags.toString());

        LabelsBuilder labelsBuilder = Labels.builder();
        // CPM
        for (String tag : tags) {
            labelsBuilder.put("ORGANIZATION", tag);
        }
        Labels labels = labelsBuilder.build();
        BoundLongCounter cpmRecorder = mentionsPerMinute.bind(labels);
        cpmRecorder.add(new Double(Math.random() * 1000).longValue());

        // Set the context with the current span
        try (Scope scope = span.makeCurrent()) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Header", "value");
                headers.set("Other-Header", "othervalue");
                HttpEntity entity = new HttpEntity(headers);
                openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), headers, setter);
                ResponseEntity<StockQuote> response = restTemplate.exchange(
                        "http://localhost:7071/subscriptionService/subscribe", HttpMethod.GET, entity, StockQuote.class);

                stockQuote = response.getBody();
                logger.info(stockQuote.toString());
            } catch (Throwable e) {
                span.setStatus(StatusCode.ERROR, e.getMessage());
            }
        } finally {
            span.end();
        }
        return ResponseEntity.noContent().build();
    }
}
