# Implementation Plan: Java to C# Migration

## Overview

This implementation plan converts the Java Spring Boot order processing system to C# .NET. The migration creates two independent services: a Producer (ASP.NET Core Web API) that receives HTTP orders and publishes product messages to ActiveMQ, and a Consumer (.NET Worker Service) that processes messages in batches and writes them to numbered pipe-delimited files. The implementation follows .NET conventions while maintaining functional equivalence with the Java system.

## Tasks

- [x] 1. Set up .NET solution structure and Docker infrastructure
  - Create .NET solution file with Producer and Consumer projects
  - Create ASP.NET Core Web API project for Producer service
  - Create .NET Worker Service project for Consumer service
  - Create docker-compose.yml for ActiveMQ Classic with OpenWire (61616) and web console (8161) ports
  - Add Apache.NMS.ActiveMQ NuGet packages to both projects
  - Add Microsoft.Extensions.Configuration and Microsoft.Extensions.Logging packages
  - Configure projects to target .NET 8.0
  - _Requirements: 15.1, 15.2, 15.3, 15.4, 17.1, 17.2, 17.3, 17.4, 17.5, 18.1, 18.2, 18.5, 18.6_

- [ ] 2. Implement Producer service data models and configuration
  - [x] 2.1 Create data models for Producer service
    - Create OrderRequest class with OrderId and ProductIds properties
    - Add validation attributes: [Required] for OrderId, [Required][MinLength(1)] for ProductIds
    - Create ProductMessage class with OrderId and ProductId properties
    - _Requirements: 1.3, 1.4, 1.5, 2.2, 2.3_

  - [x] 2.2 Create configuration models for Producer service
    - Create ProducerSettings class with BrokerUrl, TopicName, and Retry properties
    - Create ConnectionRetrySettings class with InitialDelayMs (1000), MaxDelayMs (60000), BackoffMultiplier (2.0)
    - Create appsettings.json with Producer configuration section
    - Configure logging levels for ASP.NET Core
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 4.2, 4.3, 4.4_

  - [ ]* 2.3 Write property test for message serialization round-trip
    - **Property 6: Message Serialization Round-Trip**
    - **Validates: Requirements 6.1**

- [ ] 3. Implement Producer ConnectionManager with retry logic
  - [x] 3.1 Create IConnectionManager interface and implementation
    - Implement GetConnectionAsync() method returning IConnection
    - Implement ReconnectAsync() method with exponential backoff logic
    - Add IsConnected property to track connection state
    - Use exponential backoff: delay = min(initialDelay * (multiplier ^ attempt), maxDelay)
    - Register ExceptionListener to detect connection loss and trigger reconnection
    - Implement thread-safe connection access using lock or SemaphoreSlim
    - Log each reconnection attempt with current delay
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.6, 3.3_

  - [ ]* 3.2 Write unit tests for ConnectionManager retry logic
    - Test exponential backoff calculation
    - Test connection state tracking
    - Test automatic reconnection on connection loss
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [ ] 4. Implement Producer MessagePublisher
  - [x] 4.1 Create IMessagePublisher interface and implementation
    - Implement PublishProductMessagesAsync(orderId, productIds) method
    - Add IsConnected property delegating to ConnectionManager
    - Create ISession and IMessageProducer from connection
    - Serialize ProductMessage to JSON using System.Text.Json
    - Publish each product as separate message to configured topic
    - Log message publishing operations at DEBUG level
    - Handle publishing failures and log errors
    - _Requirements: 2.1, 2.2, 2.3, 3.1, 3.2, 3.4, 16.3_

  - [ ]* 4.2 Write property test for order decomposition count
    - **Property 2: Order Decomposition Count**
    - **Validates: Requirements 2.1**

  - [ ]* 4.3 Write property test for order ID consistency
    - **Property 3: Order ID Consistency**
    - **Validates: Requirements 2.2**

  - [ ]* 4.4 Write property test for message JSON structure
    - **Property 4: Message JSON Structure**
    - **Validates: Requirements 2.3**

