package net.neoforged.meta.health;

import net.neoforged.meta.jobs.JobScheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports which scheduled jobs this instance is currently executing.
 * <p>
 * Always reports UP (jobs do not affect liveness); the running job ids are included as
 * details for debugging multi-replica deployments — e.g. to verify that only one replica
 * holds a given job at a time, or to see a long-running discovery job in progress.
 */
@Component
public class JobSchedulerHealthIndicator implements HealthIndicator {

    private final ObjectProvider<JobScheduler> jobScheduler;

    public JobSchedulerHealthIndicator(ObjectProvider<JobScheduler> jobScheduler) {
        this.jobScheduler = jobScheduler;
    }

    @Override
    public Health health() {
        var scheduler = jobScheduler.getIfAvailable();
        if (scheduler == null) {
            // Non-postgres profile: jobs run via plain cron scheduling on a single instance.
            return Health.up().withDetail("mode", "single-instance").build();
        }
        return Health.up()
                .withDetail("mode", "distributed")
                .withDetail("runningJobs", scheduler.getRunningJobIds())
                .build();
    }
}
