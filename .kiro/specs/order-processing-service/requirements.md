# Requirements Document

## Introduction

The Order Processing Service is a distributed system that receives order requests via HTTP, publishes product information to an ActiveMQ message broker, and processes products in batches for persistent storage. The system consists of a Producer component that handles HTTP requests and publishes messages, and a Consumer component that batches and persists product data.

## Glossary

- **Order_Processing_Service**: The complete system including Producer, Consumer, and ActiveMQ infrastructure
- **Producer**: The HTTP web server component that receives order requests and publishes product messages
- **Consumer**: The message processing component that consumes product messages and writes batched data
- **ActiveMQ**: The message broker that facilitates communication between Producer and Consumer
- **Order**: A request containing an orderId and a list of productIds
- **ProductId**: A unique identifier for a product within an order
- **Batch**: A collection of productIds accumulated until reaching the batch size threshold
- **Topic**: An ActiveMQ publish-subscribe messaging destination

## Requirements

### Requirement 1: HTTP Order Reception

**User Story:** As a client application, I want to submit orders via HTTP, so that the system can process my order's products asynchronously.

#### Acceptance Criteria

1. THE Producer SHALL expose an HTTP endpoint that accepts order requests
2. WHEN an order request is received, THE Producer SHALL extract the orderId from the request
3. WHEN an order request is received, THE Producer SHALL extract the list of productIds from the request
4. IF the order request is missing orderId, THEN THE Producer SHALL return an HTTP 400 error response
5. IF the order request is missing productIds, THEN THE Producer SHALL return an HTTP 400 error response
6. WHEN a valid order request is received, THE Producer SHALL return an HTTP 202 accepted response

### Requirement 2: Product Message Publishing

**User Story:** As the Producer, I want to publish each productId to ActiveMQ, so that consumers can process products independently.

#### Acceptance Criteria

1. WHEN the Producer extracts productIds from an order, THE Producer SHALL publish each productId as a separate message to the ActiveMQ topic
2. FOR ALL productIds in an order, THE Producer SHALL include the orderId in each published message
3. WHEN publishing a message, THE Producer SHALL confirm the message was sent to ActiveMQ before processing the next productId
4. IF ActiveMQ is unavailable, THEN THE Producer SHALL return an HTTP 503 error response to the client

### Requirement 3: Product Message Consumption

**User Story:** As the Consumer, I want to receive productId messages from ActiveMQ, so that I can batch and process them.

#### Acceptance Criteria

1. THE Consumer SHALL subscribe to the ActiveMQ topic for product messages
2. WHEN a product message is received, THE Consumer SHALL extract the productId from the message
3. WHEN a product message is received, THE Consumer SHALL extract the orderId from the message
4. WHEN a product message is received, THE Consumer SHALL add the productId to the current batch
5. IF a message cannot be parsed, THEN THE Consumer SHALL log the error and acknowledge the message

### Requirement 4: Batch Processing and Persistence

**User Story:** As the Consumer, I want to write batches of products to separate files, so that processed products are persisted for downstream systems.

#### Acceptance Criteria

1. WHILE the batch size is less than 5, THE Consumer SHALL accumulate products (orderId and productId pairs) in memory
2. WHEN the batch size reaches 5 products, THE Consumer SHALL write all products in the batch to a new text file
3. THE Consumer SHALL name each batch file using the format "PRODUCT_ORDER_x.txt" where x is an incrementing counter starting from 1
4. THE Consumer SHALL increment the file counter after each batch is written
5. WHEN writing to the text file, THE Consumer SHALL format each line as "ORDER_ID|PRODUCT_ID" (pipe-delimited)
6. WHEN the batch is successfully written, THE Consumer SHALL clear the batch and start accumulating new products
7. IF the file write operation fails, THEN THE Consumer SHALL log the error and retry the write operation

### Requirement 5: ActiveMQ Infrastructure

**User Story:** As a system operator, I want to deploy ActiveMQ using Docker Compose, so that the messaging infrastructure is easily manageable.

#### Acceptance Criteria

1. THE Order_Processing_Service SHALL provide a Docker Compose configuration for ActiveMQ
2. WHEN Docker Compose is started, THE ActiveMQ broker SHALL be accessible to Producer and Consumer components
3. THE Docker_Compose_Configuration SHALL expose ActiveMQ management console on a configurable port
4. THE Docker_Compose_Configuration SHALL configure persistent storage for ActiveMQ messages
5. THE Docker_Compose_Configuration SHALL define the topic used by Producer and Consumer

### Requirement 6: Connection Management

**User Story:** As the system, I want to handle connection failures gracefully, so that temporary network issues don't cause data loss.

#### Acceptance Criteria

