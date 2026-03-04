package com.orderprocessing.consumer.service;

import com.orderprocessing.consumer.batch.BatchState;
import com.orderprocessing.consumer.model.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class BatchService {

    private final BatchState batchState;
    private final FileWriterService fileWriterService;
    private final int batchSize;

    public BatchService(
            BatchState batchState,
            FileWriterService fileWriterService,
            @Value("${batch.size}") int batchSize) {
        this.batchState = batchState;
        this.fileWriterService = fileWriterService;
        this.batchSize = batchSize;
    }

    public synchronized void addProduct(Product product) {
        log.debug("[Consumer] Adding product to batch - orderId: {}, productId: {}", 
                product.getOrderId(), product.getProductId());
        
        batchState.addProduct(product);
        
        if (batchState.isFull(batchSize)) {
            log.info("[Consumer] Batch is full (size: {}), triggering file write", batchSize);
            writeBatch();
        }
    }

    public synchronized void flush() {
        if (batchState.getCurrentSize() > 0) {
            log.info("[Consumer] Flushing in-progress batch (size: {})", batchState.getCurrentSize());
            writeBatch();
        } else {
            log.info("[Consumer] No in-progress batch to flush");
        }
    }

    private void writeBatch() {
        List<Product> products = batchState.getProducts();
        int fileCounter = batchState.getFileCounter();
        
        log.info("[Consumer] Writing batch - size: {}, file counter: {}", products.size(), fileCounter);
        
        fileWriterService.writeBatch(products, fileCounter);
        
        batchState.clear();
        log.info("[Consumer] Batch cleared after successful write");
    }
}
