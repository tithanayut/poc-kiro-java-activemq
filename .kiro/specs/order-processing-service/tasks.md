# Implementation Plan: Order Processing Service

## Overview

This implementation plan breaks down the Order Processing Service into discrete coding tasks. The system consists of a Producer service (HTTP server publishing to ActiveMQ), a Consumer service (batch processor writing to files), and ActiveMQ infrastructure deployed via Docker Compose. Implementation will be in Java using Spring Boot for the Producer and a standalone Java application for the Consumer.

## Tasks

- [x] 1. Set up project structure and dependencies
  - Create Maven multi-module project with producer and consumer modules
  - Add Spring Boot dependencies for Producer (spring-boot-starter-web, spring-boot-starter-activemq)
  - Add ActiveMQ client dependencies for Consumer
  - Add logging dependencies (SLF4J with Logback)
  - Add testing dependencies (JUnit 5, jqwik for property-based testing, Mockito)
  - Create application.properties files for both modules
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7_

- [x] 2. Create Docker Compose configuration for ActiveMQ
  - Create docker-compose.yml with ActiveMQ Classic container configuration
  - Configure OpenWire port (61616) and web console port (8161)
  - Configure persistent volume for message storage
  - Add environment variables for memory settings
  - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [ ]* 2.1 Write unit test to verify Docker Compose file exists and has correct structure
  - Test file existence and YAML parsing
  - Verify port mappings and volume configuration
  - _Requirements: 5.1, 5.3, 5.4_

- [x] 3. Implement Producer data models and validation
  - [x] 3.1 Create OrderRequest DTO with orderId and productIds fields
    - Add Jackson annotations for JSON serialization
    - Add validation annotations (@NotNull, @NotEmpty)
    - _Requirements: 1.2, 1.3, 1.4, 1.5_
  
  - [ ]* 3.2 Write property test for OrderRequest parsing round-trip
    - **Property 1: Order Request Parsing Round-Trip**
    - **Validates: Requirements 1.2, 1.3**
  
  - [x] 3.3 Create ProductMessage class with orderId and productId fields
    - Add serialization support for ActiveMQ
    - _Requirements: 2.1, 2.2_
  
  - [ ]* 3.4 Write unit tests for OrderRequest validation
    - Test missing orderId returns 400
    - Test missing productIds returns 400
    - Test empty productIds array returns 400
    - _Requirements: 1.4, 1.5_

- [x] 4. Implement Producer ActiveMQ connection management
  - [x] 4.1 Create ActiveMQConnectionManager class
    - Implement connection establishment with broker URL from configuration
    - Implement connection monitoring and failure detection
    - Implement exponential backoff reconnection strategy (1s initial, 60s max, 2x multiplier)
    - Add connection state tracking (connected/reconnecting)
    - _Requirements: 6.1, 6.3, 8.1_
  
  - [x] 4.2 Add connection lifecycle logging
    - Log connection establishment with broker URL
    - Log disconnection events
    - Log reconnection attempts and success
    - _Requirements: 10.1, 10.6, 10.7, 10.8_
  
  - [ ]* 4.3 Write property test for exponential backoff timing
    - **Property 15: Exponential Backoff Timing**
    - **Validates: Requirements 6.3, 6.4**
  
  - [ ]* 4.4 Write unit tests for connection failure scenarios
    - Test connection unavailable at startup triggers retry
    - Test connection loss during operation triggers reconnection
    - _Requirements: 6.1, 6.3_

- [x] 5. Implement Producer HTTP endpoint and order processing
  - [x] 5.1 Create OrderController with POST /orders endpoint
    - Accept OrderRequest in request body
    - Return 202 Accepted for valid requests
    - Return 400 Bad Request for validation failures
    - Return 503 Service Unavailable when ActiveMQ disconnected
    - _Requirements: 1.1, 1.4, 1.5, 1.6, 2.4, 6.5_
  
  - [x] 5.2 Implement order processing logic in OrderService
    - Extract orderId and productIds from request
    - Log received order with orderId and productIds count
    - Iterate through productIds and publish each as separate message
    - Include orderId in each ProductMessage
    - Confirm each message sent before processing next
    - _Requirements: 1.2, 1.3, 2.1, 2.2, 2.3, 10.4_
  
  - [x] 5.3 Implement message publishing with ActiveMQTemplate
    - Publish ProductMessage to configured topic name
    - Log each published message with orderId and productId
    - Handle publish failures and return 503 to client
    - _Requirements: 2.1, 2.3, 2.4, 8.3, 10.5_
  
  - [ ]* 5.4 Write property test for valid order acceptance
    - **Property 2: Valid Order Acceptance**
    - **Validates: Requirements 1.6**
  
  - [ ]* 5.5 Write property test for message count equals product count
    - **Property 3: Message Count Equals Product Count**
    - **Validates: Requirements 2.1**
  
  - [ ]* 5.6 Write property test for orderId propagation in messages
    - **Property 4: OrderId Propagation in Messages**
    - **Validates: Requirements 2.2**
  
  - [ ]* 5.7 Write property test for sequential message confirmation
    - **Property 5: Sequential Message Confirmation**
    - **Validates: Requirements 2.3**
  
  - [ ]* 5.8 Write property test for service unavailable during reconnection
    - **Property 16: Service Unavailable During Reconnection**
    - **Validates: Requirements 6.5**

