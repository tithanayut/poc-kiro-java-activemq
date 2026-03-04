# Requirements Document: Consumer Spring and Lombok Refactor

## Introduction

This document specifies the functional and non-functional requirements for refactoring the existing consumer application to use Spring Framework and Lombok. The system consumes messages from ActiveMQ topics using durable subscriptions, batches product data, and writes batches to numbered text files. The refactoring modernizes the codebase while preserving all existing functionality.

## Glossary

- **System**: The consumer application that processes messages and writes batch files
- **ActiveMQ_Broker**: The message broker that publishes product messages to topics
- **Batch**: A collection of products accumulated until reaching the configured batch size
- **Batch_State**: Component that maintains the current batch and file counter
- **Message_Listener**: Component that receives and processes JMS messages
- **Batch_Service**: Component that manages batch accumulation and triggers file writes
- **File_Writer_Service**: Component that writes batches to the file system
- **File_Counter_Service**: Component that initializes and manages file counter values
- **Durable_Subscription**: JMS subscription that persists messages when consumer is offline
- **Product**: Domain model representing an order-product pair
- **Product_Message**: DTO for deserializing JSON messages from ActiveMQ

## Requirements

### Requirement 1: Message Consumption

**User Story:** As a system operator, I want the system to consume messages from ActiveMQ topics using durable subscriptions, so that no messages are lost when the consumer is offline.

#### Acceptance Criteria

1. WHEN the System starts, THE System SHALL create a durable subscription to the configured ActiveMQ topic using the configured client ID and subscription name
2. WHEN a message is published to the topic while the System is offline, THE System SHALL receive and process that message when it reconnects
3. WHEN the Message_Listener receives a valid JSON message, THE System SHALL deserialize it to a Product_Message object
4. WHEN the Message_Listener successfully processes a message, THE System SHALL acknowledge the message to the ActiveMQ_Broker
5. IF message deserialization fails, THEN THE System SHALL log the error, acknowledge the message, and continue processing subsequent messages

### Requirement 2: Batch Management

**User Story:** As a system operator, I want products to be accumulated in batches of configurable size, so that file I/O is efficient and manageable.

#### Acceptance Criteria

1. WHEN a Product is added to the Batch_State, THE Batch_State SHALL add it to the current batch
2. WHILE the current batch size is less than the configured batch size, THE System SHALL continue accumulating products
3. WHEN the current batch size reaches the configured batch size, THE Batch_Service SHALL trigger a file write operation
4. THE Batch_State SHALL ensure the number of products in the current batch never exceeds the configured batch size
5. WHEN a batch is successfully written to a file, THE Batch_Service SHALL clear the Batch_State and increment the file counter

### Requirement 3: File Writing

**User Story:** As a system operator, I want batches written to numbered text files with retry logic, so that transient I/O errors don't cause data loss.

#### Acceptance Criteria

1. WHEN the File_Writer_Service writes a batch, THE System SHALL create a file named `PRODUCT_ORDER_{counter}.txt` where counter is the current file counter value
2. WHEN writing a batch to a file, THE System SHALL format each product as a pipe-delimited line: `orderId|productId`
3. IF a file write operation fails with an IOException, THEN THE File_Writer_Service SHALL retry the operation up to 3 times with a 1-second delay between attempts
4. WHEN all retry attempts fail, THE System SHALL log a critical error with batch details and acknowledge the message
5. WHEN a batch write succeeds, THE System SHALL log the success and return control to the Batch_Service
6. THE File_Writer_Service SHALL write all products in a batch to the file atomically (all or none)

### Requirement 4: File Counter Management

**User Story:** As a system operator, I want file counters to be unique and monotonically increasing, so that batch files are never overwritten.

#### Acceptance Criteria

1. WHEN the System starts, THE File_Counter_Service SHALL scan the output directory for existing `PRODUCT_ORDER_*.txt` files
2. WHEN existing batch files are found, THE File_Counter_Service SHALL parse file names to find the maximum counter value
3. WHEN the File_Counter_Service initializes the counter, THE System SHALL set it to the maximum existing counter value plus one
4. WHEN a batch is written, THE Batch_State SHALL increment the file counter by one
5. THE System SHALL ensure that no two batch writes produce files with the same name
6. THE System SHALL ensure the file counter strictly increases with each batch write and never decreases

### Requirement 5: Output Directory Management

**User Story:** As a system operator, I want the output directory to be validated and created on startup, so that file write operations don't fail due to missing directories.

#### Acceptance Criteria

1. WHEN the System starts, THE File_Writer_Service SHALL check if the configured output directory exists
2. IF the output directory does not exist, THEN THE File_Writer_Service SHALL create it
3. IF the output directory cannot be created or is not writable, THEN THE System SHALL fail startup with a clear error message
4. WHEN the output directory is validated, THE System SHALL log the directory path

### Requirement 6: Spring Configuration Management

**User Story:** As a developer, I want all configuration externalized to application.properties with Spring property injection, so that the application is configurable without code changes.

#### Acceptance Criteria

1. THE System SHALL load configuration properties from application.properties on startup
2. THE System SHALL inject the following required properties: activemq.broker.url, activemq.client.id, activemq.topic.name, activemq.subscription.name, batch.size, output.directory.path
3. THE System SHALL inject the following optional properties with defaults: file.writer.max-retry-attempts (default: 3), file.writer.retry-delay-ms (default: 1000)
4. IF any required configuration property is missing, THEN THE System SHALL fail startup with a validation error message
5. IF a configuration property has an invalid value, THEN THE System SHALL fail startup with a validation error message

