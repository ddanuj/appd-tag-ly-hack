package com.sherrif.of.nottingham.services.processor;

import com.sherrif.of.nottingham.app.ConfigurationUtil;
import com.sherrif.of.nottingham.dto.EquityOrder;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
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
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/orderProcessor")
public class OrderProcessorService {
    // it is important to initialize the OpenTelemetry SDK as early as possible in your application's
    // lifecycle.
    private static final OpenTelemetry openTelemetry = ConfigurationUtil.initOpenTelemetry();

    Logger logger = LoggerFactory.getLogger(OrderProcessorService.class);
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


    @PostMapping(path = "/process", consumes = "application/json", produces = "application/json")
    public @ResponseBody ResponseEntity<EquityOrder> process(@RequestBody EquityOrder order, @RequestHeader MultiValueMap<String, String> headers) {
        logger.info("/orderProcessor service requested");
        // OTel Tracing API
        final Tracer tracer =
                openTelemetry.getTracer("com.sherrif.of.nottingham.order.service.services.OrderProcessorService");
        HttpEntity entity = new HttpEntity(order,headers);

        Context extractedContext = openTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.current(), entity, getter);

        // Build a span based on the received context
        Span span =
                tracer
                        .spanBuilder("orderProcessor/process")
                        .setParent(extractedContext)
                        .setSpanKind(SpanKind.SERVER)
                        .startSpan();
        span.setAttribute("tags.stock",order.getTicker());
        span.setAttribute("tags.region",order.getRegion());
        try {
            handleError(order.getTicker(), span);
        } catch (Exception e) {
            logger.error("traceId {} - Random exception during the orderProcessor/process of : {} for region {}", span.getSpanContext().getTraceId(), order.getTicker(), order.getRegion());
            Attributes attributes = Attributes.of(AttributeKey.stringKey("stack-trace"), String.valueOf(e));
            span.addEvent("Order Processor Stack trace", attributes);
            span.setStatus(StatusCode.ERROR, e.getMessage());
        }
        if(order.isErrorFlag()) {
            logger.error("traceId {} - Exception during the /placeOrder due to an input error", span.getSpanContext().getTraceId());
            Attributes attributes = Attributes.of(AttributeKey.stringKey("stack-trace"), String.valueOf(Thread.getAllStackTraces()));
            span.addEvent("Order Processor Stack trace", attributes);
            span.setStatus(StatusCode.ERROR, "Exception during the /placeOrder due to an input error");
        }

        // Set the context with the current span
        try (Scope scope = span.makeCurrent()) {
            try {
                logger.info("created order processor span with id {}", span.getSpanContext());
                Random random = new Random();
                order.setOrderId(random.nextInt(10000));
            } catch (Throwable e) {
                logger.error("traceId {} - Exception during the /process with the exception {}", span.getSpanContext().getTraceId(), String.valueOf(e));
                span.setAttribute("Stack trace", String.valueOf(e.getStackTrace()));
                span.setStatus(StatusCode.ERROR, String.valueOf(e.getStackTrace()));
            }
        } finally {
            span.end();
        }
        return ResponseEntity.ok(order);
    }

    @GetMapping("/greeting")
    public String greeting() {
        logger.info("/client service requested");
        return "success";
    }

    private void handleError(String ticker, Span span) throws Exception {
        Random random = new Random();
        if(random.nextInt(10)>5) {
            logger.error("traceId {} - Random exception during the transaction processing of : {}", span.getSpanContext().getTraceId(), ticker);
            throw new Exception(String.format("Random exception during the transaction processing of : %s", ticker));
        }
    }
}
