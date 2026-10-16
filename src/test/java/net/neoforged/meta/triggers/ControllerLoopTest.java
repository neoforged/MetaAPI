package net.neoforged.meta.triggers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerLoopTest {

    @Test
    @Timeout(value = 10)
    void testTriggerService() {
        ControllerLoop controllerLoop;
        try (var context = createControllerContext()) {
            controllerLoop = context.getBean(ControllerLoop.class);
            assertTrue(controllerLoop.isRunning());
        }
        assertFalse(controllerLoop.isRunning());
    }

    private static ConfigurableApplicationContext createControllerContext() {
        // TriggerConfiguration's beans depend on @Components (e.g. EventReceiverFactory,
        // EventDeliveryController) that the real application picks up via component
        // scanning, so scan them here as well.
        var app = new SpringApplicationBuilder(TriggerTestConfiguration.class)
                .bannerMode(Banner.Mode.OFF)
                .web(WebApplicationType.NONE)
                .build();
        return app.run();
    }

    // Auto-configuration is required for the datasource/JPA infrastructure; a plain
    // @Configuration source does not trigger it on its own.
    @EnableAutoConfiguration
    // Without a @SpringBootConfiguration in this context, Boot cannot infer the JPA
    // base packages on its own; declare them explicitly for both repositories and entities.
    @EntityScan(basePackages = "net.neoforged.meta.db")
    @EnableJpaRepositories(basePackages = "net.neoforged.meta.db")
    @ComponentScan(basePackageClasses = TriggerConfiguration.class)
    static class TriggerTestConfiguration {
    }
}