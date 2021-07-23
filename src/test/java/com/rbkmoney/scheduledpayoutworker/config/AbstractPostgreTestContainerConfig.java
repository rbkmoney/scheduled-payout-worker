package com.rbkmoney.scheduledpayoutworker.config;

import com.rbkmoney.scheduledpayoutworker.ScheduledPayoutWorkerApplication;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = ScheduledPayoutWorkerApplication.class,
        initializers = AbstractPostgreTestContainerConfig.Initializer.class)
public abstract class AbstractPostgreTestContainerConfig {

    private static final String POSTGRESQL_IMAGE_NAME = "postgres";
    private static final String POSTGRESQL_VERSION = "9.6";
    public static final PostgreSQLContainer DB = (PostgreSQLContainer) new PostgreSQLContainer(DockerImageName
            .parse(POSTGRESQL_IMAGE_NAME)
            .withTag(POSTGRESQL_VERSION))
            .withReuse(true);
    @Autowired
    Flyway flyway;

    @BeforeAll
    public static void beforeAll() {
        DB.start();
        assertTrue(DB.isRunning());
    }

    @BeforeEach
    public void resetDbData() {
        flyway.clean();
        flyway.migrate();
    }

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            TestPropertyValues.of(
                    "spring.datasource.url=" + DB.getJdbcUrl(),
                    "spring.datasource.username=" + DB.getUsername(),
                    "spring.datasource.password=" + DB.getPassword(),
                    "flyway.url=" + DB.getJdbcUrl(),
                    "flyway.user=" + DB.getUsername(),
                    "flyway.password=" + DB.getPassword()
            ).applyTo(configurableApplicationContext);
        }
    }

}
