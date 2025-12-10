# Webhook Delivery System Design

## Overview

This document outlines the design for a webhook delivery mechanism that notifies external consumers about version changes in the NeoForge Meta API. The system is designed to handle multiple subscribers, provide reliable delivery with retries, and efficiently batch events.

## Architecture

### Components

1. **Event Generation** - Creates events during version discovery
2. **Event Storage** - Persists events in the database
3. **Webhook Subscribers** - Registered webhook endpoints
4. **Delivery Scheduler** - Batches and triggers webhook deliveries
5. **Delivery Executor** - Sends batched events to subscribers with retry logic
6. **Delivery Tracker** - Tracks delivery status and progress

### Core Design Strategy

The webhook system uses a **timestamp-based ordering with lookback window** approach to handle transaction race conditions:

1. **Events ordered by timestamp:** All events have a `createdAt` timestamp and are delivered in chronological order
2. **Lookback window:** When creating delivery batches, the system looks back 5 minutes before the cursor to catch late-committing transactions
3. **Duplicate prevention:** Track delivered event IDs within the lookback window to prevent re-delivery
4. **Cursor tracking:** Each subscriber has a cursor tracking the timestamp of the last delivered event

**Why this approach?**
- **Handles AUTO_INCREMENT race conditions:** A transaction with ID 9 might commit after ID 10, but the lookback window catches it
- **Minimal duplicates:** Delivered event tracking prevents re-delivery within the lookback window
- **Simple and reliable:** No complex sequence number generation that could cause uniqueness violations
- **Scalable:** With 10-20 subscribers, tracking delivered IDs is efficient

## Data Model

### Event Entity

Table: `events`

| Column           | Type          | Constraints       | Description                                                  |
|------------------|---------------|-------------------|--------------------------------------------------------------|
| id               | Long          | PK, Auto          | Primary key (AUTO_INCREMENT)                                 |
| eventType        | String        | NOT NULL          | Event type: NEW_VERSION, VERSION_UPDATED, VERSION_DEPRECATED |
| component        | String        | NOT NULL          | Component name: NEOFORGE, MINECRAFT                          |
| componentVersion | String        | NOT NULL          | Version string (e.g., "21.0.1")                              |
| payload          | String (TEXT) |                   | JSON payload with full event details                         |
| createdAt        | Instant       | NOT NULL, indexed | Event creation timestamp (used for ordering)                 |

**Key Design Decision: Timestamp-Based Ordering with Lookback**

The system uses `createdAt` timestamp for event ordering with a lookback window to handle transaction race conditions:

**The Problem:**
```
Transaction A: Creates event, gets ID 9, starts at T0
Transaction B: Creates event, gets ID 10, starts at T1
Transaction B: Commits at T2
Transaction A: Commits at T3 (commits AFTER B despite having lower ID)
```

If we only used cursor-based queries (`WHERE id > lastCursor`), event 9 would be missed when the cursor jumps from 8 to 10.

**The Solution:**
- Events are ordered by `createdAt` timestamp (with ID as tiebreaker)
- Batch creation uses a **lookback window** (e.g., 5 minutes) before the cursor timestamp
- Track delivered event IDs to prevent duplicate deliveries within the lookback period
- This catches events from transactions that commit late

### Webhook Subscriber Entity

Table: `webhook_subscribers`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | Long | PK, Auto | Primary key |
| name | String | NOT NULL | Human-readable name |
| url | String | NOT NULL | Webhook endpoint URL |
| secret | String | NOT NULL | HMAC secret for signature verification |
| active | boolean | NOT NULL, default true | Whether subscriber is active |
| eventFilter | String | | JSON array of event types to subscribe to |
| createdAt | Instant | NOT NULL | Registration timestamp |
| lastSuccessfulDeliveryAt | Instant | | Last successful delivery timestamp |

### Delivery Batch Entity

Table: `webhook_delivery_batches`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | Long | PK, Auto | Primary key |
| subscriber | WebhookSubscriber | FK, NOT NULL | Reference to subscriber |
| startSequenceNumber | Long | NOT NULL | Start of event sequence range (inclusive) |
| endSequenceNumber | Long | NOT NULL | End of event sequence range (inclusive) |
| status | String | NOT NULL | Status: PENDING, DELIVERING, SUCCESS, FAILED |
| attemptCount | int | NOT NULL, default 0 | Number of delivery attempts |
| maxAttempts | int | NOT NULL, default 5 | Maximum retry attempts |
| nextRetryAt | Instant | | Scheduled time for next retry |
| lastError | String | | Last error message if failed |
| createdAt | Instant | NOT NULL | Batch creation timestamp |
| deliveredAt | Instant | | Successful delivery timestamp |

