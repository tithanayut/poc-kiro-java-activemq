package com.orderprocessing.consumer.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Product validation.
 * Validates Requirements 11.1, 11.2
 */
class ProductTest {

    @Test
    void testConstructor_WithNullOrderId_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Product(null, "product1")
        );
        assertEquals("orderId must not be null or empty", exception.getMessage());
    }

    @Test
    void testConstructor_WithEmptyOrderId_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Product("", "product1")
        );
        assertEquals("orderId must not be null or empty", exception.getMessage());
    }

    @Test
    void testConstructor_WithWhitespaceOrderId_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Product("   ", "product1")
        );
        assertEquals("orderId must not be null or empty", exception.getMessage());
    }

    @Test
    void testConstructor_WithNullProductId_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Product("order1", null)
        );
        assertEquals("productId must not be null or empty", exception.getMessage());
    }

    @Test
    void testConstructor_WithEmptyProductId_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Product("order1", "")
        );
        assertEquals("productId must not be null or empty", exception.getMessage());
    }

    @Test
    void testConstructor_WithWhitespaceProductId_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Product("order1", "   ")
        );
        assertEquals("productId must not be null or empty", exception.getMessage());
    }

    @Test
    void testConstructor_WithValidData_CreatesProduct() {
        // When
        Product product = new Product("order1", "product1");

        // Then
        assertNotNull(product);
        assertEquals("order1", product.getOrderId());
        assertEquals("product1", product.getProductId());
    }

    @Test
    void testSetOrderId_WithNullValue_ThrowsException() {
        // Given
        Product product = new Product("order1", "product1");

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> product.setOrderId(null)
        );
        assertEquals("orderId must not be null or empty", exception.getMessage());
    }

    @Test
    void testSetOrderId_WithEmptyValue_ThrowsException() {
        // Given
        Product product = new Product("order1", "product1");

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> product.setOrderId("")
        );
        assertEquals("orderId must not be null or empty", exception.getMessage());
    }

    @Test
    void testSetProductId_WithNullValue_ThrowsException() {
        // Given
        Product product = new Product("order1", "product1");

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> product.setProductId(null)
        );
        assertEquals("productId must not be null or empty", exception.getMessage());
    }

    @Test
    void testSetProductId_WithEmptyValue_ThrowsException() {
        // Given
        Product product = new Product("order1", "product1");

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> product.setProductId("")
        );
        assertEquals("productId must not be null or empty", exception.getMessage());
    }

    @Test
    void testSetOrderId_WithValidValue_UpdatesOrderId() {
        // Given
        Product product = new Product("order1", "product1");

        // When
        product.setOrderId("order2");

        // Then
        assertEquals("order2", product.getOrderId());
    }

    @Test
    void testSetProductId_WithValidValue_UpdatesProductId() {
        // Given
        Product product = new Product("order1", "product1");

        // When
        product.setProductId("product2");

        // Then
        assertEquals("product2", product.getProductId());
    }
}
