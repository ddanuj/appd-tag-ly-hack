package com.sherrif.of.nottingham.services.subscription;

import com.sherrif.of.nottingham.app.ConfigurationUtil;
import com.sherrif.of.nottingham.app.OrderServiceApplication;
import com.sherrif.of.nottingham.dto.StockQuote;
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
import org.apache.coyote.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;

@RestController
@RequestMapping("/subscriptionService")
public class SubscriptionService {

    // it is important to initialize the OpenTelemetry SDK as early as possible in your application's
    // lifecycle.
    private static final OpenTelemetry openTelemetry = ConfigurationUtil.initOpenTelemetry();

    Logger logger = LoggerFactory.getLogger(SubscriptionService.class);
    private StockQuote stockQuote = new StockQuote();

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

    public SubscriptionService() {
        stockQuote.setTicker("GME");
        stockQuote.setPrice(225);
        stockQuote.setTimestampInMillis(System.currentTimeMillis());
    }

    @GetMapping(path = "/subscribe")
    public ResponseEntity<StockQuote> subscribeQuote(@RequestParam(value = "ticker", defaultValue = "$GME") String ticker,
                                                     @RequestHeader MultiValueMap<String, String> headers) {
        logger.info("/subscription service requested");
        // OTel Tracing API
        final Tracer tracer =
                openTelemetry.getTracer("com.sherrif.of.nottingham.order.service.services.SubscriptionService");

        HttpEntity entity = new HttpEntity(ticker,headers);

        Context extractedContext = openTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.current(), entity, getter);

        // Build a span based on the received context
        Span span =
                tracer
                        .spanBuilder("subscriptionService/subscribe")
                        .setParent(extractedContext)
                        .setSpanKind(SpanKind.SERVER)
                        .startSpan();

        try {
            handleError(ticker);
        } catch (Exception e) {
            logger.error("Random exception during the /subscribe of : {} for region {}", ticker);
            Attributes attributes = Attributes.of(AttributeKey.stringKey("stack-trace"), String.valueOf(e));
            span.addEvent("Order Processor Stack trace", attributes);
            span.setAttribute("Stack trace", String.valueOf(e.getStackTrace()));
            span.setStatus(StatusCode.ERROR, String.valueOf(e.getStackTrace()));
        }

        // Set the context with the current span
        try (Scope scope = span.makeCurrent()) {
            try {
                logger.info("created subscription service span with id {}", span.getSpanContext());
            } catch (Throwable e) {
                logger.error("Exception during the /process with the exception {}", String.valueOf(e));
                span.setAttribute("Stack trace", String.valueOf(e.getStackTrace()));
                span.setStatus(StatusCode.ERROR, String.valueOf(e.getStackTrace()));
            }
        } finally {
            span.end();
        }
        return ResponseEntity.ok(this.stockQuote);
    }

    private void handleError(String ticker) throws Exception {
        Random random = new Random();
        if(random.nextInt(10)>5) {
            logger.error("Random exception during the transaction processing of : {}", ticker);
            throw new Exception(String.format("Random exception during the transaction processing of : %s", ticker));
        }
    }
}