### Requirement 7: Dependency Injection

**User Story:** As a developer, I want all component dependencies managed by Spring, so that the application is testable and maintainable.

#### Acceptance Criteria

1. THE System SHALL use constructor-based dependency injection for all components
2. THE System SHALL annotate service components with @Service
3. THE System SHALL annotate configuration classes with @Configuration
4. THE System SHALL annotate the application entry point with @SpringBootApplication
5. THE System SHALL enable JMS listener support with @EnableJms

### Requirement 8: Thread Safety

**User Story:** As a system operator, I want batch operations to be thread-safe, so that concurrent message processing doesn't corrupt batch state.

#### Acceptance Criteria

1. WHEN multiple threads access the Batch_State concurrently, THE System SHALL synchronize access to prevent race conditions
2. WHEN the Batch_Service adds a product, THE System SHALL use synchronized methods to ensure atomic batch operations
3. WHEN the Batch_State checks if the batch is full, THE System SHALL use synchronized methods to ensure consistent state
4. WHEN the File_Counter_Service increments the counter, THE System SHALL use thread-safe operations

### Requirement 9: Error Handling and Logging

**User Story:** As a system operator, I want comprehensive error logging, so that I can diagnose and resolve issues quickly.

#### Acceptance Criteria

1. WHEN a message deserialization error occurs, THE System SHALL log the error with message details
2. WHEN a file write fails, THE System SHALL log each retry attempt with attempt number and error details
3. WHEN all file write retries fail, THE System SHALL log a critical error with batch details
4. WHEN the ActiveMQ connection is lost, THE System SHALL log the connection loss and reconnection attempts
5. WHEN the System starts successfully, THE System SHALL log startup completion with configuration summary

### Requirement 10: Graceful Shutdown

**User Story:** As a system operator, I want the system to flush in-progress batches during shutdown, so that no data is lost when stopping the application.

#### Acceptance Criteria

1. WHEN the System receives a shutdown signal, THE System SHALL stop accepting new messages
2. WHEN the System is shutting down, THE Batch_Service SHALL flush any in-progress batch to a file
3. WHEN the shutdown flush completes, THE System SHALL close all resources and exit
4. THE System SHALL use Spring lifecycle hooks to coordinate graceful shutdown

### Requirement 11: Data Model Validation

**User Story:** As a developer, I want product data validated, so that invalid data is detected early.

#### Acceptance Criteria

1. WHEN a Product is created, THE System SHALL ensure orderId is not null or empty
2. WHEN a Product is created, THE System SHALL ensure productId is not null or empty
3. WHEN a Product_Message is deserialized, THE System SHALL ensure orderId is not null or empty
4. WHEN a Product_Message is deserialized, THE System SHALL ensure productId is not null or empty

### Requirement 12: Lombok Code Generation

**User Story:** As a developer, I want Lombok to eliminate boilerplate code, so that the codebase is concise and maintainable.

#### Acceptance Criteria

1. THE System SHALL use @Data annotation on Product to generate getters, setters, toString, equals, and hashCode
2. THE System SHALL use @Data annotation on Product_Message to generate getters, setters, toString, equals, and hashCode
3. THE System SHALL use @AllArgsConstructor on Product to generate a constructor with all fields
4. THE System SHALL use @NoArgsConstructor on Product and Product_Message to generate no-args constructors for Jackson
5. THE System SHALL use @Slf4j on components to generate logger fields

### Requirement 13: JMS Configuration

**User Story:** As a system operator, I want JMS configured with CLIENT_ACKNOWLEDGE mode and durable subscriptions, so that message processing is reliable.

#### Acceptance Criteria

1. WHEN the System creates a connection factory, THE System SHALL configure it with the ActiveMQ broker URL and client ID
2. WHEN the System creates a JMS listener container factory, THE System SHALL configure it with CLIENT_ACKNOWLEDGE mode
3. WHEN the System creates a JMS listener container factory, THE System SHALL configure it with a Jackson message converter for JSON deserialization
4. WHEN the Message_Listener is registered, THE System SHALL configure it with the topic destination and durable subscription name

### Requirement 14: ActiveMQ Connection Recovery

**User Story:** As a system operator, I want automatic reconnection to ActiveMQ after connection loss, so that the system recovers from transient network failures.

#### Acceptance Criteria

1. IF the ActiveMQ connection is lost, THEN THE System SHALL automatically attempt to reconnect
2. WHEN the System reconnects to ActiveMQ, THE System SHALL resume message processing from the durable subscription
3. WHEN connection recovery occurs, THE System SHALL log reconnection success
4. THE System SHALL use Spring JMS automatic reconnection capabilities

### Requirement 15: Performance and Scalability

**User Story:** As a system operator, I want the system to process messages efficiently and support horizontal scaling, so that throughput can be increased as needed.

#### Acceptance Criteria

1. THE System SHALL process messages asynchronously without blocking
2. THE System SHALL support configurable batch sizes to tune throughput vs. latency trade-offs
3. THE System SHALL write batches to disk immediately when full to free memory
4. WHERE multiple consumer instances are deployed, THE System SHALL ensure each instance uses a unique client ID and file counter
5. THE System SHALL support connection pooling for efficient resource usage
