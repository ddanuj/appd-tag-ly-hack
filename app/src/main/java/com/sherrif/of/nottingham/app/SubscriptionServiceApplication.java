package com.sherrif.of.nottingham.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan({"com.sherrif.of.nottingham.services.subscription"})
public class SubscriptionServiceApplication {
    public static void main(String[] args) {
        System.setProperty("otel.resource.attributes", "service.name=OtlpExporterExample");
        SpringApplication.run(SubscriptionServiceApplication.class, args);
    }
}
