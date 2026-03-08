# Design Document: Java to C# Migration

## Overview

This design document specifies the architecture and implementation approach for migrating a Java Spring Boot order processing system to C# .NET. The system consists of two independent services that communicate asynchronously through Apache ActiveMQ using a topic-based publish-subscribe pattern.

The Producer service exposes an HTTP API that accepts order requests containing multiple product IDs. It decomposes each order into individual product messages and publishes them to an ActiveMQ topic. The Consumer service maintains a durable subscription to the topic, accumulates products in batches of 5, and writes completed batches to numbered pipe-delimited files.

Key design goals:
- Maintain functional equivalence with the existing Java implementation
- Follow .NET conventions and best practices
- Ensure message durability through durable subscriptions and manual acknowledgment
- Provide resilient connection management with automatic retry logic
- Support graceful shutdown to prevent data loss

## Architecture

### System Components

The system consists of three main components:

1. **Producer Service** - ASP.NET Core Web API application
   - Exposes HTTP POST endpoint for order submission
   - Validates incoming requests
   - Decomposes orders into individual product messages
   - Publishes messages to ActiveMQ topic
   - Manages ActiveMQ connection with retry logic

2. **Consumer Service** - .NET Worker Service or Console Application
   - Maintains durable subscription to ActiveMQ topic
   - Deserializes incoming JSON messages
   - Accumulates products in memory batches
   - Writes completed batches to numbered files
   - Manages file counter persistence across restarts
   - Implements graceful shutdown

3. **ActiveMQ Broker** - Apache ActiveMQ Classic (Docker container)
   - Provides topic-based messaging infrastructure
   - Persists messages for durable subscriptions
   - Exposes OpenWire protocol on port 61616
   - Provides web console on port 8161

### Communication Flow

```
Client → HTTP POST → Producer Service → ActiveMQ Topic → Consumer Service → File System
```

1. Client sends HTTP POST request with order containing multiple product IDs
2. Producer validates request and returns 202 Accepted
3. Producer publishes N separate JSON messages (one per product) to ActiveMQ topic
4. ActiveMQ persists messages for durable subscription
5. Consumer receives messages and accumulates products in memory
6. When batch reaches size 5, Consumer writes to PRODUCT_ORDER_N.txt file
7. Consumer acknowledges messages only after successful batch addition

### Deployment Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Producer       │     │   ActiveMQ      │     │   Consumer      │
│  Service        │────▶│   Broker        │────▶│   Service       │
│  (ASP.NET Core) │     │   (Docker)      │     │   (Worker)      │
└─────────────────┘     └─────────────────┘     └─────────────────┘
        │                       │                         │
        │                       │                         │
        ▼                       ▼                         ▼
   Port 5000              Port 61616               Output Directory
                          Port 8161                (PRODUCT_ORDER_*.txt)
