package com.rbkmoney.scheduledpayoutworker.config;

import com.rbkmoney.scheduledpayoutworker.ScheduledPayoutWorkerApplication;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ContextConfiguration(classes = ScheduledPayoutWorkerApplication.class,
        initializers = AbstractKafkaTestContainerConfig.Initializer.class)
public abstract class AbstractKafkaTestContainerConfig extends AbstractPostgreTestContainerConfig {

    private static final String CONFLUENT_IMAGE_NAME = "confluentinc/cp-kafka";
    private static final String CONFLUENT_PLATFORM_VERSION = "6.1.2";

    protected static KafkaContainer KAFKA = new KafkaContainer(DockerImageName
            .parse(CONFLUENT_IMAGE_NAME)
            .withTag(CONFLUENT_PLATFORM_VERSION))
            .withEmbeddedZookeeper()
            .withReuse(true);

    @BeforeAll
    public static void beforeAll() {
        KAFKA.start();
        DB.start();

        Startables.deepStart(Stream.of(KAFKA, DB))
                .join();

        assertTrue(KAFKA.isRunning());
        assertTrue(DB.isRunning());
    }

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            TestPropertyValues.of(
                    "kafka.bootstrap-servers=" + KAFKA.getBootstrapServers()
            ).applyTo(configurableApplicationContext);
        }
    }

}
