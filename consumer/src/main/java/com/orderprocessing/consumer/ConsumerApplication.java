package com.orderprocessing.consumer;

import com.orderprocessing.consumer.service.BatchService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jms.annotation.EnableJms;

@SpringBootApplication
@EnableJms
@Slf4j
public class ConsumerApplication {

    private final BatchService batchService;

    public ConsumerApplication(BatchService batchService) {
        this.batchService = batchService;
    }

    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class, args);
        log.info("[Consumer] Consumer service started successfully");
    }

    @PreDestroy
    public void onShutdown() {
        log.info("[Consumer] Shutting down consumer service...");
        batchService.flush();
        log.info("[Consumer] Shutdown complete");
    }
}
