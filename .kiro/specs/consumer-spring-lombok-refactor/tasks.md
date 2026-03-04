# Implementation Plan: Consumer Spring and Lombok Refactor

## Overview

This implementation plan refactors the existing consumer application to use Spring Framework and Lombok. The migration follows an 8-phase approach: adding dependencies, refactoring models, creating configuration, refactoring services, refactoring messaging, updating the application entry point, adding tests, and final validation. Each task builds incrementally to maintain a working application throughout the refactoring process.

## Tasks

- [x] 1. Add Spring Boot, Spring JMS, and Lombok dependencies
  - Update pom.xml to add spring-boot-starter (3.2.0)
  - Add spring-boot-starter-activemq (3.2.0)
  - Add lombok (1.18.30) with provided scope
  - Add spring-boot-starter-test (3.2.0) with test scope
  - Remove direct ActiveMQ client dependency (replaced by Spring Boot starter)
  - Verify Maven build succeeds with new dependencies
  - _Requirements: 7.1, 12.1, 12.2, 12.3, 12.4, 12.5_

- [x] 2. Create and refactor data models with Lombok
  - [x] 2.1 Refactor Product class with Lombok annotations
    - Add @Data annotation to generate getters, setters, toString, equals, hashCode
    - Add @AllArgsConstructor to generate constructor with all fields
    - Add @NoArgsConstructor for Jackson compatibility
    - Remove manually written getters, setters, toString, equals, hashCode
    - _Requirements: 12.1, 12.3, 12.4, 11.1, 11.2_
  
  - [x] 2.2 Create ProductMessage DTO class
    - Create ProductMessage class with orderId and productId fields
    - Add @Data annotation for boilerplate code generation
    - Add @NoArgsConstructor for Jackson deserialization
    - Add @AllArgsConstructor for convenience constructor
    - _Requirements: 12.2, 12.4, 11.3, 11.4_
  
  - [x]* 2.3 Write property test for message deserialization round-trip
    - **Property 2: Message Deserialization Round-Trip**
    - **Validates: Requirements 1.3**
    - Generate random ProductMessage objects, serialize to JSON, deserialize back, verify equivalence

- [x] 3. Create Spring configuration classes
  - [x] 3.1 Create application.properties configuration file
    - Add activemq.broker.url property
    - Add activemq.client.id property
    - Add activemq.topic.name property
    - Add activemq.subscription.name property
    - Add batch.size property
    - Add output.directory.path property
    - Add file.writer.max-retry-attempts property (default: 3)
    - Add file.writer.retry-delay-ms property (default: 1000)
    - _Requirements: 6.1, 6.2, 6.3_
  
  - [x] 3.2 Create JmsConfig configuration class
    - Create JmsConfig class with @Configuration annotation
    - Implement connectionFactory bean with @Value injection for broker URL and client ID
    - Implement jmsListenerContainerFactory bean with CLIENT_ACKNOWLEDGE mode
    - Implement messageConverter bean using Jackson for JSON deserialization
    - _Requirements: 7.3, 13.1, 13.2, 13.3_
  
  - [x]* 3.3 Write unit tests for JmsConfig
    - Test connectionFactory bean creation with injected properties
    - Test jmsListenerContainerFactory configuration
    - Test messageConverter bean creation
    - _Requirements: 13.1, 13.2, 13.3_

- [x] 4. Checkpoint - Ensure configuration loads correctly
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Create file management services
  - [x] 5.1 Create FileCounterService from FileCounterInitializer
    - Create FileCounterService class with @Service annotation
    - Add constructor with @Value injection for output.directory.path
    - Implement @PostConstruct initializeCounter method to scan existing files
    - Parse PRODUCT_ORDER_*.txt file names to find maximum counter
    - Initialize counter to max + 1
    - Implement getNextCounter method with thread-safe increment
    - Add @Slf4j for logging
    - _Requirements: 4.1, 4.2, 4.3, 7.2, 8.4, 12.5_
  
  - [ ]* 5.2 Write property test for file counter initialization
    - **Property 10: File Counter Initialization Correctness**
    - **Validates: Requirements 4.2, 4.3**
    - Generate random sets of existing batch files, verify counter initializes to max + 1
  
  - [x] 5.3 Create FileWriterService from BatchFileWriter
    - Create FileWriterService class with @Service annotation
    - Add constructor with @Value injection for output.directory.path, max-retry-attempts, retry-delay-ms
    - Implement @PostConstruct initializeOutputDirectory method
    - Implement writeBatch method with retry logic (up to 3 attempts, 1-second delay)
    - Format products as pipe-delimited lines: orderId|productId
    - Generate file names as PRODUCT_ORDER_{counter}.txt
    - Add comprehensive error logging for I/O failures
    - Add @Slf4j for logging
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 5.1, 5.2, 5.3, 5.4, 7.2, 9.2, 9.3, 12.5_
  
  - [ ]* 5.4 Write property test for file write atomicity
    - **Property 5: Batch Write Atomicity**
    - **Validates: Requirements 3.6**
    - Generate random batch writes with simulated failures, verify all-or-nothing writes
  
  - [ ]* 5.5 Write property test for file name format consistency
    - **Property 6: File Name Format Consistency**
    - **Validates: Requirements 3.1**
    - Generate random file counter values, verify file names match PRODUCT_ORDER_N.txt format
  
  - [ ]* 5.6 Write property test for product formatting consistency
    - **Property 7: Product Formatting Consistency**
    - **Validates: Requirements 3.2**
    - Generate random products, verify formatted lines match orderId|productId format
  
  - [ ]* 5.7 Write unit tests for FileWriterService
    - Test successful file write
    - Test retry logic with transient failures
    - Test failure after max retries
    - Test directory creation on startup
    - _Requirements: 3.3, 3.4, 5.2_