```

## Components and Interfaces

### Producer Service Components

#### OrderController
- **Responsibility**: HTTP endpoint handling and request validation
- **Interface**:
  ```csharp
  [ApiController]
  [Route("orders")]
  public class OrderController : ControllerBase
  {
      [HttpPost]
      Task<IActionResult> CreateOrder([FromBody] OrderRequest request);
  }
  ```
- **Dependencies**: IMessagePublisher, ILogger

#### MessagePublisher
- **Responsibility**: Message publishing and ActiveMQ interaction
- **Interface**:
  ```csharp
  public interface IMessagePublisher
  {
      Task PublishProductMessagesAsync(string orderId, IEnumerable<string> productIds);
      bool IsConnected { get; }
  }
  ```
- **Implementation Details**:
  - Uses Apache.NMS.ActiveMQ for broker connectivity
  - Creates ISession and IMessageProducer instances
  - Serializes ProductMessage to JSON using System.Text.Json
  - Publishes to configured topic name

#### ConnectionManager
- **Responsibility**: ActiveMQ connection lifecycle and retry logic
- **Interface**:
  ```csharp
  public interface IConnectionManager
  {
      Task<IConnection> GetConnectionAsync();
      Task ReconnectAsync();
      bool IsConnected { get; }
  }
  ```
- **Implementation Details**:
  - Implements exponential backoff: initial 1s, max 60s, multiplier 2
  - Monitors connection state via ExceptionListener
  - Triggers automatic reconnection on connection loss
  - Thread-safe connection access

### Consumer Service Components

#### MessageConsumer
- **Responsibility**: Message subscription and batch coordination
- **Interface**:
  ```csharp
  public interface IMessageConsumer
  {
      Task StartAsync(CancellationToken cancellationToken);
      Task StopAsync(CancellationToken cancellationToken);
  }
  ```
- **Implementation Details**:
  - Creates durable subscription using clientId and subscriptionName
  - Uses manual acknowledgment mode (AcknowledgementMode.ClientAcknowledge)
  - Deserializes JSON messages to Product objects
  - Delegates batch management to BatchProcessor
  - Acknowledges messages only after successful batch addition

#### BatchProcessor
- **Responsibility**: Batch accumulation and file writing coordination
- **Interface**:
  ```csharp
  public interface IBatchProcessor
  {
      Task AddProductAsync(Product product);
      Task FlushAsync();
  }
  ```
- **Implementation Details**:
  - Maintains in-memory List<Product> with configured batch size
  - Triggers file write when batch reaches size
  - Delegates file operations to FileWriter
  - Thread-safe batch operations using lock or SemaphoreSlim

#### FileWriter
- **Responsibility**: File I/O operations with retry logic
- **Interface**:
  ```csharp
  public interface IFileWriter
  {
      Task WriteProductsAsync(IEnumerable<Product> products, int fileCounter);
  }
  ```
- **Implementation Details**:
  - Formats products as pipe-delimited: orderId|productId
  - Writes to PRODUCT_ORDER_{counter}.txt in configured directory
  - Implements retry logic: max 3 attempts, 1000ms delay
  - Creates output directory if missing
  - Uses File.WriteAllLinesAsync for async I/O

#### FileCounterManager
- **Responsibility**: File counter persistence across restarts
- **Interface**:
  ```csharp
  public interface IFileCounterManager
  {
      int GetNextCounter();
      void IncrementCounter();
  }
  ```
- **Implementation Details**:
  - Scans output directory on initialization
  - Parses PRODUCT_ORDER_*.txt filenames using regex
  - Extracts numeric counter values
  - Sets initial counter to max + 1 (or 1 if no files exist)
  - Logs warnings for unparseable filenames

### Data Models

#### OrderRequest
```csharp
public class OrderRequest
{
    [Required]
    public string OrderId { get; set; }
    
    [Required]
    [MinLength(1)]
    public List<string> ProductIds { get; set; }
}
```

#### ProductMessage
```csharp
public class ProductMessage
{
    public string OrderId { get; set; }
    public string ProductId { get; set; }
}
```

#### Product
```csharp
public class Product
{
    public string OrderId { get; set; }
    public string ProductId { get; set; }
}
```

### Configuration Models

#### ProducerSettings
```csharp
public class ProducerSettings
{
    public string BrokerUrl { get; set; }
    public string TopicName { get; set; }
    public ConnectionRetrySettings Retry { get; set; }
}

public class ConnectionRetrySettings
{
    public int InitialDelayMs { get; set; } = 1000;
    public int MaxDelayMs { get; set; } = 60000;
    public double BackoffMultiplier { get; set; } = 2.0;
}
```

#### ConsumerSettings
```csharp
public class ConsumerSettings
{
    public string BrokerUrl { get; set; }
    public string TopicName { get; set; }
    public string ClientId { get; set; }
    public string SubscriptionName { get; set; }
    public int BatchSize { get; set; } = 5;
    public string OutputDirectory { get; set; }
    public FileWriterSettings FileWriter { get; set; }
}

