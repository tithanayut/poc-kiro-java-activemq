package com.orderprocessing.consumer.config;

import jakarta.jms.ConnectionFactory;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

/**
 * JMS configuration for ActiveMQ consumer with durable subscriptions.
 * Configures connection factory, listener container factory, and message converter.
 */
@Configuration
public class JmsConfig {

    @Value("${activemq.broker.url}")
    private String brokerUrl;

    @Value("${activemq.client.id}")
    private String clientId;

    /**
     * Creates ActiveMQ connection factory with client ID for durable subscriptions.
     * 
     * @return configured connection factory
     */
    @Bean
    public ConnectionFactory connectionFactory() {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
        factory.setBrokerURL(brokerUrl);
        factory.setClientID(clientId);
        return factory;
    }

    /**
     * Creates JMS listener container factory with CLIENT_ACKNOWLEDGE mode.
     * Configures for topic subscriptions with durable subscriptions.
     * 
     * @param connectionFactory the connection factory to use
     * @return configured listener container factory
     */
    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setSessionAcknowledgeMode(jakarta.jms.Session.CLIENT_ACKNOWLEDGE);
        factory.setPubSubDomain(true); // Enable topic mode
        factory.setSubscriptionDurable(true); // Enable durable subscriptions
        factory.setMessageConverter(messageConverter());
        return factory;
    }

    /**
     * Creates Jackson message converter for JSON deserialization.
     * Configures type id mappings to handle producer/consumer package differences.
     * 
     * @return configured message converter
     */
    @Bean
    public MessageConverter messageConverter() {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_type");
        
        // Map producer's ProductMessage type to consumer's ProductMessage type
        java.util.Map<String, Class<?>> typeIdMappings = new java.util.HashMap<>();
        typeIdMappings.put("com.orderprocessing.producer.model.ProductMessage", 
                          com.orderprocessing.consumer.model.ProductMessage.class);
        converter.setTypeIdMappings(typeIdMappings);
        
        return converter;
    }
}
