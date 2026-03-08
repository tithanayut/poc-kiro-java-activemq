# Order Processing Service - .NET Migration

A distributed order processing system migrated from Java Spring Boot to C# .NET. This system demonstrates a producer-consumer messaging architecture using ActiveMQ for asynchronous message processing.

## Overview

The Order Processing Service consists of two independent .NET services that communicate via Apache ActiveMQ:

- **Producer Service**: ASP.NET Core Web API that receives HTTP order requests and publishes product messages to ActiveMQ
- **Consumer Service**: .NET Worker Service that processes messages in batches and writes them to numbered files

### How It Works

1. **Client submits an order** via HTTP POST with an order ID and list of product IDs
2. **Producer decomposes** the order into individual product messages (one per product)
3. **ActiveMQ persists** messages using durable topic subscriptions
4. **Consumer accumulates** products in memory until batch size (5) is reached
5. **Batch is written** to a numbered file in pipe-delimited format

### Example Flow

**Input Request:**
```json
POST /orders
{
  "orderId": "ORD-123",
  "productIds": ["PROD-A", "PROD-B", "PROD-C"]
}
```

**Producer publishes 3 messages to ActiveMQ:**
```json
{"orderId": "ORD-123", "productId": "PROD-A"}
{"orderId": "ORD-123", "productId": "PROD-B"}
{"orderId": "ORD-123", "productId": "PROD-C"}
```

**Consumer accumulates in batch:**
```
Batch: [ORD-123|PROD-A, ORD-123|PROD-B, ORD-123|PROD-C, ...]
```

**When batch reaches 5 products, writes to file:**
```
PRODUCT_ORDER_1.txt:
ORD-123|PROD-A
ORD-123|PROD-B
ORD-123|PROD-C
ORD-456|PROD-X
ORD-456|PROD-Y
```

### Key Features

- **Zero Message Loss**: Durable subscriptions ensure messages are persisted even when consumer is offline
- **Batch Efficiency**: Writing in batches of 5 reduces I/O operations by 80%
- **Automatic Recovery**: Consumer resumes file numbering by scanning existing files on startup
- **Connection Resilience**: Automatic reconnection with exponential backoff when ActiveMQ is unavailable
- **Graceful Shutdown**: Consumer writes partial batches before stopping to prevent data loss

## Prerequisites

- .NET 8.0 SDK or later
- Docker and Docker Compose
- curl (for testing)

## Quick Start

### 1. Start ActiveMQ

Start the ActiveMQ broker using Docker Compose:

```bash
docker-compose up -d
```

ActiveMQ will be available at:
- OpenWire protocol: `tcp://localhost:61616`
- Web console: `http://localhost:8161` (credentials: admin/admin)

Verify ActiveMQ is running:
```bash
docker ps
```

### 2. Build the .NET Solution

Build both Producer and Consumer services:

```bash
dotnet build OrderProcessing.sln
```

Or build individual projects:
```bash
dotnet build Producer.Service/Producer.Service.csproj
dotnet build Consumer.Service/Consumer.Service.csproj
```

### 3. Run the Producer Service

Start the Producer service:

```bash
cd Producer.Service
dotnet run
```

Or run directly from the solution root:
```bash
dotnet run --project Producer.Service/Producer.Service.csproj
```

The Producer HTTP API will start on `http://localhost:5000`.

You should see log output indicating successful startup:
```
info: Producer.Service.Program[0]
      Producer service starting...
info: Producer.Service.Services.ConnectionManager[0]
      Connected to ActiveMQ at activemq:tcp://localhost:61616
```

### 4. Run the Consumer Service

In a new terminal, start the Consumer service:

```bash
cd Consumer.Service
dotnet run
```

Or run directly from the solution root:
```bash
dotnet run --project Consumer.Service/Consumer.Service.csproj
```

The Consumer will start processing messages from ActiveMQ.

You should see log output indicating successful startup:
```
info: Consumer.Service.Worker[0]
      Consumer Worker starting at: 01/15/2024 10:30:45
info: Consumer.Service.Services.MessageConsumer[0]
      Connected to ActiveMQ topic: product.orders
```

## Testing the System

### Submit an Order

Use curl to submit an order with multiple products:

```bash
curl -X POST http://localhost:5000/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-123",
    "productIds": ["PROD-1", "PROD-2", "PROD-3"]
  }'
```

**Expected Response:**
```
HTTP/1.1 202 Accepted
```

### Submit Multiple Orders

Test batch processing by submitting multiple orders:

```bash
# Order 1 - 3 products
curl -X POST http://localhost:5000/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId": "ORD-001", "productIds": ["PROD-A", "PROD-B", "PROD-C"]}'

# Order 2 - 2 products (completes first batch of 5)
curl -X POST http://localhost:5000/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId": "ORD-002", "productIds": ["PROD-D", "PROD-E"]}'

# Order 3 - 4 products
curl -X POST http://localhost:5000/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId": "ORD-003", "productIds": ["PROD-F", "PROD-G", "PROD-H", "PROD-I"]}'
```