public class FileWriterSettings
{
    public int MaxRetryAttempts { get; set; } = 3;
    public int RetryDelayMs { get; set; } = 1000;
}
```

## Data Models

### Message Format

Product messages are serialized as JSON with the following structure:

```json
{
  "orderId": "ORDER123",
  "productId": "PROD456"
}
```

### File Format

Output files use pipe-delimited format with one product per line:

```
ORDER123|PROD456
ORDER123|PROD789
ORDER124|PROD111
ORDER124|PROD222
ORDER125|PROD333
```

### File Naming Convention

Files follow the pattern: `PRODUCT_ORDER_{N}.txt` where N is a sequential integer starting from 1.

Examples:
- PRODUCT_ORDER_1.txt
- PRODUCT_ORDER_2.txt
- PRODUCT_ORDER_3.txt

### Configuration Files

#### Producer appsettings.json
```json
{
  "Logging": {
    "LogLevel": {
      "Default": "Information",
      "Microsoft.AspNetCore": "Warning"
    }
  },
  "Producer": {
    "BrokerUrl": "activemq:tcp://localhost:61616",
    "TopicName": "product.orders",
    "Retry": {
      "InitialDelayMs": 1000,
      "MaxDelayMs": 60000,
      "BackoffMultiplier": 2.0
    }
  }
}
```

#### Consumer appsettings.json
```json
{
  "Logging": {
    "LogLevel": {
      "Default": "Information"
    }
  },
  "Consumer": {
    "BrokerUrl": "activemq:tcp://localhost:61616",
    "TopicName": "product.orders",
    "ClientId": "consumer-service-1",
    "SubscriptionName": "product-order-subscription",
    "BatchSize": 5,
    "OutputDirectory": "./output",
    "FileWriter": {
      "MaxRetryAttempts": 3,
      "RetryDelayMs": 1000
    }
  }
}
```


## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Valid Order Acceptance

*For any* valid order request (with non-empty orderId and non-empty productIds array), the Producer service should return HTTP 202 Accepted.

**Validates: Requirements 1.2**

### Property 2: Order Decomposition Count

*For any* order with N product IDs, the Producer service should publish exactly N separate messages to ActiveMQ.

**Validates: Requirements 2.1**

### Property 3: Order ID Consistency

*For any* order containing multiple products, all published messages should contain the same orderId value as the original order request.

**Validates: Requirements 2.2**

### Property 4: Message JSON Structure

*For any* product message published to ActiveMQ, the serialized JSON should contain both "orderId" and "productId" fields with the correct values.

**Validates: Requirements 2.3**

### Property 5: Disconnected State Rejection

*For any* incoming HTTP request while the ActiveMQ connection is unavailable, the Producer service should return HTTP 503 Service Unavailable.

**Validates: Requirements 4.5**

### Property 6: Message Serialization Round-Trip

*For any* Product object, serializing to JSON and then deserializing should produce an equivalent Product object with the same orderId and productId values.

**Validates: Requirements 6.1**

### Property 7: Batch Accumulation

*For any* sequence of products received by the Consumer, products should accumulate in memory until the configured batch size is reached before triggering a file write.

**Validates: Requirements 7.1, 7.3**

### Property 8: Batch Clearing After Write

*For any* completed batch that is written to a file, the Consumer's in-memory batch should be empty immediately after the write operation completes.

**Validates: Requirements 7.4**

### Property 9: Message Acknowledgment on Success

*For any* product successfully added to the batch, the Consumer should acknowledge the corresponding ActiveMQ message.

**Validates: Requirements 8.2**

### Property 10: File Counter Initialization

*For any* set of existing PRODUCT_ORDER_*.txt files in the output directory, the Consumer should initialize the file counter to the maximum counter value found plus 1.

**Validates: Requirements 9.2, 9.3**

### Property 11: File Naming Convention

*For any* batch written to disk, the output filename should follow the pattern PRODUCT_ORDER_{counter}.txt where counter is the current file counter value.

**Validates: Requirements 10.1**

### Property 12: File Counter Increment

*For any* successful file write operation, the file counter should be incremented by exactly 1.

**Validates: Requirements 10.4**

### Property 13: Pipe-Delimited Format

*For any* product written to an output file, the line should be formatted as "orderId|productId" with a pipe delimiter separating the two fields.

**Validates: Requirements 10.5**

## Error Handling

### Producer Service Error Handling

**Invalid Request Validation**:
- Missing orderId: Return HTTP 400 with error message
- Missing productIds: Return HTTP 400 with error message
- Empty productIds array: Return HTTP 400 with error message
- Use ASP.NET Core model validation attributes ([Required], [MinLength])

**Connection Failures**:
- ActiveMQ unavailable during request: Return HTTP 503 Service Unavailable
- Connection lost during publishing: Log error, trigger reconnection, return HTTP 503
- Message publishing failure: Log error with order details, return HTTP 500

**Reconnection Logic**:
- Exponential backoff: delay = min(initialDelay * (multiplier ^ attempt), maxDelay)
- Initial delay: 1000ms
- Max delay: 60000ms
- Multiplier: 2.0
- Continue retry indefinitely until connection restored
- Log each reconnection attempt with current delay

### Consumer Service Error Handling

**Message Processing Failures**:
- JSON deserialization error: Log error with message content, reject message (no acknowledgment)
- Missing required fields: Log error, reject message
- Null or invalid values: Log error, reject message

**File Write Failures**:
- Directory creation failure: Log error, retry operation
- File write permission error: Retry with configured delay
- Disk full error: Retry with configured delay
- Max retries exhausted: Log critical error, throw exception to stop consumer

**Batch Processing Failures**:
- Exception during batch add: Log error, do not acknowledge message, allow redelivery
- Exception during file write: Retry with backoff, preserve batch in memory
- Partial batch on shutdown: Write remaining products to file before exit

**Startup Failures**:
- Output directory not accessible: Log error, attempt to create directory
- File counter scan failure: Log warning, default to counter = 1
- ActiveMQ connection failure: Log error, retry with exponential backoff
- Configuration missing: Log error, fail fast with clear message

### Logging Strategy

**Producer Logging**:
- Startup: Log broker URL and topic name (INFO)
- Order received: Log orderId and product count (INFO)
- Messages published: Log orderId and message count (DEBUG)
- Connection lost: Log error with exception details (ERROR)
- Reconnection attempt: Log attempt number and delay (WARNING)
- Reconnection success: Log success message (INFO)

**Consumer Logging**:
- Startup: Log broker URL, topic, clientId, subscription name (INFO)
- Message received: Log orderId and productId (DEBUG)
- Batch written: Log filename and product count (INFO)
- File write retry: Log attempt number and error (WARNING)
- File write failure: Log critical error after max retries (ERROR)
- Shutdown: Log graceful shutdown with batch status (INFO)
- File counter initialized: Log starting counter value (INFO)

## Testing Strategy

### Overview

This migration explicitly excludes automated testing per Requirement 20. However, this section documents the testing approach that would be used if testing were required, to guide manual verification and future test implementation.

### Manual Verification Approach

**Producer Service Verification**:
1. Start ActiveMQ via Docker Compose
2. Start Producer service
3. Send valid order via curl: verify 202 response
4. Check ActiveMQ web console: verify N messages published
5. Send invalid orders (missing fields): verify 400 responses
6. Stop ActiveMQ: send order and verify 503 response
7. Restart ActiveMQ: verify producer reconnects and accepts orders

**Consumer Service Verification**:
1. Start ActiveMQ and Producer
2. Start Consumer service
3. Send orders with total products = multiple of batch size
4. Verify output files created with correct names
5. Verify file content matches pipe-delimited format
6. Stop and restart Consumer: verify file counter resumes correctly
7. Send partial batch and shutdown: verify partial batch written

**Integration Verification**:
1. Start all services
2. Send multiple orders with varying product counts
3. Verify end-to-end flow: HTTP → ActiveMQ → Files
4. Verify message durability: stop consumer, send orders, restart consumer
5. Verify graceful shutdown: send orders, stop consumer mid-batch

### Property-Based Testing Approach (If Implemented)

If automated testing were to be implemented in the future, the following property-based testing strategy would be recommended:

**Testing Library**: Use a property-based testing library such as:
- FsCheck (recommended for .NET)
- CsCheck
- Hedgehog

**Test Configuration**:
- Minimum 100 iterations per property test
- Each test tagged with: **Feature: java-to-csharp-migration, Property {N}: {description}**

**Property Test Examples**:

```csharp
// Property 1: Valid Order Acceptance
[Property(Arbitrary = new[] { typeof(OrderGenerators) })]
public Property ValidOrdersReturn202(OrderRequest order)
{
    // Arrange: order has non-empty orderId and productIds
    // Act: POST to /orders
    // Assert: response status is 202
}