- [ ] 5. Implement Producer OrderController HTTP API
  - [x] 5.1 Create OrderController with POST endpoint
    - Create [ApiController] with [Route("orders")]
    - Implement POST endpoint accepting OrderRequest from body
    - Return 202 Accepted for valid requests
    - Return 400 Bad Request for validation failures (handled by model validation)
    - Return 503 Service Unavailable when ActiveMQ connection is unavailable
    - Inject IMessagePublisher and ILogger dependencies
    - Log incoming order requests with orderId and product count
    - Call PublishProductMessagesAsync to publish messages
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 4.5, 16.2, 18.3_

  - [ ]* 5.2 Write property test for valid order acceptance
    - **Property 1: Valid Order Acceptance**
    - **Validates: Requirements 1.2**

  - [ ]* 5.3 Write property test for disconnected state rejection
    - **Property 5: Disconnected State Rejection**
    - **Validates: Requirements 4.5**

  - [ ]* 5.4 Write unit tests for OrderController validation
    - Test missing orderId returns 400
    - Test missing productIds returns 400
    - Test empty productIds array returns 400
    - _Requirements: 1.3, 1.4, 1.5_

- [x] 6. Configure Producer service startup and dependency injection
  - Register IConnectionManager and IMessagePublisher as singletons
  - Bind ProducerSettings from configuration
  - Configure JSON serialization options
  - Configure logging providers
  - Configure HTTP server port from configuration
  - Log successful startup with broker URL and topic name
  - _Requirements: 13.1, 13.2, 13.3, 13.4, 16.1_

- [x] 7. Checkpoint - Ensure Producer service builds and starts
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 8. Implement Consumer service data models and configuration
  - [x] 8.1 Create data models for Consumer service
    - Create Product class with OrderId and ProductId properties
    - _Requirements: 6.2, 6.3_

  - [x] 8.2 Create configuration models for Consumer service
    - Create ConsumerSettings class with BrokerUrl, TopicName, ClientId, SubscriptionName, BatchSize, OutputDirectory
    - Create FileWriterSettings class with MaxRetryAttempts (3) and RetryDelayMs (1000)
    - Create appsettings.json with Consumer configuration section
    - Configure logging levels
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 14.7, 14.8, 11.2, 11.3_

- [ ] 9. Implement Consumer FileCounterManager
  - [x] 9.1 Create IFileCounterManager interface and implementation
    - Implement GetNextCounter() method returning current counter
    - Implement IncrementCounter() method to increment by 1
    - On initialization, scan output directory for PRODUCT_ORDER_*.txt files
    - Parse filenames using regex to extract numeric counter values
    - Set initial counter to max counter + 1, or 1 if no files exist
    - Log warnings for unparseable filenames
    - Log initialized counter value at INFO level
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 16.6_

  - [ ]* 9.2 Write property test for file counter initialization
    - **Property 10: File Counter Initialization**
    - **Validates: Requirements 9.2, 9.3**

  - [ ]* 9.3 Write unit tests for FileCounterManager edge cases
    - Test no existing files returns counter 1
    - Test unparseable filenames are skipped with warning
    - _Requirements: 9.4, 9.5_

- [ ] 10. Implement Consumer FileWriter with retry logic
  - [x] 10.1 Create IFileWriter interface and implementation
    - Implement WriteProductsAsync(products, fileCounter) method
    - Format filename as PRODUCT_ORDER_{counter}.txt
    - Create output directory if it doesn't exist
    - Format each product as pipe-delimited: orderId|productId
    - Use File.WriteAllLinesAsync for async I/O
    - Implement retry logic: max 3 attempts with 1000ms delay between retries
    - Log file write operations at INFO level with filename and product count
    - Log warnings for retry attempts
    - Log errors when max retries exhausted
    - _Requirements: 10.1, 10.2, 10.3, 10.5, 11.1, 11.2, 11.3, 11.4, 11.5, 16.7, 16.8_

  - [ ]* 10.2 Write property test for file naming convention
    - **Property 11: File Naming Convention**
    - **Validates: Requirements 10.1**

  - [ ]* 10.3 Write property test for pipe-delimited format
    - **Property 13: Pipe-Delimited Format**
    - **Validates: Requirements 10.5**

  - [ ]* 10.4 Write unit tests for FileWriter retry logic
    - Test successful write on first attempt
    - Test retry on transient failure
    - Test failure after max retries
    - _Requirements: 11.1, 11.2, 11.3, 11.4_