### Delivery Cursor Entity

Table: `webhook_delivery_cursors`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | Long | PK, Auto | Primary key |
| subscriber | WebhookSubscriber | FK, UNIQUE, NOT NULL | Reference to subscriber (one cursor per subscriber) |
| lastBatchedTimestamp | Instant | NOT NULL | Timestamp of last event included in a batch (cursor position) |
| updatedAt | Instant | NOT NULL | Last cursor update timestamp |

### Delivered Event Tracking Entity

Table: `webhook_delivered_events`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | Long | PK, Auto | Primary key |
| subscriber | WebhookSubscriber | FK, NOT NULL | Reference to subscriber |
| eventId | Long | NOT NULL | Event ID that was delivered |
| deliveredAt | Instant | NOT NULL, indexed | Delivery timestamp (for cleanup) |

**Composite Index:** `(subscriber, eventId)` for fast duplicate checking

**Purpose:** Tracks which events have been delivered to prevent duplicates within the lookback window. Old entries are cleaned up after the lookback period expires.

### Batch Event Mapping Entity

Table: `webhook_batch_events`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | Long | PK, Auto | Primary key |
| batchId | Long | FK, NOT NULL, indexed | Reference to delivery batch |
| eventId | Long | NOT NULL | Event ID included in this batch |

**Purpose:** Maps which events are included in each delivery batch. Created during batch creation, used during delivery execution to fetch the exact events to deliver.

## Event Generation

### When Events Are Created

Events are generated during the version discovery process:

1. **NEW_VERSION** - When a new version is first discovered
2. **VERSION_UPDATED** - When metadata for an existing version changes
3. **VERSION_DEPRECATED** - When a version is marked as deprecated

### Transaction Consistency

Events MUST be created in the same transaction as the version change:

```java
@Transactional
public void processNewVersion(VersionData data) {
    // 1. Save or update the version
    Version version = versionRepository.save(data);

    // 2. Create event in same transaction
    Event event = Event.builder()
        .eventType("NEW_VERSION")
        .component("NEOFORGE")
        .componentVersion(version.getVersion())
        .payload(serializeEventPayload(version))
        .createdAt(Instant.now())
        .build();

    eventRepository.save(event);

    // Transaction commits - both version and event are saved atomically
}
```

## Webhook Delivery Process

### High-Level Flow

1. **Batch Creation** - Scheduler periodically creates delivery batches
2. **Event Batching** - Group undelivered events for each subscriber
3. **Delivery Execution** - Send batched events to webhook endpoints
4. **Retry Logic** - Handle failures with exponential backoff
5. **Cursor Update** - Track progress for each subscriber

### Batch Creation (Scheduled Job)

```java
@Scheduled(fixedDelay = 30000) // Every 30 seconds
@Transactional
public void createDeliveryBatches() {
    List<WebhookSubscriber> activeSubscribers = subscriberRepository.findByActiveTrue();
    Instant now = Instant.now();

    for (WebhookSubscriber subscriber : activeSubscribers) {
        // Get or create cursor (starts at epoch for new subscribers)
        WebhookDeliveryCursor cursor = cursorRepository.findBySubscriber(subscriber)
            .orElse(new WebhookDeliveryCursor(subscriber, Instant.EPOCH));

        // Calculate lookback window (e.g., 5 minutes before cursor)
        Instant lookbackStart = cursor.getLastBatchedTimestamp()
            .minus(LOOKBACK_WINDOW_MINUTES, ChronoUnit.MINUTES);

        // Fetch events from lookback window to now
        List<Event> events = eventRepository.findByCreatedAtBetweenOrderByCreatedAtAscIdAsc(
            lookbackStart,
            now
        );

        if (events.isEmpty()) {
            continue;
        }

        // Get delivered event IDs to filter duplicates
        Set<Long> deliveredEventIds = deliveredEventRepository
            .findEventIdsBySubscriberAndDeliveredAtAfter(
                subscriber,
                lookbackStart
            );

        // Filter out already-delivered events
        List<Event> newEvents = events.stream()
            .filter(e -> !deliveredEventIds.contains(e.getId()))
            .collect(Collectors.toList());

        if (!newEvents.isEmpty()) {
            // Get timestamp range for batch
            Instant batchStart = newEvents.get(0).getCreatedAt();
            Instant batchEnd = newEvents.get(newEvents.size() - 1).getCreatedAt();

            // Create delivery batch
            WebhookDeliveryBatch batch = WebhookDeliveryBatch.builder()
                .subscriber(subscriber)
                .startSequenceNumber(batchStart.toEpochMilli()) // Store for reference
                .endSequenceNumber(batchEnd.toEpochMilli())
                .status("PENDING")
                .createdAt(Instant.now())
                .build();

            deliveryBatchRepository.save(batch);

            // Store event IDs in batch metadata for delivery
            batchEventRepository.saveAll(
                newEvents.stream()
                    .map(e -> new BatchEvent(batch.getId(), e.getId()))
                    .collect(Collectors.toList())
            );

            // Update cursor to latest event timestamp
            cursor.setLastBatchedTimestamp(batchEnd);
            cursor.setUpdatedAt(now);
            cursorRepository.save(cursor);
        }
    }
}
```

