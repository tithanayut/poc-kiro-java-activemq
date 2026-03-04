package com.orderprocessing.consumer.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
@Slf4j
public class FileCounterService {

    private static final Pattern FILE_PATTERN = Pattern.compile("PRODUCT_ORDER_(\\d+)\\.txt");

    private final String outputDirectoryPath;
    private final AtomicInteger fileCounter;

    public FileCounterService(@Value("${output.directory.path}") String outputDirectoryPath) {
        this.outputDirectoryPath = outputDirectoryPath;
        this.fileCounter = new AtomicInteger(1);
    }

    @PostConstruct
    public int initializeCounter() {
        log.info("[Consumer] Initializing file counter from output directory: {}", outputDirectoryPath);
        
        Path outputDir = Paths.get(outputDirectoryPath);
        
        // Check if directory exists
        if (!Files.exists(outputDir)) {
            try {
                Files.createDirectories(outputDir);
                log.info("[Consumer] Created output directory: {}", outputDirectoryPath);
                log.info("[Consumer] Initialized file counter to: 1");
                fileCounter.set(1);
                return 1;
            } catch (IOException e) {
                log.error("[Consumer] Failed to create output directory: {}", outputDirectoryPath, e);
                throw new RuntimeException("Cannot create output directory: " + outputDirectoryPath, e);
            }
        }
        
        // Check if directory is readable
        if (!Files.isReadable(outputDir)) {
            log.error("[Consumer] Cannot read output directory: {}", outputDirectoryPath);
            throw new RuntimeException("Cannot read output directory: " + outputDirectoryPath);
        }
        
        // Check if directory is writable
        if (!Files.isWritable(outputDir)) {
            log.error("[Consumer] Cannot write to output directory: {}", outputDirectoryPath);
            throw new RuntimeException("Cannot write to output directory: " + outputDirectoryPath);
        }
        
        // Scan for existing files
        int maxCounter = 0;
        try (Stream<Path> files = Files.list(outputDir)) {
            for (Path file : files.toList()) {
                String fileName = file.getFileName().toString();
                Matcher matcher = FILE_PATTERN.matcher(fileName);
                
                if (matcher.matches()) {
                    try {
                        int counter = Integer.parseInt(matcher.group(1));
                        log.debug("[Consumer] Found valid file: {} with counter: {}", fileName, counter);
                        if (counter > maxCounter) {
                            maxCounter = counter;
                        }
                    } catch (NumberFormatException e) {
                        log.warn("[Consumer] Skipping file with invalid name format: {}", fileName);
                    }
                } else if (fileName.startsWith("PRODUCT_ORDER_") && fileName.endsWith(".txt")) {
                    log.warn("[Consumer] Skipping file with invalid name format: {}", fileName);
                }
            }
        } catch (IOException e) {
            log.error("[Consumer] Error scanning output directory: {}", outputDirectoryPath, e);
            throw new RuntimeException("Error scanning output directory: " + outputDirectoryPath, e);
        }
        
        int initialCounter = maxCounter + 1;
        log.info("[Consumer] Initialized file counter to: {}", initialCounter);
        fileCounter.set(initialCounter);
        return initialCounter;
    }

    public int getNextCounter() {
        return fileCounter.getAndIncrement();
    }
}
