package net.neoforged.meta.util;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TimeFormatter {
    private static final DateTimeFormatter ABSOLUTE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.of("UTC"));

    /**
     * Formats an Instant as a human-readable relative time string.
     *
     * @param instant The instant to format
     * @return A string like "5 minutes ago" or "in 2 hours"
     */
    public static String formatRelativeTime(Instant instant) {
        if (instant == null) {
            return null;
        }

        Instant now = Instant.now();
        Duration duration = Duration.between(now, instant);
        boolean future = !duration.isNegative();
        duration = duration.abs();

        long seconds = duration.getSeconds();
        long minutes = duration.toMinutes();
        long hours = duration.toHours();
        long days = duration.toDays();

        String timeString;
        if (seconds < 60) {
            timeString = seconds + (seconds == 1 ? " second" : " seconds");
        } else if (minutes < 60) {
            timeString = minutes + (minutes == 1 ? " minute" : " minutes");
        } else if (hours < 24) {
            timeString = hours + (hours == 1 ? " hour" : " hours");
        } else {
            timeString = days + (days == 1 ? " day" : " days");
        }

        return future ? "in " + timeString : timeString + " ago";
    }

    /**
     * Formats an Instant as an absolute timestamp string.
     *
     * @param instant The instant to format
     * @return A string like "2025-12-10 14:30:00 UTC"
     */
    public static String formatAbsoluteTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return ABSOLUTE_FORMATTER.format(instant);
    }
}
