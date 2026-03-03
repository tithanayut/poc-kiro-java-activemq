# Architecture Decision Records (ADR)

## Overview

This document captures the key architecture decisions made during the design and implementation of the Order Processing Service. Each decision is documented with its context, rationale, alternatives considered, and consequences.

---

## ADR-001: Topic-Based Messaging Pattern

**Status**: Accepted

**Context**:
The system needs to distribute product messages from a producer to one or more consumers. We need to choose between ActiveMQ queues (point-to-point) and topics (publish-subscribe).

**Decision**:
Use ActiveMQ topics for message distribution between producer and consumer services.

**Rationale**:
- Topics support multiple independent consumers receiving the same messages
- Enables future scalability where different consumers can process messages for different purposes (e.g., analytics, auditing, processing)
- Maintains loose coupling between producer and consumers
- Allows adding new consumers without modifying existing components

**Alternatives Considered**:
1. **Queues (Point-to-Point)**: Rejected because it limits the system to a single consumer or requires complex load balancing logic. Messages are consumed by only one consumer, preventing future use cases like parallel analytics or auditing.
2. **Direct HTTP Calls**: Rejected because it creates tight coupling between producer and consumer, requires consumer to be always available, and doesn't provide message persistence or retry capabilities.

**Consequences**:
- Positive: Flexible architecture that supports multiple independent consumers
- Positive: Natural support for broadcast scenarios
- Negative: Requires durable subscriptions to prevent message loss when consumers are offline (addressed in ADR-007)
- Negative: Each consumer receives all messages, which may be inefficient if consumers only need subsets

**Related Decisions**: ADR-007 (Durable Subscriptions)

---

## ADR-002: Batch Processing with In-Memory Accumulation

**Status**: Accepted

**Context**:
The consumer needs to write product data to disk. We need to decide whether to write each product immediately or accumulate products before writing.

**Decision**:
Accumulate products in memory and write them in batches of 5 to separate numbered files.

**Rationale**:
- Reduces I/O operations significantly (5x fewer file writes)
- Improves throughput by amortizing file system overhead
- Provides clear batch boundaries for tracking and debugging
- Simplifies file management with predictable file sizes
- Batch size of 5 is small enough to limit memory usage while providing meaningful I/O reduction

**Alternatives Considered**:
1. **Immediate Write Per Product**: Rejected because it creates excessive I/O operations, reduces throughput, and increases disk wear. Each product would require opening, writing, and closing a file.
2. **Single Large File**: Rejected because it makes it difficult to track batch boundaries, complicates error recovery, and creates a single point of failure. File corruption would affect all data.
3. **Time-Based Batching**: Rejected because it adds complexity with timers and doesn't guarantee consistent batch sizes. Products could be delayed unnecessarily if batch size isn't reached.

**Consequences**:
- Positive: Significant performance improvement through reduced I/O
- Positive: Clear batch boundaries for operational visibility
- Positive: Predictable file sizes simplify capacity planning
- Negative: Products in current batch are lost if consumer crashes before writing (mitigated by message acknowledgment strategy in ADR-006)
- Negative: Requires careful memory management for batch state

**Related Decisions**: ADR-003 (Separate Batch Files), ADR-006 (Manual Acknowledgment)

---

## ADR-003: Separate Numbered Files Per Batch

**Status**: Accepted

**Context**:
When writing batches to disk, we need to decide on the file organization strategy.

**Decision**:
Write each batch to a separate file with sequential numbering: `PRODUCT_ORDER_1.txt`, `PRODUCT_ORDER_2.txt`, etc.

**Rationale**:
- Clear batch boundaries make it easy to identify which products were processed together
- Sequential numbering provides natural ordering and tracking
- Separate files enable parallel processing or analysis of individual batches
- Simplifies error recovery - corrupted file only affects one batch
- File counter can be recovered from file system state on restart

**Alternatives Considered**:
1. **Single Append-Only File**: Rejected because it creates a single point of failure, makes batch boundaries unclear, and complicates concurrent access or analysis.
2. **Timestamp-Based File Names**: Rejected because timestamps don't guarantee ordering (clock skew, concurrent writes), make sequencing unclear, and complicate file counter recovery.
3. **UUID-Based File Names**: Rejected because it provides no natural ordering, makes tracking difficult, and prevents simple file counter recovery on restart.

