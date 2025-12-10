package net.neoforged.meta;

import net.neoforged.meta.config.MetaApiProperties;
import net.neoforged.meta.jobs.MavenVersionDiscoveryJob;
import net.neoforged.meta.jobs.MinecraftVersionDiscoveryJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(MetaApiProperties.class)
@EnableScheduling
public class MetaApiApplication {

    /**
     * Spring profile enabled when being run as a CLI console command.
     */
    public static final String CLI_PROFILE = "console";

    private static final Logger logger = LoggerFactory.getLogger(MetaApiApplication.class);

    public static void main(String[] args) {
        // Check if the first argument is a subcommand (doesn't start with --)
        if (args.length > 0) {
            String potentialSubcommand = args[0];

            // Remove the subcommand from args before passing to Spring
            String[] springArgs = new String[args.length - 1];
            System.arraycopy(args, 1, springArgs, 0, args.length - 1);

            switch (potentialSubcommand) {
                case "poll-minecraft-versions":
                    runConsoleCommand(MinecraftVersionDiscoveryJob.class, springArgs);
                    return;
                case "poll-maven-versions":
                    runConsoleCommand(MavenVersionDiscoveryJob.class, springArgs);
                    return;
            }
        }

        // Normal server startup
        SpringApplication.run(MetaApiApplication.class, args);
    }

    private static void runConsoleCommand(Class<? extends Runnable> jobClass, String[] args) {
        logger.info("Running: {}", jobClass);

        // Create a non-web Spring context for running jobs
        var app = new SpringApplicationBuilder(MetaApiApplication.class)
                .profiles(CLI_PROFILE)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .build(args);

        try (var context = app.run(args)) {
            int exitCode = runJob(context, jobClass);
            System.exit(exitCode);
        } catch (Exception e) {
            logger.error("Failed to run: {}", jobClass, e);
            System.exit(1);
        }
    }

    private static int runJob(ConfigurableApplicationContext context, Class<? extends Runnable> jobClass) {
        try {
            logger.info("Executing Minecraft metadata polling job");
            context.getBean(jobClass).run();
            logger.info("Job completed successfully");
            return 0;
        } catch (Exception e) {
            logger.error("Job execution failed", e);
            return 1;
        }
    }
}
