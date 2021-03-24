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

    @GetMapping(value="/getQuote/{ticker}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<StockQuote> getQuote(@PathVariable("ticker") String ticker) {
        long startTime = System.currentTimeMillis();
        StockQuote stockQuote = null;
        // OTel Tracing API
        final Tracer tracer = openTelemetry.getTracer("com.sherrif.of.nottingham.order.service.services.OrderService");
        // Start a span
        Span span = tracer.spanBuilder("orderService/getQuote").setSpanKind(SpanKind.CLIENT).startSpan();
        try {
            handleError(ticker);
        } catch (Exception e) {
            logger.error("Random exception during the /getQuote of : {} for region {}", ticker);
            span.setAttribute("Stack trace", String.valueOf(e.getStackTrace()));
            span.setStatus(StatusCode.ERROR, e.getMessage());
        }
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
                ResponseEntity<StockQuote> response = restTemplate.postForEntity(
                        "http://localhost:7072/subscriptionService/subscribe", entity, StockQuote.class);

                stockQuote = response.getBody();
                openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), entity, setter);
                logger.info("Stock quote {}", stockQuote.toString());
                // Latency
                BoundLongCounter latencyRecorder = requestLatency.bind(Labels.of("stock", ticker));
                latencyRecorder.add(System.currentTimeMillis() - startTime);
            } catch (Throwable e) {
                // EPM
                logger.error("Exception during the /equityOrder with the exception {}", String.valueOf(e));
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

    @PostMapping(path = "/equityOrder", consumes = "application/json", produces = "application/json")
    public ResponseEntity<EquityOrder> equityOrder(@RequestBody EquityOrder order) {
        long startTime = System.currentTimeMillis();
        // CPM
        BoundLongCounter cpmRecorder = callsPerMinute.bind(Labels.of("stock", order.getTicker(), "region", order.getRegion()));
        cpmRecorder.add(1);

        // OTel Tracing API
        final Tracer tracer = openTelemetry.getTracer("com.sherrif.of.nottingham.order.service.services.OrderService");
        // Start a span
        Span span = tracer.spanBuilder("orderService/equityOrder").setSpanKind(SpanKind.CLIENT).startSpan();
        if(order.isErrorFlag()) {
            logger.error("Exception during the /equityOrder due to an input error");
            span.setAttribute("Stack trace", "Exception during the /equityOrder due to an input error");
            span.setStatus(StatusCode.ERROR, "Exception during the /equityOrder due to an input error");
        }
        try {
            handleError(order.getTicker());
        } catch (Exception e) {
            logger.error("Random exception during the /equityOrder of : {} for region {}", order.getTicker(), order.getRegion());
            span.setAttribute("Stack trace", String.valueOf(e.getStackTrace()));
            span.setStatus(StatusCode.ERROR, String.valueOf(e.getStackTrace()));
        }

        // Set the context with the current span
        try (Scope scope = span.makeCurrent()) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Good", "true");
                headers.set("Other-Header", "othervalue");
                HttpEntity entity = new HttpEntity(order,headers);

                ResponseEntity<EquityOrder> response = restTemplate.postForEntity(
                        "http://localhost:7071/orderProcessor/process", entity, EquityOrder.class);

                order = response.getBody();
                openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), entity, setter);
                logger.info("Order processed {}", order.toString());
                // Latency
                BoundLongCounter latencyRecorder = requestLatency.bind(Labels.of("stock", order.getTicker(), "region", order.getRegion()));
                latencyRecorder.add(System.currentTimeMillis() - startTime);
            } catch (Throwable e) {
                logger.error("Exception during the /equityOrder with the exception {}", String.valueOf(e));
                span.setAttribute("Stack trace", String.valueOf(e.getStackTrace()));
                span.setStatus(StatusCode.ERROR, String.valueOf(e.getStackTrace()));
                // EPM
                BoundLongCounter epmRecorder = errorsPerMinute.bind(Labels.of("stock", order.getTicker(), "region", order.getRegion()));
                epmRecorder.add(1);
            }
        } finally {
            span.end();
        }
        return ResponseEntity.ok(order);
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
        return ResponseEntity.noContent().build();
    }

    private void handleError(String ticker) throws Exception {
        Random random = new Random();
        if(random.nextInt(10)>5) {
            logger.error("Random exception during the transaction processing of : {}", ticker);
            throw new Exception(String.format("Random exception during the transaction processing of : %s", ticker));
        }
    }
}