- [ ] 11. Implement Consumer BatchProcessor
  - [x] 11.1 Create IBatchProcessor interface and implementation
    - Implement AddProductAsync(product) method
    - Implement FlushAsync() method to write partial batches
    - Maintain in-memory List<Product> with configured batch size
    - When batch reaches size, call FileWriter.WriteProductsAsync
    - After successful write, increment file counter and clear batch
    - Implement thread-safe batch operations using lock or SemaphoreSlim
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 10.4_

  - [ ]* 11.2 Write property test for batch accumulation
    - **Property 7: Batch Accumulation**
    - **Validates: Requirements 7.1, 7.3**

  - [ ]* 11.3 Write property test for batch clearing after write
    - **Property 8: Batch Clearing After Write**
    - **Validates: Requirements 7.4**

  - [ ]* 11.4 Write property test for file counter increment
    - **Property 12: File Counter Increment**
    - **Validates: Requirements 10.4**

  - [ ]* 11.5 Write unit tests for BatchProcessor edge cases
    - Test batch write triggered at exact batch size
    - Test partial batch flush on shutdown
    - _Requirements: 7.3, 12.2_

- [ ] 12. Implement Consumer MessageConsumer with durable subscription
  - [x] 12.1 Create IMessageConsumer interface and implementation
    - Implement StartAsync(cancellationToken) method
    - Implement StopAsync(cancellationToken) method
    - Create connection to ActiveMQ using configured broker URL
    - Create durable subscription using clientId and subscriptionName
    - Use manual acknowledgment mode (AcknowledgementMode.ClientAcknowledge)
    - Deserialize JSON messages to Product objects using System.Text.Json
    - Call BatchProcessor.AddProductAsync for each product
    - Acknowledge messages only after successful batch addition
    - Log errors for deserialization failures and reject messages (no acknowledgment)
    - Log received messages at DEBUG level with orderId and productId
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 6.1, 6.2, 6.3, 6.4, 8.1, 8.2, 8.3, 8.4, 16.6_

  - [ ]* 12.2 Write property test for message acknowledgment on success
    - **Property 9: Message Acknowledgment on Success**
    - **Validates: Requirements 8.2**

  - [ ]* 12.3 Write unit tests for MessageConsumer error handling
    - Test deserialization failure rejects message
    - Test missing required fields rejects message
    - Test successful processing acknowledges message
    - _Requirements: 6.4, 8.2, 8.3_

- [ ] 13. Implement Consumer graceful shutdown
  - [x] 13.1 Add graceful shutdown logic to MessageConsumer
    - On shutdown signal, call BatchProcessor.FlushAsync to write partial batch
    - Skip file write if batch is empty
    - Close ActiveMQ connection before exiting
    - Log shutdown operation at INFO level with batch status
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 16.9_

  - [ ]* 13.2 Write unit tests for graceful shutdown
    - Test partial batch written on shutdown
    - Test empty batch skipped on shutdown
    - _Requirements: 12.1, 12.2_

- [x] 14. Configure Consumer service startup and dependency injection
  - Register IFileCounterManager, IFileWriter, IBatchProcessor, IMessageConsumer
  - Bind ConsumerSettings from configuration
  - Configure JSON deserialization options
  - Configure logging providers
  - Start MessageConsumer in Worker Service background task
  - Log successful startup with broker URL, topic, clientId, subscription name
  - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 14.7, 14.8, 16.5_

- [x] 15. Checkpoint - Ensure Consumer service builds and starts
  - Ensure all tests pass, ask the user if questions arise.

- [x] 16. Create README.md with build and run instructions
  - Add instructions for building the .NET solution using dotnet build
  - Add instructions for running Producer service using dotnet run
  - Add instructions for running Consumer service using dotnet run
  - Add instructions for starting ActiveMQ via docker-compose up
  - Add example curl commands for testing Producer API
  - Document configuration options for both services
  - _Requirements: 19.1, 19.2, 19.3, 19.4, 19.5_

- [x] 17. Final checkpoint - Verify end-to-end functionality
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped per Requirement 20 (no unit tests required)
- Each task references specific requirements for traceability
- The implementation uses C# and .NET 8.0 as specified in the design document
- Property tests validate universal correctness properties from the design
- Unit tests validate specific examples and edge cases
- Checkpoints ensure incremental validation at key milestones
- The migration maintains functional equivalence with the Java Spring Boot system
