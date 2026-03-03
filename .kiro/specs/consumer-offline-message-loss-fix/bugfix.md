# Bugfix Requirements Document

## Introduction

The order processing system currently loses messages when the consumer service is offline. The system uses ActiveMQ topics with non-durable subscriptions, which only deliver messages to active subscribers. When the consumer is not running, any messages published by the producer are immediately lost and cannot be recovered when the consumer comes back online.

This bug affects the reliability of the order processing system, as product orders submitted during consumer downtime (maintenance, crashes, or deployments) are permanently lost, leading to incomplete order processing and potential data loss.

The solution will convert the topic subscription to a durable subscription by adding a client ID and subscription name. This allows the topic model to persist messages for offline subscribers while supporting multiple independent consumers in the future, each with their own durable subscription to receive all messages.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN the consumer service is offline AND the producer publishes a message to the topic THEN the message is immediately lost and never delivered to the consumer because the subscription is non-durable

1.2 WHEN the consumer service starts after being offline THEN the system does not retrieve any messages that were published during the offline period because there is no client ID or subscription name configured

1.3 WHEN the consumer service is offline for any duration AND multiple messages are published THEN all messages published during that period are permanently lost due to the non-durable topic subscription

### Expected Behavior (Correct)

2.1 WHEN the consumer service is offline AND the producer publishes a message THEN the message SHALL be persisted by ActiveMQ using a durable subscription with client ID and subscription name

2.2 WHEN the consumer service starts after being offline THEN the system SHALL retrieve and process all messages that were published during the offline period using the durable subscription

2.3 WHEN the consumer service is offline for any duration AND multiple messages are published THEN all messages SHALL be preserved in order and delivered when the consumer reconnects to its durable subscription

2.4 WHEN multiple consumer instances exist in the future THEN each SHALL be able to have its own durable subscription to receive all messages independently

### Unchanged Behavior (Regression Prevention)

3.1 WHEN the consumer service is online AND the producer publishes a message THEN the system SHALL CONTINUE TO deliver and process the message immediately

3.2 WHEN a message is successfully processed AND acknowledged by the consumer THEN the system SHALL CONTINUE TO remove the message from persistence

3.3 WHEN the consumer processes a batch of products THEN the system SHALL CONTINUE TO write them to numbered output files in the correct format

3.4 WHEN the consumer restarts THEN the system SHALL CONTINUE TO resume file numbering correctly by scanning existing output files
