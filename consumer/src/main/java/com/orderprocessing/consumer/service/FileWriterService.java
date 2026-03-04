package com.orderprocessing.consumer.service;

import com.orderprocessing.consumer.model.Product;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
@Slf4j
public class FileWriterService {

    private final String outputDirectoryPath;
    private final int maxRetryAttempts;
    private final int retryDelayMs;

    public FileWriterService(
            @Value("${output.directory.path}") String outputDirectoryPath,
            @Value("${file.writer.max-retry-attempts:3}") int maxRetryAttempts,
            @Value("${file.writer.retry-delay-ms:1000}") int retryDelayMs) {
        this.outputDirectoryPath = outputDirectoryPath;
        this.maxRetryAttempts = maxRetryAttempts;
        this.retryDelayMs = retryDelayMs;
    }

    @PostConstruct
    public void initializeOutputDirectory() {
        log.info("[Consumer] Initializing output directory: {}", outputDirectoryPath);
        
        Path outputDir = Paths.get(outputDirectoryPath);
        
        if (!Files.exists(outputDir)) {
            try {
                Files.createDirectories(outputDir);
                log.info("[Consumer] Created output directory: {}", outputDirectoryPath);
            } catch (IOException e) {
                log.error("[Consumer] Failed to create output directory: {}", outputDirectoryPath, e);
                throw new RuntimeException("Cannot create output directory: " + outputDirectoryPath, e);
            }
        }
        
        if (!Files.isWritable(outputDir)) {
            log.error("[Consumer] Output directory is not writable: {}", outputDirectoryPath);
            throw new RuntimeException("Output directory is not writable: " + outputDirectoryPath);
        }
        
        log.info("[Consumer] Output directory validated: {}", outputDirectoryPath);
    }

    public void writeBatch(List<Product> products, int fileCounter) {
        String fileName = String.format("PRODUCT_ORDER_%d.txt", fileCounter);
        Path filePath = Paths.get(outputDirectoryPath, fileName);

        log.info("[Consumer] Writing batch to file - batch size: {}, file: {}", 
                products.size(), fileName);

        boolean success = false;
        int attempt = 0;

        while (!success && attempt < maxRetryAttempts) {
            try {
                writeToFile(filePath, products);
                success = true;
                log.info("[Consumer] Successfully wrote batch to file: {}", fileName);
            } catch (IOException e) {
                attempt++;
                log.error("[Consumer] Failed to write batch to file: {} (attempt {}/{})", 
                        fileName, attempt, maxRetryAttempts, e);
                
                if (attempt < maxRetryAttempts) {
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("[Consumer] Interrupted during retry delay");
                        break;
                    }
                }
            }
        }

        if (!success) {
            log.error("[Consumer] CRITICAL: Failed to write batch after {} attempts. " +
                    "Batch will be lost. File: {}, batch size: {}", 
                    maxRetryAttempts, fileName, products.size());
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
