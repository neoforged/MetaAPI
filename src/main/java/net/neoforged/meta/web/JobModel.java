package net.neoforged.meta.web;

import net.neoforged.meta.util.TimeFormatter;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.config.TaskExecutionOutcome.Status;

import java.time.Instant;

public record JobModel(
        String name,
        @Nullable Instant nextExecution,
        @Nullable Instant lastExecution,
        Status lastStatus,
        @Nullable Throwable lastError) {

    public String relativeNextExecution() {
        return TimeFormatter.formatRelativeTime(nextExecution);
    }

    public String relativeLastExecution() {
        return TimeFormatter.formatRelativeTime(lastExecution);
    }

    public String absoluteNextExecution() {
        return TimeFormatter.formatAbsoluteTime(nextExecution);
    }

    public String absoluteLastExecution() {
        return TimeFormatter.formatAbsoluteTime(lastExecution);
    }
}
