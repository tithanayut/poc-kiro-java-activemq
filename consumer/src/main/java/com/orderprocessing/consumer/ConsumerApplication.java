package com.orderprocessing.consumer;

import com.orderprocessing.consumer.batch.BatchState;
import com.orderprocessing.consumer.file.BatchFileWriter;
import com.orderprocessing.consumer.file.FileCounterInitializer;
import com.orderprocessing.consumer.messaging.MessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ConsumerApplication {

    private static final Logger logger = LoggerFactory.getLogger(ConsumerApplication.class);

    public static void main(String[] args) {
        try {
            // Load configuration
            Properties config = loadConfiguration();
            
            String brokerUrl = config.getProperty("activemq.broker.url");
            String topicName = config.getProperty("activemq.topic.name");
            String outputDirectory = config.getProperty("output.directory.path");
            int batchSize = Integer.parseInt(config.getProperty("batch.size"));

            logger.info("[Consumer] Configuration loaded:");
            logger.info("[Consumer]   ActiveMQ Broker URL: {}", brokerUrl);
            logger.info("[Consumer]   Topic Name: {}", topicName);
            logger.info("[Consumer]   Output Directory: {}", outputDirectory);
            logger.info("[Consumer]   Batch Size: {}", batchSize);

            // Initialize file counter
            FileCounterInitializer counterInitializer = new FileCounterInitializer(outputDirectory);
            int initialFileCounter = counterInitializer.initialize();

            // Initialize batch state
            BatchState batchState = new BatchState(batchSize, initialFileCounter);

            // Initialize file writer
            BatchFileWriter fileWriter = new BatchFileWriter(outputDirectory);

            // Initialize and start message consumer
            MessageConsumer messageConsumer = new MessageConsumer(brokerUrl, topicName, batchState, fileWriter);
            messageConsumer.start();

            logger.info("[Consumer] Consumer service started successfully");

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("[Consumer] Shutting down consumer service...");
                messageConsumer.stop();
            }));

            // Keep the application running
            Thread.currentThread().join();

        } catch (Exception e) {
            logger.error("[Consumer] Failed to start consumer service", e);
            System.exit(1);
        }
    }

    private static Properties loadConfiguration() throws IOException {
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream("./src/main/resources/application.properties")) {
            properties.load(fis);
        }
        return properties;
    }
}
