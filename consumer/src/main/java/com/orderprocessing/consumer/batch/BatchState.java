package com.orderprocessing.consumer.batch;

import com.orderprocessing.consumer.model.Product;
import com.orderprocessing.consumer.service.FileCounterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class BatchState {

    private final List<Product> products;
    private final FileCounterService fileCounterService;

    /**
     * Spring constructor with dependency injection.
     *
     * @param fileCounterService service for managing file counter
     */
    public BatchState(FileCounterService fileCounterService) {
        this.products = new ArrayList<>();
        this.fileCounterService = fileCounterService;
    }

    /**
     * Adds a product to the current batch.
     * Thread-safe operation.
     *
     * @param product the product to add
     */
    public synchronized void addProduct(Product product) {
        products.add(product);
        log.info("[Consumer] Added product to batch - orderId: {}, productId: {}, current batch size: {}",
                   product.getOrderId(), product.getProductId(), products.size());
    }

    /**
     * Checks if the batch is full based on the provided max size.
     * Thread-safe operation.
     *
     * @param maxSize the maximum batch size
     * @return true if batch size >= maxSize, false otherwise
     */
    public synchronized boolean isFull(int maxSize) {
        return products.size() >= maxSize;
    }

    /**
     * Returns a copy of the current batch products.
     * Thread-safe operation.
     *
     * @return list of products in the batch
     */
    public synchronized List<Product> getProducts() {
        return new ArrayList<>(products);
    }

    /**
     * Returns the current batch size.
     * Thread-safe operation.
     *
     * @return number of products in the batch
     */
    public synchronized int getCurrentSize() {
        return products.size();
    }

    /**
     * Clears all products from the batch.
     * Thread-safe operation.
     */
    public synchronized void clear() {
        products.clear();
    }

    /**
     * Gets the next file counter value from FileCounterService.
     * Thread-safe operation.
     *
     * @return the next file counter value
     */
    public synchronized int getFileCounter() {
        return fileCounterService.getNextCounter();
    }
}