**Key Components:**

- **Lookback Window:** Configurable period (e.g., 5 minutes) to catch late-committing transactions
- **Delivered Event Tracking:** Prevents duplicate delivery of events in the lookback window
- **Timestamp Ordering:** Events ordered by `createdAt` then `id` for consistency
- **Cursor:** Tracks the timestamp of the last event included in a batch

### Delivery Execution (Separate Job)

```java
@Scheduled(fixedDelay = 10000) // Every 10 seconds
public void executeDeliveries() {
    // Find batches ready for delivery/retry
    List<WebhookDeliveryBatch> batches = deliveryBatchRepository
        .findByStatusInAndNextRetryAtBefore(
            List.of("PENDING", "FAILED"),
            Instant.now()
        );

    for (WebhookDeliveryBatch batch : batches) {
        deliveryExecutor.executeAsync(batch);
    }
}
```

```java
@Async
@Transactional
public void executeAsync(WebhookDeliveryBatch batch) {
    try {
        // Mark as delivering
        batch.setStatus("DELIVERING");
        batch.setAttemptCount(batch.getAttemptCount() + 1);
        deliveryBatchRepository.save(batch);

        // Fetch events in batch (by event IDs stored during batch creation)
        List<Long> eventIds = batchEventRepository.findEventIdsByBatchId(batch.getId());
        List<Event> events = eventRepository.findAllById(eventIds);

        // Sort by timestamp for consistent ordering
        events.sort(Comparator.comparing(Event::getCreatedAt)
                              .thenComparing(Event::getId));

        // Apply subscriber's event filter if configured
        events = applyEventFilter(events, batch.getSubscriber().getEventFilter());

        if (events.isEmpty()) {
            // All events filtered out, mark as success
            batch.setStatus("SUCCESS");
            batch.setDeliveredAt(Instant.now());
            deliveryBatchRepository.save(batch);
            return;
        }

        // Build webhook payload
        WebhookPayload payload = WebhookPayload.builder()
            .events(events.stream()
                .map(this::toEventDto)
                .collect(Collectors.toList()))
            .build();

        // Send HTTP request
        sendWebhookRequest(batch.getSubscriber(), payload);

        // Mark as successful
        Instant deliveredAt = Instant.now();
        batch.setStatus("SUCCESS");
        batch.setDeliveredAt(deliveredAt);
        batch.getSubscriber().setLastSuccessfulDeliveryAt(deliveredAt);

        // Track delivered event IDs to prevent duplicates in lookback window
        List<DeliveredEvent> deliveredEvents = events.stream()
            .map(e -> DeliveredEvent.builder()
                .subscriber(batch.getSubscriber())
                .eventId(e.getId())
                .deliveredAt(deliveredAt)
                .build())
            .collect(Collectors.toList());

        deliveredEventRepository.saveAll(deliveredEvents);

    } catch (Exception e) {
        handleDeliveryFailure(batch, e);
    }

    deliveryBatchRepository.save(batch);
}
```

### Retry Strategy with Exponential Backoff

