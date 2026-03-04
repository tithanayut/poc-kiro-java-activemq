package com.orderprocessing.consumer.messaging;

import com.orderprocessing.consumer.model.Product;
import com.orderprocessing.consumer.model.ProductMessage;
import com.orderprocessing.consumer.service.BatchService;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MessageListener {

    private final BatchService batchService;

    public MessageListener(BatchService batchService) {
        this.batchService = batchService;
    }

    @JmsListener(
            destination = "${activemq.topic.name}",
            subscription = "${activemq.subscription.name}",
            containerFactory = "jmsListenerContainerFactory"
    )
    public void onMessage(ProductMessage message, Message jmsMessage) {
        try {
            log.info("[Consumer] Received message - orderId: {}, productId: {}", 
                    message.getOrderId(), message.getProductId());

            // Convert ProductMessage to Product domain model
            Product product = new Product(message.getOrderId(), message.getProductId());

            // Delegate to BatchService
            batchService.addProduct(product);

            // Acknowledge message after successful processing
            jmsMessage.acknowledge();
            
            log.debug("[Consumer] Message acknowledged successfully");
        } catch (Exception e) {
            log.error("[Consumer] Error processing message - orderId: {}, productId: {}", 
                    message.getOrderId(), message.getProductId(), e);
            try {
                // Acknowledge message even on error to prevent redelivery
                jmsMessage.acknowledge();
                log.debug("[Consumer] Message acknowledged after error");
            } catch (JMSException ackException) {
                log.error("[Consumer] Error acknowledging message after processing failure", ackException);
            }
        }
    }
}
