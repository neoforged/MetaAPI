package net.neoforged.meta.triggers;

import net.neoforged.meta.db.EventReceiverState;
import net.neoforged.meta.db.EventReceiverStateDao;
import org.hibernate.StaleObjectStateException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This is a facade for the trigger system to remember state for each configured event receiver.
 * <p>
 * Uses optimistic locking (@Version on EventReceiverState) and retry logic to handle
 * concurrent updates from multiple replicas in HA deployments.
 */
@Component
public class EventReceiverStateStore {
    private static final Logger logger = LoggerFactory.getLogger(EventReceiverStateStore.class);
    private static final int MAX_RETRIES = 3;

    private final EventReceiverStateDao dao;

    public EventReceiverStateStore(EventReceiverStateDao dao) {
        this.dao = dao;
    }

    @Transactional(readOnly = true)
    public Map<String, EventReceiverState> getAll() {
        return dao.findAll()
                .stream()
                .collect(Collectors.toMap(
                        EventReceiverState::getId,
                        Function.identity()
                ));
    }

    @Transactional
    public EventReceiverState create(String eventReceiverId) {
        var state = new EventReceiverState();
        state.setId(eventReceiverId);
        dao.saveAndFlush(state);
        return state;
    }

    @Transactional
    @Retryable(
            value = ObjectOptimisticLockingFailureException.class,
            maxAttempts = MAX_RETRIES,
            backoff = @Backoff(delay = 50, multiplier = 2)
    )
    public void recordSuccess(String eventReceiverId, long highestEventIdSeen, long eventsDelivered) {
        var state = dao.findById(eventReceiverId).orElseGet(() -> create(eventReceiverId));
        state.setHighestEventIdSeen(highestEventIdSeen);
        state.setEventsDelivered(state.getEventsDelivered() + eventsDelivered);
        state.setDeliveryState(EventReceiverState.DeliveryState.OK);
        state.setLastSuccess(Instant.now());
        // Reset error state
        state.setFailingSince(null);
        state.setConsecutiveFailures(0);
        state.setLastFailureMessage(null);
        dao.saveAndFlush(state);
    }

    @Transactional
    @Retryable(
            value = ObjectOptimisticLockingFailureException.class,
            maxAttempts = MAX_RETRIES,
            backoff = @Backoff(delay = 50, multiplier = 2)
    )
    public void recordFailure(String eventReceiverId, Throwable error) {
        var state = dao.findById(eventReceiverId).orElseGet(() -> create(eventReceiverId));
        var writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        state.setDeliveryState(EventReceiverState.DeliveryState.FAILING);
        state.setLastFailureMessage(writer.toString());
        state.setLastFailure(Instant.now());
        state.setConsecutiveFailures(state.getConsecutiveFailures() + 1);
        if (state.getFailingSince() == null) {
            state.setFailingSince(Instant.now());
        }
        dao.saveAndFlush(state);
    }

    @Transactional
    @Retryable(
            value = ObjectOptimisticLockingFailureException.class,
            maxAttempts = MAX_RETRIES,
            backoff = @Backoff(delay = 50, multiplier = 2)
    )
    public void pause(String eventReceiverId, @Nullable Instant pausedUntil) {
        var state = dao.findById(eventReceiverId).orElseGet(() -> create(eventReceiverId));
        if (!state.isPaused() || !Objects.equals(state.getPausedUntil(), pausedUntil)) {
            state.setPaused(true);
            state.setPausedUntil(pausedUntil);
            dao.saveAndFlush(state);
        }
    }

    @Transactional
    @Retryable(
            value = ObjectOptimisticLockingFailureException.class,
            maxAttempts = MAX_RETRIES,
            backoff = @Backoff(delay = 50, multiplier = 2)
    )
    public void resume(String eventReceiverId) {
        var state = dao.findById(eventReceiverId).orElse(null);
        if (state != null && state.isPaused()) {
            state.setPaused(false);
            state.setPausedUntil(null);
            dao.saveAndFlush(state);
        }
    }

    @Transactional(readOnly = true)
    @Nullable
    public EventReceiverState getById(String eventReceiverId) {
        return dao.findById(eventReceiverId).orElse(null);
    }

    // Recovery methods - called when retries are exhausted
    @Recover
    public void recoverRecordSuccess(ObjectOptimisticLockingFailureException e, String eventReceiverId, long highestEventIdSeen, long eventsDelivered) {
        logger.warn("Failed to record success for receiver '{}' after {} retries", eventReceiverId, MAX_RETRIES, e);
    }

    @Recover
    public void recoverRecordFailure(ObjectOptimisticLockingFailureException e, String eventReceiverId, Throwable error) {
        logger.warn("Failed to record failure for receiver '{}' after {} retries", eventReceiverId, MAX_RETRIES, e);
    }

    @Recover
    public void recoverPause(ObjectOptimisticLockingFailureException e, String eventReceiverId, Instant pausedUntil) {
        logger.warn("Failed to pause receiver '{}' after {} retries", eventReceiverId, MAX_RETRIES, e);
    }

    @Recover
    public void recoverResume(ObjectOptimisticLockingFailureException e, String eventReceiverId) {
        logger.warn("Failed to resume receiver '{}' after {} retries", eventReceiverId, MAX_RETRIES, e);
    }
}