- [x] 6. Create batch management components
  - [x] 6.1 Create BatchState component
    - Create BatchState class with @Component annotation
    - Add @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE) for multi-consumer scenarios
    - Add constructor with FileCounterService dependency injection
    - Implement synchronized addProduct method
    - Implement synchronized isFull method
    - Implement synchronized getProducts method
    - Implement synchronized getCurrentSize method
    - Implement synchronized clear method
    - Implement synchronized getFileCounter method
    - Implement synchronized incrementFileCounter method
    - _Requirements: 2.1, 2.4, 7.1, 8.1, 8.2, 8.3_
  
  - [ ]* 6.2 Write property test for batch size invariant
    - **Property 4: Batch Size Invariant**
    - **Validates: Requirements 2.4**
    - Generate random sequences of product additions, verify batch size never exceeds maximum
  
  - [ ]* 6.3 Write property test for thread-safe batch operations
    - **Property 12: Thread-Safe Batch Operations**
    - **Validates: Requirements 8.1**
    - Generate concurrent product additions, verify final batch state is consistent
  
  - [x] 6.4 Create BatchService to coordinate batch operations
    - Create BatchService class with @Service annotation
    - Add constructor with BatchState, FileWriterService, and @Value(batch.size) injection
    - Implement synchronized addProduct method
    - Check if batch is full after each addition
    - Trigger FileWriterService.writeBatch when batch is full
    - Clear BatchState and increment file counter after successful write
    - Implement synchronized flush method for graceful shutdown
    - Add @Slf4j for logging
    - _Requirements: 2.1, 2.2, 2.3, 2.5, 7.1, 7.2, 8.2, 10.2, 12.5_
  
  - [ ]* 6.5 Write property test for file counter monotonicity
    - **Property 8: File Counter Monotonicity**
    - **Validates: Requirements 4.4, 4.6**
    - Generate random batch writes, verify counter strictly increases
  
  - [ ]* 6.6 Write property test for file name uniqueness
    - **Property 9: File Name Uniqueness**
    - **Validates: Requirements 4.5**
    - Generate random batch writes, verify no duplicate file names
  
  - [ ]* 6.7 Write unit tests for BatchService
    - Test product addition to batch
    - Test batch fullness detection
    - Test file write trigger when batch is full
    - Test batch clearing after successful write
    - Test flush method for graceful shutdown
    - _Requirements: 2.1, 2.2, 2.3, 2.5, 10.2_

- [x] 7. Checkpoint - Ensure batch management works correctly
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Create message listener component
  - [x] 8.1 Create MessageListener from MessageConsumer
    - Create MessageListener class with @Component annotation
    - Add constructor with BatchService dependency injection
    - Implement onMessage method with @JmsListener annotation
    - Configure @JmsListener with destination=${activemq.topic.name}
    - Configure @JmsListener with subscription=${activemq.subscription.name}
    - Configure @JmsListener with containerFactory=jmsListenerContainerFactory
    - Deserialize JSON messages to ProductMessage objects
    - Convert ProductMessage to Product domain model
    - Delegate product processing to BatchService
    - Acknowledge messages after successful processing
    - Handle deserialization errors with logging and acknowledgment
    - Add @Slf4j for logging
    - _Requirements: 1.1, 1.3, 1.4, 1.5, 7.1, 9.1, 12.5, 13.4_
  
  - [ ]* 8.2 Write property test for message acknowledgment guarantee
    - **Property 1: Message Acknowledgment Guarantee**
    - **Validates: Requirements 1.4**
    - Generate random message processing scenarios, verify acknowledgment only on success
  
  - [ ]* 8.3 Write property test for invalid message error handling
    - **Property 3: Invalid Message Error Handling**
    - **Validates: Requirements 1.5**
    - Generate malformed JSON messages, verify error logging, acknowledgment, and continued processing
  
  - [ ]* 8.4 Write unit tests for MessageListener
    - Test successful message deserialization and processing
    - Test message acknowledgment after successful processing
    - Test deserialization error handling
    - Test ProductMessage to Product conversion
    - _Requirements: 1.3, 1.4, 1.5_

