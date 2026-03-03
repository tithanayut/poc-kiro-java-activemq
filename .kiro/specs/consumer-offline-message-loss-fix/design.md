# Consumer Offline Message Loss Fix - Bugfix Design

## Overview

The order processing system loses messages when the consumer service is offline because it uses a non-durable topic subscription. Messages published to the topic while no active subscriber is connected are immediately discarded by ActiveMQ. This fix converts the subscription to a durable subscription by configuring a client ID on the connection and creating a durable subscriber with a subscription name. This allows ActiveMQ to persist messages for offline subscribers and deliver them when the consumer reconnects, ensuring zero message loss during consumer downtime.

## Glossary

- **Bug_Condition (C)**: The condition that triggers message loss - when the consumer is offline and messages are published to the topic
- **Property (P)**: The desired behavior - messages published while consumer is offline should be persisted and delivered upon reconnection
- **Preservation**: Existing message processing behavior for online consumers that must remain unchanged by the fix
- **MessageConsumer**: The class in `consumer/src/main/java/com/orderprocessing/consumer/messaging/MessageConsumer.java` that subscribes to the ActiveMQ topic and processes order messages
- **Non-durable Subscription**: A topic subscription that only receives messages while actively connected; messages are lost if no subscriber is present
- **Durable Subscription**: A topic subscription identified by client ID and subscription name that persists messages for offline subscribers
- **Client ID**: A unique identifier set on the JMS connection that enables durable subscriptions
- **Subscription Name**: A unique name for the durable subscription that allows ActiveMQ to track which messages have been delivered

## Bug Details

### Fault Condition

The bug manifests when the consumer service is not running (offline) and the producer publishes messages to the topic. The `MessageConsumer` class creates a non-durable subscription using `session.createConsumer(topic)` without setting a client ID on the connection or using `session.createDurableSubscriber()`. This causes ActiveMQ to discard messages immediately if no active subscriber is connected.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type MessagePublishEvent
  OUTPUT: boolean
  
  RETURN input.consumerServiceStatus == OFFLINE
         AND input.messagePublishedToTopic == true
         AND connection.clientID == null
         AND subscriptionType == NON_DURABLE
END FUNCTION
```

### Examples

- **Example 1**: Consumer is stopped for deployment. Producer publishes order message with orderId="ORD-001". Message is lost. When consumer restarts, the message is never processed.
- **Example 2**: Consumer crashes at 10:00 AM. Producer publishes 50 order messages between 10:00-10:30 AM. Consumer restarts at 10:30 AM. All 50 messages are permanently lost.
- **Example 3**: Consumer is offline for maintenance. Producer publishes messages for orderIds "ORD-100", "ORD-101", "ORD-102". When consumer comes back online, it receives no messages and processing continues from new messages only.
- **Edge Case**: Consumer is online and processing normally. Producer publishes message. Consumer receives and processes message immediately (this should continue to work after the fix).

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Online message processing must continue to work exactly as before - messages delivered immediately when consumer is running
- Message acknowledgment behavior must remain unchanged - messages acknowledged after successful processing
- Batch processing logic must remain unchanged - products accumulated and written to files when batch is full
- File writing behavior must remain unchanged - numbered output files with correct format

**Scope:**
All scenarios where the consumer is ONLINE and actively processing messages should be completely unaffected by this fix. This includes:
- Real-time message delivery and processing
- Message parsing and Product object creation
- Batch state management and file writing
- Error handling and logging
- Connection lifecycle management (start/stop)

## Hypothesized Root Cause

Based on the bug description and code analysis, the root cause is:

1. **Missing Client ID**: The `ActiveMQConnectionFactory.createConnection()` creates a connection without setting a client ID, which is required for durable subscriptions
   - Line: `connection = connectionFactory.createConnection();`
   - No `connection.setClientID()` call is present

2. **Non-Durable Consumer Creation**: The code uses `session.createConsumer(topic)` instead of `session.createDurableSubscriber(topic, subscriptionName)`
   - Line: `consumer = session.createConsumer(topic);`
   - This creates a non-durable subscription that doesn't persist messages

3. **No Subscription Name**: There is no subscription name defined to identify the durable subscription
   - Durable subscriptions require a unique name to track message delivery state

4. **Configuration Gap**: The system lacks configuration for client ID and subscription name
   - These should be configurable to support multiple independent consumers in the future

## Correctness Properties

Property 1: Fault Condition - Messages Persisted During Offline Period

_For any_ message published to the topic while the consumer service is offline (isBugCondition returns true), the fixed MessageConsumer SHALL persist the message in ActiveMQ's durable subscription storage and deliver it to the consumer when it reconnects, ensuring zero message loss.

**Validates: Requirements 2.1, 2.2, 2.3**

Property 2: Preservation - Online Message Processing Behavior

_For any_ message published to the topic while the consumer service is online (isBugCondition returns false), the fixed MessageConsumer SHALL process the message exactly as the original code does, preserving immediate delivery, acknowledgment, batch processing, and file writing behavior.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4**

## Fix Implementation

### Changes Required

Assuming our root cause analysis is correct:

**File**: `consumer/src/main/java/com/orderprocessing/consumer/messaging/MessageConsumer.java`

**Function**: `start()`

**Specific Changes**:
1. **Add Client ID Configuration**: Add a client ID field to the class and set it on the connection before calling `connection.start()`
   - Add field: `private final String clientId;`
   - Add to constructor parameters
   - Call: `connection.setClientID(clientId);` after creating connection

2. **Add Subscription Name Configuration**: Add a subscription name field to identify the durable subscription
   - Add field: `private final String subscriptionName;`
   - Add to constructor parameters

3. **Replace Non-Durable Consumer with Durable Subscriber**: Change `session.createConsumer(topic)` to `session.createDurableSubscriber(topic, subscriptionName)`
   - Replace line: `consumer = session.createConsumer(topic);`
   - With: `consumer = session.createDurableSubscriber(topic, subscriptionName);`

4. **Update Constructor**: Modify constructor signature to accept clientId and subscriptionName parameters
   - Add parameters after topicName
   - Store in fields

5. **Update Logging**: Add log statements to indicate durable subscription is being used
   - Log client ID and subscription name during connection setup

**File**: `consumer/src/main/java/com/orderprocessing/consumer/ConsumerApplication.java` (if exists)

**Changes**: Update MessageConsumer instantiation to pass client ID and subscription name (likely from configuration)

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bug on unfixed code, then verify the fix works correctly and preserves existing behavior.

### Exploratory Fault Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix. Confirm or refute the root cause analysis. If we refute, we will need to re-hypothesize.

**Test Plan**: Write integration tests that start the consumer, stop it, publish messages while offline, then restart the consumer and verify messages are NOT received (on unfixed code). This will confirm the bug and validate our understanding of the root cause.

**Test Cases**:
1. **Basic Offline Message Loss Test**: Start consumer, stop it, publish 1 message, restart consumer, verify message is NOT received (will fail on unfixed code - confirms bug)
2. **Multiple Message Loss Test**: Start consumer, stop it, publish 10 messages, restart consumer, verify 0 messages received (will fail on unfixed code - confirms bug)
3. **Timing Test**: Start consumer, stop it, wait 5 seconds, publish message, wait 5 seconds, restart consumer, verify message is NOT received (will fail on unfixed code - confirms bug)
4. **Persistence Test**: Start consumer, stop it, publish message, restart broker, restart consumer, verify message is NOT received (will fail on unfixed code - confirms bug and broker behavior)

**Expected Counterexamples**:
- Messages published while consumer is offline are not delivered when consumer restarts
- Message count in ActiveMQ shows 0 pending messages for the subscription
- Possible causes: no client ID set, using createConsumer instead of createDurableSubscriber, no subscription name

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := MessageConsumer_fixed.start()
  ASSERT messagesPersistedDuringOffline(result)
  ASSERT messagesDeliveredOnReconnect(result)
END FOR
```

