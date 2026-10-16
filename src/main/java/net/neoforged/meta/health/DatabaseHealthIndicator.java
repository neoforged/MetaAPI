package net.neoforged.meta.health;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

/**
 * Reports database connectivity and connection pool status for K8s readiness checks.
 * <p>
 * Replaces the built-in {@code db} health indicator (disabled in application.yaml) with
 * more useful details: which database is connected, and the HikariCP pool state so
 * saturation can be spotted from /actuator/health during debugging.
 */
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            var metadata = connection.getMetaData();
            var builder = Health.up()
                    .withDetail("product", metadata.getDatabaseProductName())
                    .withDetail("version", metadata.getDatabaseProductVersion());

            var poolStats = dataSource instanceof HikariDataSource hikari ? hikari.getHikariPoolMXBean() : null;
            if (poolStats != null) {
                builder.withDetail("pool.activeConnections", poolStats.getActiveConnections())
                        .withDetail("pool.idleConnections", poolStats.getIdleConnections())
                        .withDetail("pool.totalConnections", poolStats.getTotalConnections())
                        .withDetail("pool.threadsAwaitingConnection", poolStats.getThreadsAwaitingConnection());
            }

            return builder.build();
        } catch (Exception e) {
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}
