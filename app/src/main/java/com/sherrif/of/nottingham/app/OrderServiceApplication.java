package com.sherrif.of.nottingham.app;

import com.sherrif.of.nottingham.services.order.TagGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@ComponentScan({"com.sherrif.of.nottingham.services.order"})
public class OrderServiceApplication {

	public static void main(String[] args) {
		// Set to process the spans with the LoggingSpanExporter
		System.setProperty("otel.resource.attributes", "service.name=OtlpExporterExample");
		SpringApplication.run(OrderServiceApplication.class, args);
	}

	@Bean
	public RestTemplate restTemplate(RestTemplateBuilder builder) {
		return builder.build();
	}

	@Bean
	TagGenerator tagGenerator() {
		return new TagGenerator();
	}

}
