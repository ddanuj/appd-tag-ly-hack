package com.sherrif.of.nottingham.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan({"com.sherrif.of.nottingham.services.processor"})
public class OrderProcessorApplication {
    public static void main(String[] args) {
        // Set to process the spans with the LoggingSpanExporter
        System.setProperty("otel.resource.attributes", "service.name=OtlpExporterExample");
        SpringApplication.run(OrderProcessorApplication.class, args);
    }
}