// Property 2: Order Decomposition Count
[Property]
public Property OrderDecompositionMatchesProductCount(string orderId, NonEmptyArray<string> productIds)
{
    // Arrange: create order with N products
    // Act: publish order
    // Assert: exactly N messages published to ActiveMQ
}

// Property 6: Message Serialization Round-Trip
[Property]
public Property SerializationRoundTrip(Product product)
{
    // Arrange: random product
    // Act: serialize then deserialize
    // Assert: result equals original
}

// Property 10: File Counter Initialization
[Property]
public Property FileCounterInitializesCorrectly(PositiveInt[] existingCounters)
{
    // Arrange: create files with given counter values
    // Act: initialize FileCounterManager
    // Assert: counter = max(existingCounters) + 1
}
```

**Unit Test Examples**:

Unit tests would focus on specific examples and edge cases:

```csharp
[Fact]
public async Task PostOrder_MissingOrderId_Returns400()
{
    // Arrange: request with null orderId
    // Act: POST to /orders
    // Assert: 400 Bad Request
}

[Fact]
public async Task PostOrder_EmptyProductIds_Returns400()
{
    // Arrange: request with empty productIds array
    // Act: POST to /orders
    // Assert: 400 Bad Request
}

[Fact]
public async Task FileCounter_NoExistingFiles_StartsAtOne()
{
    // Arrange: empty output directory
    // Act: initialize FileCounterManager
    // Assert: counter = 1
}

