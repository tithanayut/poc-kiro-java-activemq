package com.orderprocessing.producer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderProcessingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Order Processing Producer API")
                        .description("REST API for submitting orders to ActiveMQ")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Order Processing Team")));
    }
}