- [ ] 6. Checkpoint - Ensure Producer tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Implement Consumer file counter initialization
  - [x] 7.1 Create FileCounterInitializer class
    - Implement algorithm to scan output directory for PRODUCT_ORDER_*.txt files
    - Parse counter values from file names using regex pattern
    - Handle missing directory by creating it and returning counter 1
    - Handle unreadable directory by logging error and failing startup
    - Skip files with invalid names and log warnings
    - Return max counter + 1, or 1 if no valid files found
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7, 9.8_
  
  - [x] 7.2 Add file counter initialization logging
    - Log output directory path at startup
    - Log each valid file found with counter value
    - Log warnings for invalid file names
    - Log final initialized counter value
    - _Requirements: 9.9, 10.2, 10.3, 10.18_
  
  - [ ]* 7.3 Write property test for file counter initialization from existing files
    - **Property 12: File Counter Initialization from Existing Files**
    - **Validates: Requirements 9.1, 9.2, 9.3, 9.4**
  
  - [ ]* 7.4 Write property test for invalid file name handling
    - **Property 13: Invalid File Name Handling**
    - **Validates: Requirements 9.8**
  
  - [ ]* 7.5 Write property test for file counter initialization logging
    - **Property 14: File Counter Initialization Logging**
    - **Validates: Requirements 9.9**
  
  - [ ]* 7.6 Write unit tests for file counter edge cases
    - Test empty directory initializes to 1
    - Test directory with gaps in sequence (1, 3, 7) initializes to 8
    - Test mixed valid and invalid files
    - Test unreadable directory fails startup
    - _Requirements: 9.5, 9.6, 9.7, 9.8_

- [x] 8. Implement Consumer data models and batch state
  - [x] 8.1 Create Product class with orderId and productId fields
    - Add constructor and getters
    - _Requirements: 3.2, 3.3_
  
  - [x] 8.2 Create BatchState class to manage in-memory batch
    - Store list of Product objects
    - Track current batch size
    - Store max batch size from configuration
    - Store current file counter value
    - Implement addProduct method
    - Implement isFull method (size >= maxSize)
    - Implement clear method
    - Implement getFileCounter and incrementFileCounter methods
    - _Requirements: 3.4, 4.1, 4.2, 4.4, 4.6_

- [x] 9. Implement Consumer ActiveMQ connection and message consumption
  - [x] 9.1 Create ConsumerConnectionManager class
    - Implement connection establishment with broker URL from configuration
    - Implement connection monitoring and failure detection
    - Implement exponential backoff reconnection strategy
    - Preserve batch state during reconnection
    - _Requirements: 6.2, 6.4, 8.4_
  
  - [x] 9.2 Create MessageConsumer class with topic subscription
    - Subscribe to configured topic name
    - Use manual acknowledgment mode
    - _Requirements: 3.1, 7.4, 8.5_
  
  - [x] 9.3 Implement message parsing and extraction
    - Parse ProductMessage from received message
    - Extract orderId and productId
    - Log received message with orderId and productId
    - Handle unparseable messages by logging error and acknowledging
    - _Requirements: 3.2, 3.3, 3.5, 10.12_
  
  - [ ]* 9.4 Write property test for message parsing round-trip
    - **Property 6: Message Parsing Round-Trip**
    - **Validates: Requirements 3.2, 3.3**
  
  - [ ]* 9.5 Write unit tests for message parsing errors
    - Test unparseable message is acknowledged
    - Test missing orderId is handled
    - Test missing productId is handled
    - _Requirements: 3.5_

- [x] 10. Implement Consumer batch processing and file writing
  - [x] 10.1 Implement batch accumulation logic
    - Add product to BatchState when message received
    - Log current batch size after addition
    - Acknowledge message to ActiveMQ after successful add
    - Do not acknowledge if add fails
    - _Requirements: 3.4, 4.1, 7.1, 7.2, 10.13_
  
  - [x] 10.2 Implement batch write logic
    - Check if batch is full (size >= threshold)
    - Create file name using format PRODUCT_ORDER_{counter}.txt
    - Write each product as ORDER_ID|PRODUCT_ID line
    - Log batch size and file name when writing
    - Increment file counter after successful write
    - Clear batch after successful write
    - _Requirements: 4.2, 4.3, 4.4, 4.5, 4.6, 10.14_
  
  - [x] 10.3 Implement file write error handling
    - Retry file write up to 3 times with 1-second delay
    - Log error with file name and batch size on failure
    - Acknowledge messages after retry exhaustion to prevent infinite redelivery
    - _Requirements: 4.7, 10.17_
  
  - [ ]* 10.4 Write property test for batch accumulation before threshold
    - **Property 7: Batch Accumulation Before Threshold**
    - **Validates: Requirements 4.1**
  
  - [ ]* 10.5 Write property test for batch write at threshold
    - **Property 8: Batch Write at Threshold**
    - **Validates: Requirements 4.2, 4.3**
  
  - [ ]* 10.6 Write property test for file format correctness
    - **Property 9: File Format Correctness**
    - **Validates: Requirements 4.5**
  
  - [ ]* 10.7 Write property test for batch clear after write
    - **Property 10: Batch Clear After Write**
    - **Validates: Requirements 4.4, 4.6**
  
  - [ ]* 10.8 Write property test for file counter increment
    - **Property 11: File Counter Increment**
    - **Validates: Requirements 4.3, 4.4**
  
  - [ ]* 10.9 Write property test for message acknowledgment after batch add
    - **Property 17: Message Acknowledgment After Batch Add**
    - **Validates: Requirements 7.1**
  
  - [ ]* 10.10 Write property test for acknowledgment before file write
    - **Property 18: Acknowledgment Before File Write**
    - **Validates: Requirements 7.3**
  
  - [ ]* 10.11 Write unit tests for file write failures
    - Test file write retry logic
    - Test error logging on failure
    - _Requirements: 4.7, 10.17_