[Fact]
public async Task Consumer_Shutdown_WritesPartialBatch()
{
    // Arrange: batch with 3 products (less than batch size 5)
    // Act: trigger shutdown
    // Assert: file written with 3 products
}
```

**Integration Test Examples**:

Integration tests would verify end-to-end behavior:

```csharp
[Fact]
public async Task EndToEnd_OrderProcessing_CreatesCorrectFiles()
{
    // Arrange: start ActiveMQ, Producer, Consumer
    // Act: POST order with 12 products
    // Assert: 2 files created (5 products each) + 1 file (2 products)
}

[Fact]
public async Task DurableSubscription_ConsumerOffline_MessagesPersistedAndDelivered()
{
    // Arrange: start ActiveMQ and Producer
    // Act: stop Consumer, POST orders, restart Consumer
    // Assert: all messages processed after restart
}
```

### Test Coverage Goals (If Implemented)

- **Property Tests**: Cover all 13 correctness properties
- **Unit Tests**: Cover error conditions, edge cases, and specific examples
- **Integration Tests**: Cover end-to-end scenarios and ActiveMQ integration
- **Code Coverage**: Target 80%+ line coverage for business logic

### Testing Exclusion Rationale

Per Requirement 20, this migration prioritizes rapid delivery over test coverage. The system's correctness will be verified through:
1. Manual testing during development
2. Comparison with existing Java system behavior
3. Production monitoring and logging
4. Future test implementation if needed