```java
private void handleDeliveryFailure(WebhookDeliveryBatch batch, Exception error) {
    batch.setLastError(error.getMessage());

    if (batch.getAttemptCount() >= batch.getMaxAttempts()) {
        // Give up after max attempts
        batch.setStatus("FAILED");
        batch.setNextRetryAt(null);

        // Log permanent failure
        logger.error("Webhook delivery failed permanently for batch {}: {}",
            batch.getId(), error.getMessage());

        // Optional: Disable subscriber after consecutive failures
        checkAndDisableSubscriber(batch.getSubscriber());

    } else {
        // Schedule retry with exponential backoff
        batch.setStatus("FAILED");

        // Backoff: 1min, 2min, 4min, 8min, 16min
        long backoffMinutes = (long) Math.pow(2, batch.getAttemptCount() - 1);
        batch.setNextRetryAt(Instant.now().plus(backoffMinutes, ChronoUnit.MINUTES));
    }
}
```

### HTTP Request with Signature

```java
private void sendWebhookRequest(WebhookSubscriber subscriber, WebhookPayload payload) {
    String jsonBody = objectMapper.writeValueAsString(payload);

    // Generate HMAC signature
    String signature = generateHmacSignature(subscriber.getSecret(), jsonBody);

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(subscriber.getUrl()))
        .header("Content-Type", "application/json")
        .header("X-Webhook-Signature", signature)
        .header("User-Agent", "NeoForge-Meta-API/1.0")
        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
        .timeout(Duration.ofSeconds(30))
        .build();

    HttpResponse<String> response = httpClient.send(request,
        HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new WebhookDeliveryException(
            "Webhook returned status " + response.statusCode()
        );
    }
}
```

## Event Ordering and Delivery Guarantees

### Guarantees

1. **At-Least-Once Delivery** - Events may be delivered multiple times in rare cases (e.g., failed retry tracking)
2. **Ordered Delivery** - Events are delivered ordered by `createdAt` timestamp, then `id`
3. **No Skipped Events** - The lookback window ensures late-committing transactions are caught
4. **Minimal Duplicates** - Delivered event tracking prevents re-delivery within the lookback window

### Handling Duplicates (Consumer Side)

Webhook consumers should implement idempotency using the event ID:

```json
{
  "events": [
    {
      "id": 42,
      "eventType": "NEW_VERSION",
      "component": "NEOFORGE",
      "componentVersion": "21.0.1",
      "createdAt": "2025-12-07T10:30:00Z",
      "data": { ... }
    }
  ]
}
```

Consumers should track delivered event IDs to ignore duplicates:
- Store the highest processed event ID per event type
- Or maintain a sliding window of processed IDs (e.g., last hour)
- Skip events with IDs already seen

### Why Duplicates Are Rare But Possible

1. **Normal Case:** Delivered event tracking prevents duplicates within lookback window
2. **Edge Cases:**
   - System crash between delivery and tracking storage
   - Database replication lag (if using replicas)
   - Manual batch replay for recovery

Therefore, consumers **must** implement idempotency.

## Event Cleanup

Old events and delivery tracking records should be cleaned up to prevent unbounded growth:

```java
@Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
@Transactional
public void cleanupOldData() {
    Instant now = Instant.now();

    // 1. Clean up delivered event tracking older than lookback window + safety margin
    // We only need to track delivered events within the lookback window
    Instant deliveryTrackingCutoff = now
        .minus(LOOKBACK_WINDOW_MINUTES + 60, ChronoUnit.MINUTES); // Extra hour for safety

    int deletedTracking = deliveredEventRepository
        .deleteByDeliveredAtBefore(deliveryTrackingCutoff);

    logger.info("Cleaned up {} delivered event tracking records", deletedTracking);

    // 2. Clean up old events
    // Find minimum cursor timestamp across all active subscribers
    Instant minCursorTimestamp = cursorRepository
        .findMinLastBatchedTimestamp()
        .orElse(now);

    // Keep events for at least 7 days
    Instant eventRetentionCutoff = now.minus(7, ChronoUnit.DAYS);

    // Only delete events that are:
    // 1. Older than 7 days, AND
    // 2. Before all active subscriber cursors (already delivered)
    Instant eventCutoff = minCursorTimestamp.isBefore(eventRetentionCutoff)
        ? minCursorTimestamp
        : eventRetentionCutoff;

    int deletedEvents = eventRepository.deleteByCreatedAtBefore(eventCutoff);

    logger.info("Cleaned up {} old events", deletedEvents);

    // 3. Clean up old successful delivery batches (keep for 30 days)
    Instant batchCutoff = now.minus(30, ChronoUnit.DAYS);
    int deletedBatches = deliveryBatchRepository
        .deleteByStatusAndCreatedAtBefore("SUCCESS", batchCutoff);

    logger.info("Cleaned up {} old delivery batches", deletedBatches);
}
```