- [ ] 11. Checkpoint - Ensure Consumer tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 12. Implement configuration management
  - [x] 12.1 Create Producer application.properties
    - Define activemq.broker.url property
    - Define server.port property
    - Define activemq.topic.name property
    - _Requirements: 8.1, 8.2, 8.3_
  
  - [x] 12.2 Create Consumer application.properties
    - Define activemq.broker.url property
    - Define activemq.topic.name property
    - Define output.directory.path property
    - Define batch.size property
    - _Requirements: 8.4, 8.5, 8.6, 8.7_
  
  - [x] 12.3 Add configuration loading and logging at startup
    - Log all configuration parameters when Producer starts
    - Log all configuration parameters when Consumer starts
    - _Requirements: 10.1, 10.2_
  
  - [ ]* 12.4 Write property test for configuration value propagation
    - **Property 19: Configuration Value Propagation**
    - **Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7**

- [x] 13. Implement comprehensive logging
  - [x] 13.1 Add connection lifecycle logging to both services
    - Log connection establishment events
    - Log disconnection events
    - Log reconnection events
    - _Requirements: 10.6, 10.7, 10.8, 10.9, 10.10, 10.11_
  
  - [x] 13.2 Add error logging for all error scenarios
    - Log message publishing errors with orderId and productId
    - Log message consumption errors with message content
    - Log file write errors with file name and batch size
    - Log file counter initialization errors with directory path
    - Include timestamps, component names, severity, and stack traces
    - _Requirements: 10.15, 10.16, 10.17, 10.18_
  
  - [ ]* 13.3 Write property tests for logging requirements
    - **Property 20: Order Request Logging**
    - **Property 21: Message Publication Logging**
    - **Property 22: Message Reception Logging**
    - **Property 23: Batch Addition Logging**
    - **Property 24: Batch Write Logging**
    - **Validates: Requirements 10.4, 10.5, 10.12, 10.13, 10.14**

- [x] 14. Create main application classes and startup logic
  - [x] 14.1 Create ProducerApplication with Spring Boot main method
    - Initialize ActiveMQConnectionManager before starting HTTP server
    - Wait for ActiveMQ connection before accepting requests
    - _Requirements: 6.1_
  
  - [x] 14.2 Create ConsumerApplication with main method
    - Initialize FileCounterInitializer to scan directory and set starting counter
    - Initialize BatchState with file counter from initializer
    - Initialize ConsumerConnectionManager
    - Wait for ActiveMQ connection before starting message consumption
    - Start MessageConsumer with BatchState
    - _Requirements: 6.2, 9.1, 9.2, 9.3, 9.4_

- [ ]* 15. Write integration tests for end-to-end flows
  - Test complete flow: HTTP request → ActiveMQ → File write with correct naming
  - Test multiple batches create separate numbered files (PRODUCT_ORDER_1.txt, PRODUCT_ORDER_2.txt, etc.)
  - Test Consumer restart with existing files continues numbering correctly
  - Test connection recovery preserves batch state and file counter
  - _Requirements: All requirements_

- [x] 16. Create README with setup and running instructions
  - Document prerequisites (Java, Maven, Docker)
  - Document how to start ActiveMQ with Docker Compose
  - Document how to build and run Producer
  - Document how to build and run Consumer
  - Document configuration options
  - Document example API requests
  - Document how to verify output files

- [ ] 17. Final checkpoint - Ensure all tests pass
  - Run all unit tests and property tests
  - Run integration tests with ActiveMQ
  - Verify Producer and Consumer can start and process orders end-to-end
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples, edge cases, and error conditions
- File naming follows the pattern PRODUCT_ORDER_1.txt, PRODUCT_ORDER_2.txt, etc.
- File content format is ORDER_ID|PRODUCT_ID (pipe-delimited)
- File counter initialization ensures sequential numbering across Consumer restarts
- Implementation uses Java with Spring Boot for Producer and standalone Java for Consumer
- ActiveMQ Classic is deployed via Docker Compose for easy local development
