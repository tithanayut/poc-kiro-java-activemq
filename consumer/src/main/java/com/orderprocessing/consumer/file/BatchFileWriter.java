package com.orderprocessing.consumer.file;

import com.orderprocessing.consumer.batch.BatchState;
import com.orderprocessing.consumer.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class BatchFileWriter {

    private static final Logger logger = LoggerFactory.getLogger(BatchFileWriter.class);
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MS = 1000;

    private final String outputDirectoryPath;

    public BatchFileWriter(String outputDirectoryPath) {
        this.outputDirectoryPath = outputDirectoryPath;
    }

    public synchronized void writeBatch(BatchState batchState) {
        List<Product> products = batchState.getProducts();
        int fileCounter = batchState.getFileCounter();
        String fileName = String.format("PRODUCT_ORDER_%d.txt", fileCounter);
        Path filePath = Paths.get(outputDirectoryPath, fileName);

        logger.info("[Consumer] Writing batch to file - batch size: {}, file: {}", 
                   products.size(), fileName);

        boolean success = false;
        int attempt = 0;

        while (!success && attempt < MAX_RETRY_ATTEMPTS) {
            try {
                writeToFile(filePath, products);
                success = true;
                logger.info("[Consumer] Successfully wrote batch to file: {}", fileName);
            } catch (IOException e) {
                attempt++;
                logger.error("[Consumer] Failed to write batch to file: {} (attempt {}/{})", 
                           fileName, attempt, MAX_RETRY_ATTEMPTS, e);
                
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        if (!success) {
            logger.error("[Consumer] CRITICAL: Failed to write batch after {} attempts. " +
                        "Batch will be lost. File: {}, batch size: {}", 
                        MAX_RETRY_ATTEMPTS, fileName, products.size());
        } else {
            batchState.incrementFileCounter();
            batchState.clear();
        }
    }

    private void writeToFile(Path filePath, List<Product> products) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath.toFile()))) {
            for (Product product : products) {
                String line = String.format("%s|%s", product.getOrderId(), product.getProductId());
                writer.write(line);
                writer.newLine();
            }
        }
    }
}