**Test Cases**:
1. **Single Message Persistence**: Stop consumer, publish 1 message, restart consumer, verify message is received and processed
2. **Multiple Message Persistence**: Stop consumer, publish 10 messages, restart consumer, verify all 10 messages received in order
3. **Long Offline Period**: Stop consumer, wait 1 hour, publish messages, restart consumer, verify all messages received
4. **Broker Restart Persistence**: Stop consumer, publish messages, restart broker, restart consumer, verify messages still delivered

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT MessageConsumer_original.processMessage(input) = MessageConsumer_fixed.processMessage(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically across the input domain
- It catches edge cases that manual unit tests might miss
- It provides strong guarantees that behavior is unchanged for all non-buggy inputs

**Test Plan**: Observe behavior on UNFIXED code first for online message processing, then write property-based tests capturing that behavior.

**Test Cases**:
1. **Online Message Processing Preservation**: Observe that messages are processed immediately when consumer is online on unfixed code, then write test to verify this continues after fix
2. **Batch Processing Preservation**: Observe that batch state accumulates products and writes files correctly on unfixed code, then write test to verify this continues after fix
3. **Acknowledgment Preservation**: Observe that messages are acknowledged after processing on unfixed code, then write test to verify this continues after fix
4. **Error Handling Preservation**: Observe that malformed messages are handled correctly on unfixed code, then write test to verify this continues after fix

### Unit Tests

- Test that client ID is set correctly on connection
- Test that durable subscriber is created with correct subscription name
- Test that message processing logic remains unchanged
- Test that acknowledgment behavior is preserved
- Test error handling for invalid messages

### Property-Based Tests

- Generate random sequences of start/stop/publish operations and verify no messages are lost
- Generate random message payloads and verify processing behavior is identical for online scenarios
- Generate random offline durations and verify all messages are eventually delivered
- Test that multiple restarts don't cause duplicate message delivery

### Integration Tests

- Test full consumer lifecycle: start, process messages, stop, restart
- Test offline message accumulation and delivery on reconnect
- Test that batch file writing continues to work correctly with persisted messages
- Test that file numbering remains correct after processing offline messages
- Test broker restart scenarios with durable subscriptions
- Test multiple consumer instances with different client IDs (future scenario)