**Consequences**:
- Positive: Clear operational visibility into batch processing
- Positive: Simplified error recovery and debugging
- Positive: Enables parallel batch analysis
- Positive: File counter recovery from file system state
- Negative: More files to manage compared to single file approach
- Negative: Requires file counter state management (addressed in ADR-004)

**Related Decisions**: ADR-002 (Batch Processing), ADR-004 (File Counter Persistence)

---

## ADR-004: File Counter Persistence via File System Scanning

**Status**: Accepted

**Context**:
The consumer needs to maintain a file counter across restarts to avoid overwriting existing batch files. We need to decide how to persist this counter.

**Decision**:
Initialize file counter on startup by scanning the output directory for existing `PRODUCT_ORDER_*.txt` files, extracting counter values, and setting the counter to (max + 1).

**Rationale**:
- No additional persistence mechanism required (database, state file)
- File system is the source of truth - counter is derived from actual files
- Resilient to state file corruption or loss
- Simple implementation with no external dependencies
- Handles gaps in sequence gracefully (uses max counter)
- Automatically handles manual file deletion or cleanup

**Alternatives Considered**:
1. **Separate State File**: Rejected because it introduces a second source of truth that can become inconsistent with actual files, requires additional error handling for state file corruption, and adds complexity.
2. **Database Persistence**: Rejected because it adds infrastructure dependency, increases complexity, and creates potential for state divergence between database and file system.
3. **In-Memory Only (Reset to 1 on Restart)**: Rejected because it would overwrite existing files on restart, causing data loss and breaking the sequential numbering guarantee.

**Consequences**:
- Positive: Simple, reliable persistence without external dependencies
- Positive: File system is single source of truth
- Positive: Resilient to state corruption
- Positive: Handles manual file operations gracefully
- Negative: Requires directory scan on startup (O(n) where n = number of files)
- Negative: Invalid file names must be handled gracefully (logged and skipped)

**Related Decisions**: ADR-003 (Separate Batch Files)

---

## ADR-005: Pipe-Delimited Output Format

**Status**: Accepted

**Context**:
We need to choose a format for storing order and product data in output files.

**Decision**:
Use pipe-delimited format: `ORDER_ID|PRODUCT_ID` with one entry per line.

**Rationale**:
- Simple, human-readable format
- Preserves relationship between orders and products
- Easy to parse with standard tools (awk, cut, grep)
- Pipe character is unlikely to appear in order or product IDs
- No escaping complexity like CSV with commas in values
- Compact representation

**Alternatives Considered**:
1. **CSV Format**: Rejected because commas may appear in IDs requiring escaping, adds parsing complexity, and provides no benefit over pipe-delimited for this use case.
2. **JSON Format**: Rejected because it adds unnecessary overhead (brackets, quotes), reduces human readability for simple key-value pairs, and increases file size.
3. **Fixed-Width Format**: Rejected because it requires padding, wastes space, and limits ID length flexibility.
4. **Tab-Delimited**: Rejected because tabs are less visible in text editors and can be confused with spaces.

**Consequences**:
- Positive: Simple, readable format
- Positive: Easy parsing with standard Unix tools
- Positive: Compact file size
- Negative: Requires validation that IDs don't contain pipe characters
- Negative: No built-in schema or type information

---

## ADR-006: Manual Message Acknowledgment After Processing

**Status**: Accepted

**Context**:
We need to decide when to acknowledge messages from ActiveMQ: automatically on receipt, or manually after successful processing.

**Decision**:
Use manual acknowledgment mode and acknowledge messages only after they are successfully added to the batch.

**Rationale**:
- Prevents message loss if consumer crashes during processing
- Ensures at-least-once delivery semantics
- Messages are redelivered if consumer fails before acknowledgment
- Aligns with reliability requirements for order processing
- Batch is in-memory, so acknowledgment before file write is acceptable

**Alternatives Considered**:
1. **Auto-Acknowledgment**: Rejected because messages would be acknowledged immediately on receipt, before processing. Consumer crash would lose messages that were received but not processed.
2. **Acknowledgment After File Write**: Rejected because it would require tracking which messages are in each batch, adds complexity, and provides minimal benefit since batch is already in memory.
3. **Transactional Sessions**: Rejected because it adds significant complexity, reduces throughput, and is overkill for this use case where manual acknowledgment provides sufficient guarantees.

