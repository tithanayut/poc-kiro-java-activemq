package com.orderprocessing.consumer.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class FileCounterInitializer {

    private static final Logger logger = LoggerFactory.getLogger(FileCounterInitializer.class);
    private static final Pattern FILE_PATTERN = Pattern.compile("PRODUCT_ORDER_(\\d+)\\.txt");

    private final String outputDirectoryPath;

    public FileCounterInitializer(String outputDirectoryPath) {
        this.outputDirectoryPath = outputDirectoryPath;
    }

    public int initialize() {
        logger.info("[Consumer] Initializing file counter from output directory: {}", outputDirectoryPath);
        
        Path outputDir = Paths.get(outputDirectoryPath);
        
        // Check if directory exists
        if (!Files.exists(outputDir)) {
            try {
                Files.createDirectories(outputDir);
                logger.info("[Consumer] Created output directory: {}", outputDirectoryPath);
                logger.info("[Consumer] Initialized file counter to: 1");
                return 1;
            } catch (IOException e) {
                logger.error("[Consumer] Failed to create output directory: {}", outputDirectoryPath, e);
                throw new RuntimeException("Cannot create output directory: " + outputDirectoryPath, e);
            }
        }
        
        // Check if directory is readable
        if (!Files.isReadable(outputDir)) {
            logger.error("[Consumer] Cannot read output directory: {}", outputDirectoryPath);
            throw new RuntimeException("Cannot read output directory: " + outputDirectoryPath);
        }
        
        // Check if directory is writable
        if (!Files.isWritable(outputDir)) {
            logger.error("[Consumer] Cannot write to output directory: {}", outputDirectoryPath);
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
                        logger.debug("[Consumer] Found valid file: {} with counter: {}", fileName, counter);
                        if (counter > maxCounter) {
                            maxCounter = counter;
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("[Consumer] Skipping file with invalid name format: {}", fileName);
                    }
                } else if (fileName.startsWith("PRODUCT_ORDER_") && fileName.endsWith(".txt")) {
                    logger.warn("[Consumer] Skipping file with invalid name format: {}", fileName);
                }
            }
        } catch (IOException e) {
            logger.error("[Consumer] Error scanning output directory: {}", outputDirectoryPath, e);
            throw new RuntimeException("Error scanning output directory: " + outputDirectoryPath, e);
        }
        
        int initialCounter = maxCounter + 1;
        logger.info("[Consumer] Initialized file counter to: {}", initialCounter);
        return initialCounter;
    }
}
