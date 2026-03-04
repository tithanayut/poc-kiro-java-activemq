package com.orderprocessing.consumer.config;

import jakarta.jms.ConnectionFactory;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JmsConfig configuration class.
 * Tests bean creation and configuration properties.
 */
@SpringBootTest(classes = JmsConfig.class)
@TestPropertySource(properties = {
    "activemq.broker.url=tcp://localhost:61616",
    "activemq.client.id=test-client-id"
})
class JmsConfigTest {

    @Autowired
    private JmsConfig jmsConfig;

    @Test
    void testConnectionFactoryBeanCreation() {
        // When
        ConnectionFactory connectionFactory = jmsConfig.connectionFactory();

        // Then
        assertNotNull(connectionFactory, "Connection factory should not be null");
        assertTrue(connectionFactory instanceof ActiveMQConnectionFactory, 
            "Connection factory should be ActiveMQConnectionFactory");
        
        ActiveMQConnectionFactory activeMQFactory = (ActiveMQConnectionFactory) connectionFactory;
        assertEquals("tcp://localhost:61616", activeMQFactory.getBrokerURL(), 
            "Broker URL should match configured value");
        assertEquals("test-client-id", activeMQFactory.getClientID(), 
            "Client ID should match configured value");
    }

    @Test
    void testJmsListenerContainerFactoryConfiguration() {
        // Given
        ConnectionFactory connectionFactory = jmsConfig.connectionFactory();

        // When
        DefaultJmsListenerContainerFactory factory = jmsConfig.jmsListenerContainerFactory(connectionFactory);

        // Then
        assertNotNull(factory, "Listener container factory should not be null");
        // Note: DefaultJmsListenerContainerFactory doesn't expose getters for configuration properties
        // The configuration is verified through integration tests
    }

    @Test
    void testMessageConverterBeanCreation() {
        // When
        MessageConverter converter = jmsConfig.messageConverter();

        // Then
        assertNotNull(converter, "Message converter should not be null");
    }
}