After these requests, you should see:
- `PRODUCT_ORDER_1.txt` with 5 products (PROD-A through PROD-E)
- `PRODUCT_ORDER_2.txt` with 4 products (PROD-F through PROD-I) after Consumer shutdown

### Test Error Handling

**Missing orderId:**
```bash
curl -X POST http://localhost:5000/orders \
  -H "Content-Type: application/json" \
  -d '{"productIds": ["PROD-1"]}'
```
Expected: `400 Bad Request`

**Missing productIds:**
```bash
curl -X POST http://localhost:5000/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId": "ORD-123"}'
```
Expected: `400 Bad Request`

**Empty productIds array:**
```bash
curl -X POST http://localhost:5000/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId": "ORD-123", "productIds": []}'
```
Expected: `400 Bad Request`

**ActiveMQ unavailable:**
```bash
# Stop ActiveMQ
docker-compose down

# Try to submit order
curl -X POST http://localhost:5000/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId": "ORD-123", "productIds": ["PROD-1"]}'
```
Expected: `503 Service Unavailable`

## Output Files

The Consumer writes batches to numbered files in the output directory:

```
Consumer.Service/output/
├── PRODUCT_ORDER_1.txt
├── PRODUCT_ORDER_2.txt
└── PRODUCT_ORDER_3.txt
```

Each file contains products in pipe-delimited format:

```
ORD-123|PROD-1
ORD-123|PROD-2
ORD-456|PROD-3
ORD-456|PROD-4
ORD-789|PROD-5
```

### File Counter Persistence

The Consumer automatically resumes file numbering after restart by scanning existing files. If files `PRODUCT_ORDER_1.txt` through `PRODUCT_ORDER_5.txt` exist, the next batch will be written to `PRODUCT_ORDER_6.txt`.

## Configuration

### Producer Service Configuration

Edit `Producer.Service/appsettings.json`:

```json
{
  "Logging": {
    "LogLevel": {
      "Default": "Information",
      "Microsoft.AspNetCore": "Warning"
    }
  },
  "Kestrel": {
    "Endpoints": {
      "Http": {
        "Url": "http://localhost:5000"
      }
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

**Configuration Options:**

- `Kestrel.Endpoints.Http.Url`: HTTP server listening address (default: `http://localhost:5000`)
- `Producer.BrokerUrl`: ActiveMQ broker connection URL (default: `activemq:tcp://localhost:61616`)
- `Producer.TopicName`: ActiveMQ topic name for publishing messages (default: `product.orders`)
- `Producer.Retry.InitialDelayMs`: Initial retry delay in milliseconds (default: 1000)
- `Producer.Retry.MaxDelayMs`: Maximum retry delay in milliseconds (default: 60000)
- `Producer.Retry.BackoffMultiplier`: Exponential backoff multiplier (default: 2.0)
- `Logging.LogLevel.Default`: Default log level (options: Trace, Debug, Information, Warning, Error, Critical)

### Consumer Service Configuration

Edit `Consumer.Service/appsettings.json`:

```json
{
  "Logging": {
    "LogLevel": {
      "Default": "Information",
      "Microsoft.Hosting.Lifetime": "Information"
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

**Configuration Options:**

- `Consumer.BrokerUrl`: ActiveMQ broker connection URL (default: `activemq:tcp://localhost:61616`)
- `Consumer.TopicName`: ActiveMQ topic name for subscription (default: `product.orders`)
- `Consumer.ClientId`: Unique client identifier for durable subscription (default: `consumer-service-1`)
- `Consumer.SubscriptionName`: Durable subscription name (default: `product-order-subscription`)
- `Consumer.BatchSize`: Number of products per batch before writing to file (default: 5)
- `Consumer.OutputDirectory`: Directory path for output files (default: `./output`)
- `Consumer.FileWriter.MaxRetryAttempts`: Maximum file write retry attempts (default: 3)
- `Consumer.FileWriter.RetryDelayMs`: Delay between file write retries in milliseconds (default: 1000)
- `Logging.LogLevel.Default`: Default log level (options: Trace, Debug, Information, Warning, Error, Critical)

## Architecture

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

### Producer Service Components

- **OrderController**: HTTP endpoint handling and request validation
- **MessagePublisher**: Message publishing and ActiveMQ interaction
- **ConnectionManager**: ActiveMQ connection lifecycle and retry logic with exponential backoff

### Consumer Service Components

- **MessageConsumer**: Message subscription and batch coordination with durable subscriptions
- **BatchProcessor**: Batch accumulation and file writing coordination
- **FileWriter**: File I/O operations with retry logic
- **FileCounterManager**: File counter persistence across restarts

