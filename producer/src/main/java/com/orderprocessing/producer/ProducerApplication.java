package com.orderprocessing.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class ProducerApplication {

    private static final Logger logger = LoggerFactory.getLogger(ProducerApplication.class);

    @Value("${activemq.broker.url}")
    private String brokerUrl;

    @Value("${server.port}")
    private String serverPort;

    @Value("${activemq.topic.name}")
    private String topicName;

    public static void main(String[] args) {
        SpringApplication.run(ProducerApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logConfiguration() {
        logger.info("[Producer] Configuration loaded:");
        logger.info("[Producer]   ActiveMQ Broker URL: {}", brokerUrl);
        logger.info("[Producer]   HTTP Server Port: {}", serverPort);
        logger.info("[Producer]   Topic Name: {}", topicName);
        logger.info("[Producer] Producer service started successfully");
    }
}
