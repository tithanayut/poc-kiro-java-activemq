package com.orderprocessing.producer.controller;

import com.orderprocessing.producer.connection.ActiveMQConnectionManager;
import com.orderprocessing.producer.model.OrderRequest;
import com.orderprocessing.producer.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/orders")
@Tag(name = "Orders", description = "Order processing endpoints")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final ActiveMQConnectionManager connectionManager;

    public OrderController(OrderService orderService, ActiveMQConnectionManager connectionManager) {
        this.orderService = orderService;
        this.connectionManager = connectionManager;
    }

    @PostMapping
    @Operation(summary = "Create a new order", description = "Submits an order to the ActiveMQ topic for processing")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Order accepted for processing"),
            @ApiResponse(responseCode = "400", description = "Invalid order data"),
            @ApiResponse(responseCode = "503", description = "Service temporarily unavailable")
    })
    public ResponseEntity<Map<String, String>> createOrder(@Valid @RequestBody OrderRequest orderRequest) {
        try {
            if (connectionManager.isReconnecting()) {
                logger.warn("[Producer] Rejecting order request - ActiveMQ reconnecting");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("error", "Service temporarily unavailable"));
            }

            orderService.processOrder(orderRequest);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of("message", "Order accepted"));
        } catch (Exception e) {
            logger.error("[Producer] Error processing order", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Service temporarily unavailable"));
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put("error", errorMessage);
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }
}
