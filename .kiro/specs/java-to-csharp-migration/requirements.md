# Requirements Document

## Introduction

This document specifies the requirements for migrating an existing Java Spring Boot order processing system to C# .NET. The system consists of two services (Producer and Consumer) that communicate via ActiveMQ message broker using a topic-based publish-subscribe pattern. The Producer receives HTTP order requests and publishes product messages to ActiveMQ, while the Consumer processes messages in batches and writes them to numbered files in pipe-delimited format.

## Glossary

- **Producer_Service**: The C# .NET web API that receives HTTP order requests and publishes product messages to ActiveMQ
- **Consumer_Service**: The C# .NET application that subscribes to ActiveMQ messages, accumulates products in batches, and writes them to files
- **ActiveMQ_Broker**: Apache ActiveMQ Classic message broker running in Docker, providing topic-based messaging
- **Product_Message**: A JSON message containing orderId and productId fields
- **Batch**: A collection of exactly 5 products accumulated in memory before writing to disk
- **Output_File**: A pipe-delimited text file named PRODUCT_ORDER_N.txt where N is a sequential counter
- **Durable_Subscription**: An ActiveMQ subscription that persists messages when the consumer is offline
- **Connection_Manager**: Component responsible for establishing and maintaining ActiveMQ connections with retry logic
- **File_Counter**: Sequential number used to name output files, persisted by scanning existing files on startup

## Requirements

### Requirement 1: Producer HTTP API

**User Story:** As a client application, I want to submit orders via HTTP POST, so that products can be queued for processing

#### Acceptance Criteria

1. THE Producer_Service SHALL expose an HTTP POST endpoint at /orders
2. WHEN a valid order request is received, THE Producer_Service SHALL return HTTP 202 Accepted
3. WHEN an order request is missing orderId, THE Producer_Service SHALL return HTTP 400 Bad Request
4. WHEN an order request is missing productIds, THE Producer_Service SHALL return HTTP 400 Bad Request
5. WHEN an order request has empty productIds array, THE Producer_Service SHALL return HTTP 400 Bad Request
6. WHEN the ActiveMQ connection is unavailable, THE Producer_Service SHALL return HTTP 503 Service Unavailable

### Requirement 2: Order Decomposition

**User Story:** As a system architect, I want orders decomposed into individual product messages, so that products can be processed independently

#### Acceptance Criteria

1. WHEN an order with N productIds is received, THE Producer_Service SHALL publish N separate Product_Messages to ActiveMQ
2. FOR ALL Product_Messages from the same order, THE Producer_Service SHALL use the same orderId value
3. THE Producer_Service SHALL serialize Product_Messages as JSON with orderId and productId fields

### Requirement 3: ActiveMQ Topic Publishing

**User Story:** As a system architect, I want messages published to an ActiveMQ topic, so that multiple consumers can receive the same messages

#### Acceptance Criteria

1. THE Producer_Service SHALL publish Product_Messages to the configured ActiveMQ topic
2. THE Producer_Service SHALL use the topic name from configuration
3. THE Producer_Service SHALL use the broker URL from configuration
4. THE Producer_Service SHALL serialize messages as JSON text

### Requirement 4: Producer Connection Management

**User Story:** As a system operator, I want automatic connection recovery, so that the producer reconnects after ActiveMQ outages

#### Acceptance Criteria

1. WHEN the ActiveMQ connection is lost, THE Connection_Manager SHALL attempt to reconnect using exponential backoff
2. THE Connection_Manager SHALL use an initial retry delay of 1 second
3. THE Connection_Manager SHALL use a maximum retry delay of 60 seconds
4. THE Connection_Manager SHALL use a backoff multiplier of 2
5. WHILE reconnecting, THE Producer_Service SHALL return HTTP 503 for all incoming requests
6. WHEN the connection is re-established, THE Producer_Service SHALL resume accepting requests

### Requirement 5: Consumer Message Subscription

**User Story:** As a system architect, I want durable topic subscriptions, so that messages are not lost when the consumer is offline

#### Acceptance Criteria

1. THE Consumer_Service SHALL create a durable subscription to the configured ActiveMQ topic
2. THE Consumer_Service SHALL use a unique client ID from configuration
3. THE Consumer_Service SHALL use a subscription name from configuration
4. WHEN the Consumer_Service is offline, THE ActiveMQ_Broker SHALL persist messages for the durable subscription
5. WHEN the Consumer_Service reconnects, THE ActiveMQ_Broker SHALL deliver all persisted messages

