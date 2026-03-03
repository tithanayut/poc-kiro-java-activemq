package com.orderprocessing.producer.connection;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.jms.Connection;
import jakarta.jms.JMSException;

@Component
public class ActiveMQConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(ActiveMQConnectionManager.class);
    
    private static final int INITIAL_DELAY_SECONDS = 1;
    private static final int MAX_DELAY_SECONDS = 60;
    private static final int BACKOFF_MULTIPLIER = 2;

    @Value("${activemq.broker.url}")
    private String brokerUrl;

    private ActiveMQConnectionFactory connectionFactory;
    private Connection connection;
    private volatile boolean connected = false;
    private volatile boolean reconnecting = false;

    @PostConstruct
    public void initialize() {
        logger.info("[Producer] Initializing ActiveMQ connection to: {}", brokerUrl);
        connectionFactory = new ActiveMQConnectionFactory(brokerUrl);
        establishConnection();
    }

    private void establishConnection() {
        int attempt = 0;
        while (!connected) {
            try {
                connection = connectionFactory.createConnection();
                connection.start();
                connected = true;
                reconnecting = false;
                logger.info("[Producer] Successfully connected to ActiveMQ at: {}", brokerUrl);
            } catch (JMSException e) {
                int delay = calculateBackoffDelay(attempt);
                logger.error("[Producer] Failed to connect to ActiveMQ (attempt {}). Retrying in {} seconds...", 
                           attempt + 1, delay, e);
                sleep(delay);
                attempt++;
            }
        }
    }

    public void handleConnectionLoss() {
        if (!reconnecting) {
            reconnecting = true;
            connected = false;
            logger.warn("[Producer] ActiveMQ connection lost. Starting reconnection attempts...");
            
            new Thread(() -> {
                int attempt = 0;
                while (!connected) {
                    int delay = calculateBackoffDelay(attempt);
                    logger.info("[Producer] Attempting to reconnect to ActiveMQ (attempt {})...", attempt + 1);
                    sleep(delay);
                    
                    try {
                        if (connection != null) {
                            try {
                                connection.close();
                            } catch (JMSException ignored) {
                            }
                        }
                        connection = connectionFactory.createConnection();
                        connection.start();
                        connected = true;
                        reconnecting = false;
                        logger.info("[Producer] Successfully reconnected to ActiveMQ");
                    } catch (JMSException e) {
                        logger.error("[Producer] Reconnection attempt {} failed", attempt + 1, e);
                    }
                    attempt++;
                }
            }).start();
        }
    }

    private int calculateBackoffDelay(int attempt) {
        int delay = INITIAL_DELAY_SECONDS * (int) Math.pow(BACKOFF_MULTIPLIER, attempt);
        return Math.min(delay, MAX_DELAY_SECONDS);
    }

    private void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isConnected() {
        return connected && !reconnecting;
    }

    public boolean isReconnecting() {
        return reconnecting;
    }

    public Connection getConnection() {
        return connection;
    }

    @PreDestroy
    public void cleanup() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("[Producer] ActiveMQ connection closed");
            } catch (JMSException e) {
                logger.error("[Producer] Error closing ActiveMQ connection", e);
            }
        }
    }
}
