package com.orderprocessing.producer.service;

import com.orderprocessing.producer.connection.ActiveMQConnectionManager;
import com.orderprocessing.producer.model.OrderRequest;
import com.orderprocessing.producer.model.ProductMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final JmsTemplate jmsTemplate;
    private final ActiveMQConnectionManager connectionManager;

    @Value("${activemq.topic.name}")
    private String topicName;

    public OrderService(JmsTemplate jmsTemplate, ActiveMQConnectionManager connectionManager) {
        this.jmsTemplate = jmsTemplate;
        this.connectionManager = connectionManager;
    }

    public void processOrder(OrderRequest orderRequest) throws Exception {
        String orderId = orderRequest.getOrderId();
        int productCount = orderRequest.getProductIds().size();
        
        logger.info("[Producer] Received order - orderId: {}, productIds count: {}", orderId, productCount);

        if (!connectionManager.isConnected()) {
            logger.error("[Producer] ActiveMQ connection unavailable for order: {}", orderId);
            throw new Exception("ActiveMQ connection unavailable");
        }

        for (String productId : orderRequest.getProductIds()) {
            try {
                ProductMessage message = new ProductMessage(orderId, productId);
                jmsTemplate.convertAndSend(topicName, message);
                logger.info("[Producer] Published message - orderId: {}, productId: {}", orderId, productId);
            } catch (Exception e) {
                logger.error("[Producer] Failed to publish message - orderId: {}, productId: {}", 
                           orderId, productId, e);
                connectionManager.handleConnectionLoss();
                throw new Exception("Failed to publish message to ActiveMQ");
            }
        }
    }
}