## Monitoring and Observability

### Metrics to Track

1. **Event Generation Rate** - Events created per minute
2. **Delivery Success Rate** - Successful deliveries / total attempts
3. **Delivery Latency** - Time from event creation to delivery
4. **Retry Rate** - Failed deliveries requiring retries
5. **Subscriber Health** - Last successful delivery per subscriber

### Health Checks

```java
@Component
public class WebhookHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        // Check for batches stuck in DELIVERING state
        long stuckBatches = deliveryBatchRepository.countByStatusAndCreatedAtBefore(
            "DELIVERING",
            Instant.now().minus(5, ChronoUnit.MINUTES)
        );

        if (stuckBatches > 0) {
            return Health.down()
                .withDetail("stuckBatches", stuckBatches)
                .build();
        }

        // Check for permanently failed batches
        long failedBatches = deliveryBatchRepository.countByStatus("FAILED");

        if (failedBatches > 100) {
            return Health.degraded()
                .withDetail("failedBatches", failedBatches)
                .build();
        }

        return Health.up().build();
    }
}
```

## Subscriber Management API

### Register Webhook

```
POST /v1/webhooks/subscribers
Content-Type: application/json

{
  "name": "My Service",
  "url": "https://example.com/webhooks/neoforge",
  "secret": "shared-secret-key",
  "eventFilter": ["NEW_VERSION", "VERSION_UPDATED"]
}
```

### List Webhooks

```
GET /v1/webhooks/subscribers
```

### Update Webhook

```
PATCH /v1/webhooks/subscribers/{id}
```

### Delete Webhook

```
DELETE /v1/webhooks/subscribers/{id}
```

### View Delivery History

```
GET /v1/webhooks/subscribers/{id}/deliveries?limit=50
```

## Manual Delivery Trigger

For testing or manual batch processing:

```java
@PostMapping("/v1/webhooks/trigger")
public ResponseEntity<Void> triggerDelivery() {
    batchCreationService.createDeliveryBatches();
    deliveryExecutor.executeDeliveries();
    return ResponseEntity.accepted().build();
}
```

## Security Considerations

1. **HMAC Signatures** - All webhook requests include signature for verification
2. **HTTPS Only** - Only allow HTTPS webhook URLs (enforce in validation)
3. **Rate Limiting** - Limit webhook registration per IP/API key
4. **Secret Management** - Store subscriber secrets encrypted
5. **Timeout Protection** - 30-second timeout prevents hanging on slow endpoints

## Configuration

```yaml
meta-api:
  webhooks:
    batch-creation-interval: 30s
    delivery-execution-interval: 10s
    lookback-window-minutes: 5 # Time window to catch late-committing transactions
    max-retry-attempts: 5
    request-timeout: 30s
    event-retention-days: 7 # Minimum event retention
    delivered-tracking-retention-minutes: 360 # 6 hours (lookback + safety margin)
    max-events-per-batch: 1000 # Limit batch size for very active systems
```

### Configuration Tuning

**Lookback Window (`lookback-window-minutes`):**
- **5 minutes (recommended):** Good balance for most workloads
- **Increase if:** You have very long-running transactions or high database load
- **Decrease if:** You want lower latency and are confident transactions commit quickly
- **Trade-off:** Longer window = more duplicate checking overhead, but safer against missed events

**Delivered Tracking Retention (`delivered-tracking-retention-minutes`):**
- Should be **at least lookback window + 1 hour** for safety
- Tracks which events were delivered to prevent duplicates in the lookback window
- Cleaned up automatically - no need for long retention

## Future Enhancements

1. **Dead Letter Queue** - Store permanently failed deliveries for manual inspection
2. **Webhook Verification** - Challenge-response verification during registration
3. **Event Replay** - Allow subscribers to request historical events
4. **Filtering by Component** - Subscribe to only NEOFORGE or MINECRAFT events
5. **Rate Limiting** - Limit delivery rate per subscriber
6. **Circuit Breaker** - Temporarily disable failing subscribers automatically
7. **Webhook Transformations** - Custom payload formats per subscriber