### Requirement 6: Message Deserialization

**User Story:** As a developer, I want messages deserialized from JSON, so that product data can be processed

#### Acceptance Criteria

1. WHEN a Product_Message is received, THE Consumer_Service SHALL deserialize the JSON into a Product object
2. THE Consumer_Service SHALL extract the orderId field
3. THE Consumer_Service SHALL extract the productId field
4. IF deserialization fails, THEN THE Consumer_Service SHALL log an error and reject the message

### Requirement 7: Batch Accumulation

**User Story:** As a system architect, I want products accumulated in batches of 5, so that file I/O operations are reduced

#### Acceptance Criteria

1. THE Consumer_Service SHALL accumulate products in memory until the batch size is reached
2. THE Consumer_Service SHALL use the batch size from configuration (default: 5)
3. WHEN the batch reaches the configured size, THE Consumer_Service SHALL write the batch to a file
4. WHEN a batch is written, THE Consumer_Service SHALL clear the batch and start accumulating the next batch

### Requirement 8: Manual Message Acknowledgment

**User Story:** As a system architect, I want manual message acknowledgment, so that messages are not lost if processing fails

#### Acceptance Criteria

1. THE Consumer_Service SHALL use manual acknowledgment mode for ActiveMQ messages
2. WHEN a product is successfully added to the batch, THE Consumer_Service SHALL acknowledge the message
3. IF adding a product to the batch fails, THEN THE Consumer_Service SHALL not acknowledge the message
4. WHEN a message is not acknowledged, THE ActiveMQ_Broker SHALL redeliver the message

### Requirement 9: File Counter Persistence

**User Story:** As a system operator, I want file numbering to resume after restart, so that existing files are not overwritten

#### Acceptance Criteria

1. WHEN the Consumer_Service starts, THE Consumer_Service SHALL scan the output directory for existing PRODUCT_ORDER_*.txt files
2. THE Consumer_Service SHALL extract the counter value from each filename
3. THE Consumer_Service SHALL set the File_Counter to the maximum counter value plus 1
4. IF no files exist, THEN THE Consumer_Service SHALL set the File_Counter to 1
5. IF a filename cannot be parsed, THEN THE Consumer_Service SHALL log a warning and skip that file

### Requirement 10: Batch File Writing

**User Story:** As a data analyst, I want batches written to numbered files, so that I can track processing order

#### Acceptance Criteria

1. WHEN a batch is full, THE Consumer_Service SHALL write the batch to a file named PRODUCT_ORDER_{File_Counter}.txt
2. THE Consumer_Service SHALL use the output directory path from configuration
3. THE Consumer_Service SHALL create the output directory if it does not exist
4. WHEN a file is written, THE Consumer_Service SHALL increment the File_Counter
5. THE Consumer_Service SHALL write each product on a separate line in pipe-delimited format: orderId|productId

### Requirement 11: File Write Retry Logic

**User Story:** As a system operator, I want automatic retry on file write failures, so that transient errors are handled gracefully

#### Acceptance Criteria

1. IF a file write operation fails, THEN THE Consumer_Service SHALL retry the operation
2. THE Consumer_Service SHALL use the maximum retry attempts from configuration (default: 3)
3. THE Consumer_Service SHALL use the retry delay from configuration (default: 1000ms)
4. WHEN all retry attempts are exhausted, THE Consumer_Service SHALL log an error and fail
5. WHEN a retry succeeds, THE Consumer_Service SHALL log a warning about the retry

### Requirement 12: Graceful Shutdown

**User Story:** As a system operator, I want graceful shutdown, so that in-progress batches are written before stopping

#### Acceptance Criteria

1. WHEN the Consumer_Service receives a shutdown signal, THE Consumer_Service SHALL write the current batch to a file
2. IF the current batch is empty, THEN THE Consumer_Service SHALL not write a file
3. THE Consumer_Service SHALL close the ActiveMQ connection before exiting
4. THE Consumer_Service SHALL log the shutdown operation

### Requirement 13: Producer Configuration

**User Story:** As a system operator, I want configuration via appsettings.json, so that I can customize the producer without recompiling

#### Acceptance Criteria

1. THE Producer_Service SHALL read the ActiveMQ broker URL from configuration
2. THE Producer_Service SHALL read the ActiveMQ topic name from configuration
3. THE Producer_Service SHALL read the HTTP server port from configuration
4. THE Producer_Service SHALL read logging configuration from appsettings.json