- [x] 9. Refactor application entry point
  - [x] 9.1 Convert ConsumerApplication to Spring Boot application
    - Add @SpringBootApplication annotation to ConsumerApplication
    - Add @EnableJms annotation to enable JMS listener support
    - Replace manual initialization code with SpringApplication.run
    - Remove manual component instantiation (replaced by Spring DI)
    - Add shutdown hook to flush in-progress batches
    - _Requirements: 7.4, 7.5, 10.1, 10.2, 10.3, 10.4_
  
  - [ ]* 9.2 Write integration test for application startup
    - Test application context loads successfully
    - Test all beans are created correctly
    - Test configuration properties are injected
    - _Requirements: 6.1, 6.2, 7.1, 7.2, 7.3_
  
  - [ ]* 9.3 Write property test for configuration validation
    - **Property 11: Configuration Validation Completeness**
    - **Validates: Requirements 6.4, 6.5**
    - Generate configurations with missing or invalid properties, verify startup failure

- [x] 10. Add integration tests with embedded ActiveMQ
  - [-]* 10.1 Write end-to-end message flow integration test
    - Start embedded ActiveMQ broker
    - Publish test messages to topic
    - Verify batch files created with correct content
    - Verify file counter increments correctly
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 3.1, 3.2, 4.4_
  
  - [ ]* 10.2 Write durable subscription persistence integration test
    - Publish messages while consumer is stopped
    - Start consumer
    - Verify all messages are delivered and processed
    - _Requirements: 1.1, 1.2, 14.1, 14.2_
  
  - [ ]* 10.3 Write graceful shutdown integration test
    - Send messages during shutdown
    - Verify in-progress batch is flushed
    - Verify no message loss
    - _Requirements: 10.1, 10.2, 10.3, 10.4_
  
  - [ ]* 10.4 Write ActiveMQ connection recovery integration test
    - Simulate connection loss
    - Verify automatic reconnection
    - Verify message processing resumes
    - _Requirements: 14.1, 14.2, 14.3, 14.4_

- [ ] 11. Add product validation
  - [x] 11.1 Add validation to Product class
    - Add validation logic to ensure orderId is not null or empty
    - Add validation logic to ensure productId is not null or empty
    - Throw IllegalArgumentException for invalid data
    - _Requirements: 11.1, 11.2_
  
  - [ ]* 11.2 Write property test for product validation
    - **Property 13: Product Validation**
    - **Validates: Requirements 11.1, 11.2, 11.3, 11.4**
    - Generate products with null or empty fields, verify validation errors
  
  - [ ]* 11.3 Write unit tests for Product validation
    - Test validation with null orderId
    - Test validation with empty orderId
    - Test validation with null productId
    - Test validation with empty productId
    - Test successful creation with valid data
    - _Requirements: 11.1, 11.2_

- [ ] 12. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 13. Update logging and error handling
  - [ ] 13.1 Add comprehensive logging throughout application
    - Add startup completion logging with configuration summary
    - Add connection loss and reconnection logging
    - Ensure all error scenarios have appropriate logging
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_
  
  - [x] 13.2 Verify error handling for all scenarios
    - Verify message deserialization error handling
    - Verify file write error handling with retries
    - Verify output directory validation on startup
    - Verify configuration validation on startup
    - _Requirements: 1.5, 3.3, 3.4, 5.3, 6.4, 6.5_

- [ ] 14. Final integration and validation
  - [x] 14.1 Test with real ActiveMQ broker
    - Configure connection to real ActiveMQ broker
    - Publish test messages
    - Verify durable subscription behavior
    - Verify batch file creation and content
    - Verify file counter persistence across restarts
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 3.1, 3.2, 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_
  
  - [ ] 14.2 Performance testing with high message volume
    - Publish large volume of messages (10,000+)
    - Verify throughput and memory usage
    - Verify no message loss
    - Verify batch files created correctly
    - _Requirements: 15.1, 15.2, 15.3_
  
  - [ ] 14.3 Test horizontal scaling with multiple consumer instances
    - Deploy multiple consumer instances with unique client IDs
    - Verify each instance processes messages independently
    - Verify file counter uniqueness across instances
    - _Requirements: 15.4_

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at key milestones
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- Integration tests validate end-to-end flows with real components
- The refactoring maintains all existing functionality while modernizing the codebase
- Use application.properties format (not YAML) for configuration
- All components use constructor-based dependency injection
- Lombok eliminates boilerplate code for models and logging