## Troubleshooting

### Producer returns 503 Service Unavailable

**Cause**: ActiveMQ is not running or not accessible

**Solutions**:
- Check if ActiveMQ is running: `docker ps`
- Verify ActiveMQ is accessible: `telnet localhost 61616`
- Check Producer logs for connection errors
- Restart ActiveMQ: `docker-compose restart activemq`

### Consumer not processing messages

**Cause**: Consumer is not connected to ActiveMQ or topic name mismatch

**Solutions**:
- Verify Consumer is connected to ActiveMQ (check logs)
- Ensure topic name matches in both Producer and Consumer configuration
- Check ActiveMQ web console (`http://localhost:8161`) for message count
- Verify durable subscription exists in ActiveMQ console

### Output files not created

**Cause**: Batch size not reached or output directory not writable

**Solutions**:
- Verify output directory exists and is writable
- Check Consumer logs for file write errors
- Ensure batch size is reached (default: 5 products)
- Submit more orders to complete a batch

### Build errors

**Cause**: Missing .NET SDK or NuGet package restore issues

**Solutions**:
- Verify .NET SDK is installed: `dotnet --version`
- Restore NuGet packages: `dotnet restore`
- Clean and rebuild: `dotnet clean && dotnet build`

## Stopping the Services

### Stop Producer Service

Press `Ctrl+C` in the Producer terminal

### Stop Consumer Service

Press `Ctrl+C` in the Consumer terminal

The Consumer will gracefully shutdown and write any partial batch to a file before exiting.

### Stop ActiveMQ

```bash
docker-compose down
```

To remove volumes as well:
```bash
docker-compose down -v
```

## Project Structure

```
OrderProcessing/
├── Producer.Service/              # Producer service (ASP.NET Core Web API)
│   ├── Controllers/              # REST controllers
│   │   └── OrderController.cs
│   ├── Services/                 # Business logic
│   │   ├── MessagePublisher.cs
│   │   └── ConnectionManager.cs
│   ├── Models/                   # Data models
│   │   ├── OrderRequest.cs
│   │   ├── ProductMessage.cs
│   │   └── ProducerSettings.cs
│   ├── Interfaces/               # Service interfaces
│   │   ├── IMessagePublisher.cs
│   │   └── IConnectionManager.cs
│   ├── appsettings.json          # Configuration
│   └── Program.cs                # Application entry point
│
├── Consumer.Service/              # Consumer service (.NET Worker Service)
│   ├── Services/                 # Business logic
│   │   ├── MessageConsumer.cs
│   │   ├── BatchProcessor.cs
│   │   ├── FileWriter.cs
│   │   └── FileCounterManager.cs
│   ├── Models/                   # Data models
│   │   ├── Product.cs
│   │   ├── ConsumerSettings.cs
│   │   └── FileWriterSettings.cs
│   ├── Interfaces/               # Service interfaces
│   │   ├── IMessageConsumer.cs
│   │   ├── IBatchProcessor.cs
│   │   ├── IFileWriter.cs
│   │   └── IFileCounterManager.cs
│   ├── output/                   # Output directory for batch files
│   ├── appsettings.json          # Configuration
│   ├── Program.cs                # Application entry point
│   └── Worker.cs                 # Background worker
│
├── docker-compose.yml             # ActiveMQ container configuration
├── OrderProcessing.sln            # .NET solution file
└── README.md                      # This file
```

## Development

### Running in Development Mode

Both services support development-specific configuration via `appsettings.Development.json`.

Set the environment variable:
```bash
export ASPNETCORE_ENVIRONMENT=Development  # Linux/macOS
set ASPNETCORE_ENVIRONMENT=Development     # Windows
```

### Viewing Logs

Both services log to console. Adjust log levels in `appsettings.json`:

```json
{
  "Logging": {
    "LogLevel": {
      "Default": "Information",
      "Producer.Service": "Debug",
      "Consumer.Service": "Debug"
    }
  }
}
```

### ActiveMQ Web Console

Access the ActiveMQ web console at `http://localhost:8161`:
- Username: `admin`
- Password: `admin`

Use the console to:
- Monitor topic message counts
- View durable subscriptions
- Check connection status
- Browse messages

## Migration Notes

This project is a migration from Java Spring Boot to C# .NET. Key differences:

- **Framework**: Spring Boot → ASP.NET Core / .NET Worker Service
- **Dependency Injection**: Spring IoC → Microsoft.Extensions.DependencyInjection
- **Configuration**: application.properties → appsettings.json
- **Build Tool**: Maven → dotnet CLI
- **Package Manager**: Maven Central → NuGet
- **ActiveMQ Client**: Spring JMS → Apache.NMS.ActiveMQ

The functional behavior remains identical to the original Java implementation.

## License

This project is for demonstration purposes.
