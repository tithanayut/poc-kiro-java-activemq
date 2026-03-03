package com.orderprocessing.consumer.batch;

import com.orderprocessing.consumer.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class BatchState {

    private static final Logger logger = LoggerFactory.getLogger(BatchState.class);

    private final List<Product> products;
    private final int maxSize;
    private int fileCounter;

    public BatchState(int maxSize, int initialFileCounter) {
        this.products = new ArrayList<>();
        this.maxSize = maxSize;
        this.fileCounter = initialFileCounter;
    }

    public synchronized void addProduct(Product product) {
        products.add(product);
        logger.info("[Consumer] Added product to batch - orderId: {}, productId: {}, current batch size: {}", 
                   product.getOrderId(), product.getProductId(), products.size());
    }

    public synchronized boolean isFull() {
        return products.size() >= maxSize;
    }

    public synchronized List<Product> getProducts() {
        return new ArrayList<>(products);
    }

    public synchronized int getCurrentSize() {
        return products.size();
    }

    public synchronized void clear() {
        products.clear();
    }

    public synchronized int getFileCounter() {
        return fileCounter;
    }

    public synchronized void incrementFileCounter() {
        fileCounter++;
    }

    public int getMaxSize() {
        return maxSize;
    }
}