### Requirement 14: Consumer Configuration

**User Story:** As a system operator, I want configuration via appsettings.json, so that I can customize the consumer without recompiling

#### Acceptance Criteria

1. THE Consumer_Service SHALL read the ActiveMQ broker URL from configuration
2. THE Consumer_Service SHALL read the ActiveMQ topic name from configuration
3. THE Consumer_Service SHALL read the ActiveMQ client ID from configuration
4. THE Consumer_Service SHALL read the ActiveMQ subscription name from configuration
5. THE Consumer_Service SHALL read the batch size from configuration
6. THE Consumer_Service SHALL read the output directory path from configuration
7. THE Consumer_Service SHALL read the file writer retry settings from configuration
8. THE Consumer_Service SHALL read logging configuration from appsettings.json

### Requirement 15: Docker Compose Infrastructure

**User Story:** As a developer, I want ActiveMQ deployed via Docker Compose, so that I have a consistent development environment

#### Acceptance Criteria

1. THE system SHALL include a docker-compose.yml file that deploys ActiveMQ Classic
2. THE ActiveMQ_Broker SHALL expose port 61616 for OpenWire protocol
3. THE ActiveMQ_Broker SHALL expose port 8161 for the web console
4. THE ActiveMQ_Broker SHALL use a persistent volume for message storage
5. THE docker-compose.yml file SHALL be compatible with the existing Java system's Docker Compose configuration

### Requirement 16: Logging

**User Story:** As a system operator, I want structured logging, so that I can monitor and troubleshoot the system

#### Acceptance Criteria

1. THE Producer_Service SHALL log when it starts successfully
2. THE Producer_Service SHALL log when it receives an order request
3. THE Producer_Service SHALL log when it publishes messages to ActiveMQ
4. THE Producer_Service SHALL log connection loss and reconnection attempts
5. THE Consumer_Service SHALL log when it starts successfully
6. THE Consumer_Service SHALL log when it receives a message
7. THE Consumer_Service SHALL log when it writes a batch to a file
8. THE Consumer_Service SHALL log file write errors and retries
9. THE Consumer_Service SHALL log graceful shutdown operations

### Requirement 17: .NET Project Structure

**User Story:** As a developer, I want a standard .NET solution structure, so that the project follows .NET conventions

#### Acceptance Criteria

1. THE system SHALL use a .NET solution file (.sln) as the root
2. THE Producer_Service SHALL be a separate .NET project using ASP.NET Core Web API
3. THE Consumer_Service SHALL be a separate .NET project using .NET Worker Service or Console Application
4. THE system SHALL use .csproj files for project configuration instead of Maven pom.xml
5. THE system SHALL target .NET 8.0 or later

### Requirement 18: NuGet Package Dependencies

**User Story:** As a developer, I want NuGet packages for ActiveMQ integration, so that I can connect to the message broker

#### Acceptance Criteria

1. THE Producer_Service SHALL use Apache.NMS.ActiveMQ or equivalent NuGet package for ActiveMQ connectivity
2. THE Consumer_Service SHALL use Apache.NMS.ActiveMQ or equivalent NuGet package for ActiveMQ connectivity
3. THE Producer_Service SHALL use Microsoft.AspNetCore.Mvc for HTTP API functionality
4. THE system SHALL use System.Text.Json or Newtonsoft.Json for JSON serialization
5. THE system SHALL use Microsoft.Extensions.Configuration for configuration management
6. THE system SHALL use Microsoft.Extensions.Logging for logging

### Requirement 19: Build and Run Instructions

**User Story:** As a developer, I want clear build and run instructions, so that I can get the system running quickly

#### Acceptance Criteria

1. THE system SHALL include a README.md with instructions for building the .NET solution
2. THE README.md SHALL include instructions for running the Producer_Service
3. THE README.md SHALL include instructions for running the Consumer_Service
4. THE README.md SHALL include instructions for starting ActiveMQ via Docker Compose
5. THE README.md SHALL include example curl commands for testing the Producer API

### Requirement 20: No Unit Tests Required

**User Story:** As a project stakeholder, I want to minimize migration effort, so that the system can be delivered quickly

#### Acceptance Criteria

1. THE migration SHALL not include unit tests for the Producer_Service
2. THE migration SHALL not include unit tests for the Consumer_Service
3. THE migration SHALL not include integration tests
4. THE migration SHALL not include property-based tests
