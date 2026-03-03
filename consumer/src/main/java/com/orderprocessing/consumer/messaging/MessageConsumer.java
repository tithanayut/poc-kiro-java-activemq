package com.orderprocessing.consumer.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.consumer.batch.BatchState;
import com.orderprocessing.consumer.file.BatchFileWriter;
import com.orderprocessing.consumer.model.Product;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.jms.*;

public class MessageConsumer {

    private static final Logger logger = LoggerFactory.getLogger(MessageConsumer.class);

    private final String brokerUrl;
    private final String topicName;
    private final String clientId;
    private final String subscriptionName;
    private final BatchState batchState;
    private final BatchFileWriter fileWriter;
    private final ObjectMapper objectMapper;

    private Connection connection;
    private Session session;
    private jakarta.jms.MessageConsumer consumer;

    public MessageConsumer(String brokerUrl, String topicName, BatchState batchState, BatchFileWriter fileWriter) {
        this(brokerUrl, topicName, "order-consumer-client", "order-consumer-subscription", batchState, fileWriter);
    }

    public MessageConsumer(String brokerUrl, String topicName, String clientId, String subscriptionName,
                          BatchState batchState, BatchFileWriter fileWriter) {
        this.brokerUrl = brokerUrl;
        this.topicName = topicName;
        this.clientId = clientId;
        this.subscriptionName = subscriptionName;
        this.batchState = batchState;
        this.fileWriter = fileWriter;
        this.objectMapper = new ObjectMapper();
    }

    public void start() throws JMSException {
        logger.info("[Consumer] Connecting to ActiveMQ at: {}", brokerUrl);
        logger.info("[Consumer] Using durable subscription - clientId: {}, subscriptionName: {}", clientId, subscriptionName);

        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUrl);
        connection = connectionFactory.createConnection();
        connection.setClientID(clientId);
        connection.start();

        logger.info("[Consumer] Successfully connected to ActiveMQ with client ID: {}", clientId);

        session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        Topic topic = session.createTopic(topicName);
        consumer = session.createDurableSubscriber(topic, subscriptionName);

        logger.info("[Consumer] Created durable subscription: {} on topic: {}", subscriptionName, topicName);

        consumer.setMessageListener(message -> {
            try {
                processMessage(message);
            } catch (Exception e) {
                logger.error("[Consumer] Error processing message", e);
            }
        });
    }

    private void processMessage(Message message) throws JMSException {
        try {
            if (message instanceof TextMessage) {
                TextMessage textMessage = (TextMessage) message;
                String messageText = textMessage.getText();

                JsonNode jsonNode = objectMapper.readTree(messageText);
                String orderId = jsonNode.get("orderId").asText();
                String productId = jsonNode.get("productId").asText();

                logger.info("[Consumer] Received message - orderId: {}, productId: {}", orderId, productId);

                Product product = new Product(orderId, productId);
                batchState.addProduct(product);

                message.acknowledge();

                if (batchState.isFull()) {
                    fileWriter.writeBatch(batchState);
                }
            }
        } catch (Exception e) {
            logger.error("[Consumer] Error parsing message: {}", message, e);
            message.acknowledge();
        }
    }

    public void stop() {
        try {
            if (consumer != null) {
                consumer.close();
            }
            if (session != null) {
                session.close();
            }
            if (connection != null) {
                connection.close();
            }
            logger.info("[Consumer] Disconnected from ActiveMQ");
        } catch (JMSException e) {
            logger.error("[Consumer] Error closing connection", e);
        }
    }
}