**Consequences**:
- Positive: No message loss on consumer crash (messages redelivered)
- Positive: At-least-once delivery semantics
- Negative: Possible duplicate processing if consumer crashes after adding to batch but before file write (acceptable trade-off)
- Negative: Requires careful error handling to avoid infinite redelivery loops

**Related Decisions**: ADR-002 (Batch Processing)

---

## ADR-007: Durable Topic Subscriptions for Offline Message Persistence

**Status**: Accepted

**Context**:
The original implementation used non-durable topic subscriptions, causing message loss when the consumer was offline. Messages published while no subscriber was connected were immediately discarded by ActiveMQ.

**Decision**:
Convert to durable subscriptions by configuring a client ID on the connection and using `createDurableSubscriber()` with a subscription name.

**Rationale**:
- Prevents message loss during consumer downtime (maintenance, crashes, deployments)
- ActiveMQ persists messages for offline durable subscribers
- Messages are delivered when consumer reconnects
- Supports multiple independent consumers with separate durable subscriptions
- Maintains topic model benefits (multiple consumers) while adding persistence
- Minimal code changes required (client ID + durable subscriber API)

**Alternatives Considered**:
1. **Switch to Queues**: Rejected because it loses the ability to have multiple independent consumers receiving all messages. Queues provide point-to-point delivery only.
2. **Virtual Topics**: Rejected because it adds complexity with queue-per-consumer pattern, requires additional ActiveMQ configuration, and doesn't provide significant benefits over durable subscriptions for this use case.
3. **External Message Store**: Rejected because it adds infrastructure complexity, requires additional persistence layer, and duplicates functionality already provided by ActiveMQ.
4. **Accept Message Loss**: Rejected because it violates reliability requirements for order processing system.

**Consequences**:
- Positive: Zero message loss during consumer downtime
- Positive: Maintains topic model for multiple consumers
- Positive: Simple implementation with minimal code changes
- Positive: Leverages built-in ActiveMQ persistence
- Negative: Requires unique client ID per consumer instance
- Negative: Requires subscription name configuration
- Negative: Durable subscriptions persist until explicitly unsubscribed (operational consideration)

**Related Decisions**: ADR-001 (Topic-Based Messaging)

---

## ADR-008: Exponential Backoff for Connection Retry

**Status**: Accepted

**Context**:
Both producer and consumer need to handle ActiveMQ connection failures and reconnect automatically.

**Decision**:
Implement exponential backoff strategy with initial delay of 1 second, maximum delay of 60 seconds, and backoff multiplier of 2.

**Rationale**:
- Prevents overwhelming the broker with rapid reconnection attempts
- Exponential backoff is industry best practice for retry logic
- Initial delay is short enough for quick recovery from transient failures
- Maximum delay prevents indefinite waiting while still being reasonable for broker restart scenarios
- Multiplier of 2 provides good balance between aggressive and conservative retry

**Alternatives Considered**:
1. **Fixed Delay Retry**: Rejected because it doesn't adapt to failure duration. Short delays waste resources on persistent failures; long delays slow recovery from transient failures.
2. **Linear Backoff**: Rejected because it doesn't back off aggressively enough for persistent failures, leading to unnecessary load on the broker.
3. **No Retry (Fail Fast)**: Rejected because it requires manual intervention for every connection failure, reducing system availability.
4. **Infinite Retry with No Backoff**: Rejected because it would overwhelm the broker with connection attempts during outages.

**Consequences**:
- Positive: Graceful handling of broker outages
- Positive: Prevents broker overload during recovery
- Positive: Quick recovery from transient failures
- Negative: Maximum delay of 60 seconds may be too long for some use cases (configurable if needed)
- Negative: Requires careful testing of retry logic

---

## ADR-009: Producer Returns HTTP 503 During Reconnection

**Status**: Accepted

**Context**:
When the producer loses connection to ActiveMQ, it needs to decide how to handle incoming HTTP requests during reconnection attempts.

**Decision**:
Return HTTP 503 Service Unavailable for all incoming requests while reconnecting to ActiveMQ.

