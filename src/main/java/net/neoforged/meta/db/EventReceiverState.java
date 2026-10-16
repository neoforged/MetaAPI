package net.neoforged.meta.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

@Entity
public class EventReceiverState {
    @Id
    private String id;

    /**
     * Optimistic locking version for concurrent update safety in HA deployments.
     */
    @Version
    private Long version;

    /**
     * The current state
     */
    public enum DeliveryState {
        NEVER,
        OK,
        FAILING
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryState deliveryState = DeliveryState.NEVER;

    /**
     * Used to produce incremental updates. At the time the receiver is updated,
     * we record what the highest event-id in the DB is, and store it here when
     * the update succeeds.
     */
    @Nullable
    private Long highestEventIdSeen;

    /**
     * The number of actual events delivered to the receiver.
     */
    @Column(nullable = false)
    private long eventsDelivered;

    /**
     * Timestamp of the last successful delivery to this receiver. This is not set to null when there is a failure.
     */
    @Nullable
    private Instant lastSuccess;

    /**
     * The timestamp of the last failed delivery. This is not set to null when there is a successful delivery.
     */
    @Nullable
    private Instant lastFailure;

    /**
     * The number of consecutive failed delivery attempts if the last delivery has failed.
     * <p>
     * This number will be 0 if {@link #deliveryState} is not {@link DeliveryState#FAILING}.
     */
    @Column(nullable = false)
    private int consecutiveFailures;

    /**
     * If the last delivery failed, this is the timestamp of the first consecutive failure before that.
     * <p>
     * This is {@code null} if {@link #deliveryState} is not {@link DeliveryState#FAILING}.
     */
    @Nullable
    private Instant failingSince;

    /**
     * The last error if the receiver is currently in an error state.
     * <p>
     * This is {@code null} if {@link #deliveryState} is not {@link DeliveryState#FAILING}.
     */
    @Column(columnDefinition = "TEXT")
    @Nullable
    private String lastFailureMessage;

    /**
     * Allows an event receiver to be paused.
     */
    @Column(nullable = false)
    private boolean paused;

    /**
     * If paused is true and this is not-null, the receiver is only paused until the stated time.
     */
    @Nullable
    private Instant pausedUntil;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public DeliveryState getDeliveryState() {
        return deliveryState;
    }

    public void setDeliveryState(DeliveryState deliveryState) {
        this.deliveryState = deliveryState;
    }

    @Nullable
    public Long getHighestEventIdSeen() {
        return highestEventIdSeen;
    }

    public void setHighestEventIdSeen(@Nullable Long lastEventDelivered) {
        this.highestEventIdSeen = lastEventDelivered;
    }

    public @Nullable Instant getLastSuccess() {
        return lastSuccess;
    }

    public void setLastSuccess(@Nullable Instant lastSuccess) {
        this.lastSuccess = lastSuccess;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(int consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    public @Nullable Instant getFailingSince() {
        return failingSince;
    }

    public void setFailingSince(@Nullable Instant failingSince) {
        this.failingSince = failingSince;
    }

    public @Nullable Instant getLastFailure() {
        return lastFailure;
    }

    public void setLastFailure(@Nullable Instant lastFailure) {
        this.lastFailure = lastFailure;
    }

    public @Nullable String getLastFailureMessage() {
        return lastFailureMessage;
    }

    public void setLastFailureMessage(@Nullable String lastFailureMessage) {
        this.lastFailureMessage = lastFailureMessage;
    }

    public long getEventsDelivered() {
        return eventsDelivered;
    }

    public void setEventsDelivered(long eventsDelivered) {
        this.eventsDelivered = eventsDelivered;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public @Nullable Instant getPausedUntil() {
        return pausedUntil;
    }

    public void setPausedUntil(@Nullable Instant pausedUntil) {
        this.pausedUntil = pausedUntil;
    }
}
