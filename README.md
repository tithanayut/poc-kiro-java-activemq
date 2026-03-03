# Order Processing Service

A distributed order processing system built with Java, Spring Boot, and ActiveMQ. The system receives HTTP order requests, publishes product messages to ActiveMQ, and processes them in batches for persistent storage.

## Architecture

- **Producer Service**: HTTP server that receives order requests and publishes product messages to ActiveMQ
- **Consumer Service**: Message processor that batches products and writes them to numbered files
- **ActiveMQ Broker**: Message broker providing publish-subscribe messaging via topics

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Docker and Docker Compose

## Quick Start

### 1. Start ActiveMQ

```bash
docker-compose up -d
```

ActiveMQ will be available at:
- OpenWire protocol: `tcp://localhost:61616`
- Web console: `http://localhost:8161` (admin/admin)

### 2. Build the Project

```bash
mvn clean install
```

### 3. Run the Producer

```bash
cd producer
mvn spring-boot:run
```

The Producer HTTP server will start on port 8080.

### 4. Run the Consumer

In a new terminal:

```bash
cd consumer
mvn exec:java -Dexec.mainClass="com.orderprocessing.consumer.ConsumerApplication"
```

The Consumer will start processing messages from ActiveMQ.

## Configuration

### Producer Configuration

Edit `producer/src/main/resources/application.properties`:

```properties
# ActiveMQ Configuration
activemq.broker.url=tcp://localhost:61616
activemq.topic.name=products.topic

# HTTP Server Configuration
server.port=8080
```

### Consumer Configuration

Edit `consumer/src/main/resources/application.properties`:

```properties
# ActiveMQ Configuration
activemq.broker.url=tcp://localhost:61616
activemq.topic.name=products.topic

# Batch Configuration
batch.size=5
output.directory.path=./output
```

## API Usage

### Submit an Order

```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-123",
    "productIds": ["PROD-1", "PROD-2", "PROD-3"]
  }'
```

**Response:**
- `202 Accepted`: Order accepted and products published
- `400 Bad Request`: Missing orderId or productIds
- `503 Service Unavailable`: ActiveMQ connection unavailable

## Output Files

The Consumer writes batches to numbered files in the output directory:

```
output/
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

## File Counter Persistence

The Consumer automatically resumes file numbering after restart by scanning existing files in the output directory. If files `PRODUCT_ORDER_1.txt` through `PRODUCT_ORDER_5.txt` exist, the next batch will be written to `PRODUCT_ORDER_6.txt`.

## Testing

### Run Unit Tests

```bash
mvn test
```

### Run Integration Tests

```bash
mvn verify
```

## Logging

Both services log to console with the following format:

```
2024-01-15 10:30:45.123 [main] INFO  c.o.producer.ProducerApplication - [Producer] Configuration loaded
```

Log levels can be adjusted in `application.properties`:

```properties
logging.level.com.orderprocessing=INFO
```

## Troubleshooting

### Producer returns 503 Service Unavailable

- Check if ActiveMQ is running: `docker ps`
- Verify ActiveMQ is accessible: `telnet localhost 61616`
- Check Producer logs for connection errors

### Consumer not processing messages

- Verify Consumer is connected to ActiveMQ (check logs)
- Ensure topic name matches in both Producer and Consumer configuration
- Check ActiveMQ web console for message count

### Output files not created

- Verify output directory exists and is writable
- Check Consumer logs for file write errors
- Ensure batch size is reached (default: 5 products)

## Stopping the Services

### Stop Producer

Press `Ctrl+C` in the Producer terminal

### Stop Consumer

Press `Ctrl+C` in the Consumer terminal

### Stop ActiveMQ

```bash
docker-compose down
```

## Project Structure

```
order-processing-service/
├── producer/                   # Producer service (Spring Boot)
│   └── src/main/java/com/orderprocessing/producer/
│       ├── controller/        # REST controllers
│       ├── service/           # Business logic
│       ├── model/             # Data models
│       ├── connection/        # ActiveMQ connection management
│       └── config/            # Spring configuration
├── consumer/                   # Consumer service (standalone Java)
│   └── src/main/java/com/orderprocessing/consumer/
│       ├── messaging/         # Message consumption
│       ├── batch/             # Batch state management
│       ├── file/              # File operations
│       └── model/             # Data models
├── docker-compose.yml         # ActiveMQ container configuration
└── pom.xml                    # Maven parent POM
```

## License

This project is for demonstration purposes.