1. WHEN the Producer starts, THE Producer SHALL establish a connection to ActiveMQ before accepting HTTP requests
2. WHEN the Consumer starts, THE Consumer SHALL establish a connection to ActiveMQ before processing messages
3. IF the Producer loses connection to ActiveMQ, THEN THE Producer SHALL attempt to reconnect with exponential backoff
4. IF the Consumer loses connection to ActiveMQ, THEN THE Consumer SHALL attempt to reconnect with exponential backoff
5. WHILE reconnecting to ActiveMQ, THE Producer SHALL return HTTP 503 responses to incoming requests

### Requirement 7: Message Acknowledgment

**User Story:** As the Consumer, I want to acknowledge messages only after successful processing, so that no productIds are lost during failures.

#### Acceptance Criteria

1. WHEN a productId is added to the batch, THE Consumer SHALL acknowledge the message to ActiveMQ
2. IF adding a productId to the batch fails, THEN THE Consumer SHALL not acknowledge the message
3. WHEN the batch is written to file, THE Consumer SHALL have already acknowledged all messages in that batch
4. THE Consumer SHALL use manual acknowledgment mode rather than automatic acknowledgment

### Requirement 8: Configuration Management

**User Story:** As a system operator, I want to configure system parameters externally, so that I can adjust behavior without code changes.

#### Acceptance Criteria

1. THE Producer SHALL read the ActiveMQ broker URL from configuration
2. THE Producer SHALL read the HTTP server port from configuration
3. THE Producer SHALL read the topic name from configuration
4. THE Consumer SHALL read the ActiveMQ broker URL from configuration
5. THE Consumer SHALL read the topic name from configuration
6. THE Consumer SHALL read the output directory path from configuration
7. THE Consumer SHALL read the batch size from configuration

### Requirement 9: File Counter Persistence

**User Story:** As the Consumer, I want to resume file counter from existing files when restarting, so that file numbering continues sequentially without overwriting previous batches.

#### Acceptance Criteria

1. WHEN the Consumer starts, THE Consumer SHALL scan the output directory for existing PRODUCT_ORDER_*.txt files
2. WHEN existing files are found, THE Consumer SHALL extract the counter value from each file name
3. WHEN existing files are found, THE Consumer SHALL determine the maximum counter value from all existing files
4. WHEN existing files are found, THE Consumer SHALL initialize the file counter to (maximum counter value + 1)
5. WHEN no existing files are found, THE Consumer SHALL initialize the file counter to 1
6. IF the output directory does not exist, THEN THE Consumer SHALL create the directory and initialize the file counter to 1
7. IF the output directory is not readable, THEN THE Consumer SHALL log an error and fail to start
8. IF a file name cannot be parsed (corrupted or invalid format), THEN THE Consumer SHALL log a warning and skip that file when determining the maximum counter
9. WHEN the Consumer initializes the file counter, THE Consumer SHALL log the starting counter value

### Requirement 10: System Logging

**User Story:** As a system operator, I want comprehensive logging at each processing step, so that I can monitor system behavior and troubleshoot issues.

#### Acceptance Criteria

1. WHEN the Producer starts, THE Producer SHALL log the configuration parameters loaded
2. WHEN the Consumer starts, THE Consumer SHALL log the configuration parameters loaded
3. WHEN the Consumer starts, THE Consumer SHALL log the initialized file counter value
4. WHEN an order request is received, THE Producer SHALL log the orderId and the count of productIds
5. WHEN the Producer publishes a productId to ActiveMQ, THE Producer SHALL log the orderId and productId
6. WHEN the Producer establishes a connection to ActiveMQ, THE Producer SHALL log the connection event
7. WHEN the Producer loses connection to ActiveMQ, THE Producer SHALL log the disconnection event
8. WHEN the Producer reconnects to ActiveMQ, THE Producer SHALL log the reconnection event
9. WHEN the Consumer establishes a connection to ActiveMQ, THE Consumer SHALL log the connection event
10. WHEN the Consumer loses connection to ActiveMQ, THE Consumer SHALL log the disconnection event
11. WHEN the Consumer reconnects to ActiveMQ, THE Consumer SHALL log the reconnection event
12. WHEN the Consumer receives a product message, THE Consumer SHALL log the orderId and productId
13. WHEN the Consumer adds a product to the batch, THE Consumer SHALL log the current batch size
14. WHEN the Consumer writes a batch to file, THE Consumer SHALL log the batch size and file name
15. IF an error occurs during message publishing, THEN THE Producer SHALL log the error details including orderId and productId
16. IF an error occurs during message consumption, THEN THE Consumer SHALL log the error details including the message content
17. IF an error occurs during file write operations, THEN THE Consumer SHALL log the error details including the file name and batch size
18. IF an error occurs during file counter initialization, THEN THE Consumer SHALL log the error details including the directory path