**Rationale**:
- Honest communication of service state to clients
- HTTP 503 is semantically correct for temporary unavailability
- Clients can implement retry logic based on 503 status
- Prevents accepting requests that cannot be fulfilled
- Avoids queueing requests in memory (potential memory exhaustion)

**Alternatives Considered**:
1. **Queue Requests in Memory**: Rejected because it risks memory exhaustion during long outages, adds complexity for request timeout handling, and provides no delivery guarantees.
2. **Return HTTP 500 Internal Server Error**: Rejected because 500 indicates a server error, not temporary unavailability. Clients may not retry 500 responses.
3. **Block Requests Until Reconnected**: Rejected because it ties up HTTP threads, can lead to thread exhaustion, and provides poor user experience with long timeouts.
4. **Accept and Discard Requests**: Rejected because it silently loses data and violates reliability requirements.

**Consequences**:
- Positive: Clear communication of service state
- Positive: Enables client-side retry logic
- Positive: Prevents memory exhaustion
- Negative: Clients must implement retry logic for 503 responses
- Negative: Orders submitted during outage are rejected (acceptable trade-off for reliability)

---

## ADR-010: Docker Compose for ActiveMQ Deployment

**Status**: Accepted

**Context**:
ActiveMQ broker needs to be deployed and managed for local development and testing.

**Decision**:
Use Docker Compose to deploy ActiveMQ in a container with persistent volume for message storage.

**Rationale**:
- Simple, consistent deployment across development environments
- No need to install ActiveMQ directly on host machines
- Persistent volumes ensure message durability across container restarts
- Easy to version control broker configuration
- Matches modern containerized deployment patterns
- Web console accessible for monitoring and debugging

**Alternatives Considered**:
1. **Manual ActiveMQ Installation**: Rejected because it creates environment inconsistencies, requires manual setup steps, and complicates onboarding new developers.
2. **Embedded ActiveMQ**: Rejected because it couples broker lifecycle to application lifecycle, complicates testing, and doesn't reflect production deployment.
3. **Kubernetes**: Rejected because it's overkill for local development, adds complexity, and requires additional infrastructure.
4. **Cloud-Managed Message Broker**: Rejected for local development because it requires internet connectivity, incurs costs, and adds latency.

**Consequences**:
- Positive: Consistent development environments
- Positive: Simple setup with single command
- Positive: Message persistence across restarts
- Positive: Easy broker configuration management
- Negative: Requires Docker installation
- Negative: Container overhead (minimal for modern systems)

---

## ADR-011: Configuration via Properties Files

**Status**: Accepted

**Context**:
Both producer and consumer need configuration for broker URL, topic name, batch size, and other parameters.

**Decision**:
Use Java properties files (`application.properties`) for configuration with Spring Boot's configuration management.

**Rationale**:
- Standard Spring Boot configuration approach
- Simple key-value format, easy to read and edit
- Supports environment-specific overrides
- No additional dependencies required
- Type-safe access via `@Value` annotations
- Familiar to Java developers

**Alternatives Considered**:
1. **YAML Configuration**: Rejected because it adds parsing complexity, is more error-prone with indentation, and provides no significant benefit for flat configuration structure.
2. **Environment Variables Only**: Rejected because it makes local development harder (many variables to set), doesn't support complex values well, and lacks discoverability.
3. **Database Configuration**: Rejected because it adds infrastructure dependency, requires application to be running to view config, and complicates deployment.
4. **Hardcoded Values**: Rejected because it prevents environment-specific configuration and requires recompilation for changes.

**Consequences**:
- Positive: Simple, standard configuration approach
- Positive: Easy to override for different environments
- Positive: Type-safe access in code
- Negative: Requires application restart for configuration changes
- Negative: Sensitive values (passwords) need additional protection (not applicable for this POC)

---

## Summary

These architecture decisions form the foundation of the Order Processing Service design. Key themes include:

- **Reliability**: Durable subscriptions, manual acknowledgment, exponential backoff
- **Simplicity**: File-based persistence, properties configuration, pipe-delimited format
- **Scalability**: Topic-based messaging, batch processing, separate files
- **Operability**: Docker Compose deployment, clear error responses, comprehensive logging

Each decision balances trade-offs between simplicity, reliability, performance, and future extensibility.
