package com.sherrif.of.nottingham.services.processor;

import com.sherrif.of.nottingham.app.ConfigurationUtil;
import com.sherrif.of.nottingham.dto.EquityOrder;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.BoundLongCounter;
import io.opentelemetry.api.metrics.common.Labels;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;

@RestController
@RequestMapping("/orderProcessor")
public class OrderProcessorService {
    Logger logger = LoggerFactory.getLogger(OrderProcessorService.class);
    // it is important to initialize the OpenTelemetry SDK as early as possible in your application's
    // lifecycle.
    private static final OpenTelemetry openTelemetry = ConfigurationUtil.initOpenTelemetry();
    private final TextMapPropagator textFormat =
            openTelemetry.getPropagators().getTextMapPropagator();

    TextMapGetter<HttpEntity> getter =
            new TextMapGetter<>() {
                @Override
                public String get(HttpEntity carrier, String key) {
                    if (carrier.getHeaders().containsKey(key)) {
                        return carrier.getHeaders().get(key).get(0);
                    }
                    return null;
                }

                @Override
                public Iterable<String> keys(HttpEntity carrier) {
                    return carrier.getHeaders().keySet();
                }
            };
    private EquityOrder equityOrder = new EquityOrder();

    public OrderProcessorService() {

    }

    @PostMapping(path = "/process", consumes = "application/json", produces = "application/json")
    public ResponseEntity<EquityOrder> processOrder(@RequestBody EquityOrder order) {
        logger.info("/orderProcessor service requested");
        // OTel Tracing API
        final Tracer tracer =
                openTelemetry.getTracer("com.sherrif.of.nottingham.order.service.services.OrderProcessorService");

        Context extractedContext = openTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.current(), null, getter);

        // Build a span based on the received context
        Span span =
                tracer
                        .spanBuilder("orderProcessor/process")
                        .setParent(extractedContext)
                        .setSpanKind(SpanKind.SERVER)
                        .startSpan();

        // Set the context with the current span
        try (Scope scope = span.makeCurrent()) {
            try {
                logger.info("created order processor span with id {}", span.getSpanContext());
                Random random = new Random();
                equityOrder.setOrderId(random.nextInt(10000));
            } catch (Throwable e) {
                logger.error("Exception during the /process with the exception {}", String.valueOf(e));
                span.setAttribute("Stack trace", String.valueOf(e.getStackTrace()));
                span.setStatus(StatusCode.ERROR, String.valueOf(e.getStackTrace()));
            }
        } finally {
            span.end();
        }
        return ResponseEntity.ok(equityOrder);
    }
}
