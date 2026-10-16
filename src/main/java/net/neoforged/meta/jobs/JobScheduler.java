package net.neoforged.meta.jobs;

import net.neoforged.meta.config.ScheduledTaskProperties;
import net.neoforged.meta.config.ScheduledTasksProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Distributed job scheduler that ensures only one replica executes each scheduled job at a time.
 * <p>
 * This replaces the simple cron-based scheduling with a database-backed locking mechanism.
 * <p>
 * Works by:
 * 1. Running a fast poll (every 10 seconds) that checks all configured jobs
 * 2. For each job whose cron expression indicates it's due, try to acquire an atomic lock
 *    for this scheduled slot (a single conditional UPDATE also records the executed slot)
 * 3. If lock acquired: run the job in a separate thread, release lock when done
 * 4. If lock not acquired: another replica is running it or already ran this slot, skip
 */
@Component
@Profile("postgres")
public class JobScheduler {

    private static final Logger logger = LoggerFactory.getLogger(JobScheduler.class);

    private final ScheduledTasksProperties properties;
    private final JobLockService jobLockService;
    private final MinecraftVersionDiscoveryJob minecraftVersionDiscoveryJob;
    private final MavenVersionDiscoveryJob mavenVersionDiscoveryJob;
    private final ExecutorService jobExecutor;

    // Track last scheduled time for each job to determine if it's due
    private final Map<String, Instant> lastScheduledTime = new ConcurrentHashMap<>();

    // Jobs currently executing on this instance (exposed via JobSchedulerHealthIndicator)
    private final Set<String> runningJobIds = ConcurrentHashMap.newKeySet();

    public JobScheduler(ScheduledTasksProperties properties,
                        JobLockService jobLockService,
                        MinecraftVersionDiscoveryJob minecraftVersionDiscoveryJob,
                        MavenVersionDiscoveryJob mavenVersionDiscoveryJob) {
        this.properties = properties;
        this.jobLockService = jobLockService;
        this.minecraftVersionDiscoveryJob = minecraftVersionDiscoveryJob;
        this.mavenVersionDiscoveryJob = mavenVersionDiscoveryJob;

        // Dedicated executor for running jobs (doesn't block the scheduler poll)
        this.jobExecutor = Executors.newFixedThreadPool(
                2, // One thread per job type
                r -> new Thread(r, "job-scheduler-executor")
        );
    }

    /**
     * Poll for jobs that need to run. Runs frequently with a fixed rate.
     */
    @Scheduled(fixedRateString = "${job-scheduler.poll-interval:10s}")
    public void poll() {
        logger.trace("Job scheduler poll");

        // Clean up any expired locks periodically
        jobLockService.cleanupExpired();

        // Check each configured job
        checkAndRunJob("maven-version-discovery",
                properties.mavenVersionDiscovery(),
                mavenVersionDiscoveryJob);

        checkAndRunJob("minecraft-version-discovery",
                properties.minecraftVersionDiscovery(),
                minecraftVersionDiscoveryJob);
    }

    private void checkAndRunJob(String jobId, ScheduledTaskProperties config, Runnable job) {
        if (!config.enabled()) {
            return;
        }

        CronExpression cronExpression = CronExpression.parse(config.cronPattern());
        ZonedDateTime now = ZonedDateTime.now();

        // Check if the job is due: look for next execution in the past 5 seconds to now+5 seconds.
        // Framework 7's cron engine requires a full date-time type (Instant lacks DayOfWeek).
        ZonedDateTime nextExecution = cronExpression.next(now.minusSeconds(5));
        if (nextExecution != null && !nextExecution.isAfter(now.plusSeconds(5))) {
            // Job is due (within 5 seconds of scheduled time)
            logger.debug("Job '{}' is due according to cron expression", jobId);

            // Try to atomically acquire the lock for this scheduled slot
            if (jobLockService.tryAcquire(jobId, nextExecution.toInstant())) {
                logger.info("Starting job '{}'", jobId);
                lastScheduledTime.put(jobId, now.toInstant());

                // Run job in separate thread so poll isn't blocked
                jobExecutor.submit(() -> executeJob(jobId, job));
            }
            // Else: another replica has the lock, skip
        }
    }

    /**
     * @return the ids of jobs currently executing on this instance
     */
    public Set<String> getRunningJobIds() {
        return Set.copyOf(runningJobIds);
    }

    private void executeJob(String jobId, Runnable job) {
        Instant jobStart = Instant.now();
        runningJobIds.add(jobId);
        try {
            job.run();

            long durationMs = Duration.between(jobStart, Instant.now()).toMillis();
            logger.info("Job '{}' completed successfully in {}ms", jobId, durationMs);
        } catch (Exception e) {
            long durationMs = Duration.between(jobStart, Instant.now()).toMillis();
            logger.error("Job '{}' failed after {}ms", jobId, durationMs, e);
            throw e;
        } finally {
            runningJobIds.remove(jobId);
            jobLockService.release(jobId);
        }
    }
}
