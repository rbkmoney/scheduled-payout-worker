package com.rbkmoney.scheduledpayoutworker.config;

import com.rbkmoney.kafka.common.exception.handler.SeekToCurrentWithSleepBatchErrorHandler;
import com.rbkmoney.machinegun.eventsink.SinkEvent;
import com.rbkmoney.scheduledpayoutworker.config.properties.KafkaSslProperties;
import com.rbkmoney.scheduledpayoutworker.serde.impl.kafka.SinkEventDeserializer;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(KafkaSslProperties.class)
public class KafkaConfig {

    @Value("${retry-policy.maxAttempts}")
    int maxAttempts;
    @Value("${kafka.consumer.auto-offset-reset}")
    private String autoOffsetReset;
    @Value("${kafka.consumer.enable-auto-commit}")
    private boolean enableAutoCommit;
    @Value("${kafka.consumer.group-id}")
    private String groupId;
    @Value("${kafka.client-id}")
    private String clientId;
    @Value("${kafka.consumer.max-poll-records}")
    private int maxPollRecords;
    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;
    @Value("${kafka.topics.invoice.concurrency}")
    private int invoiceConcurrency;
    @Value("${kafka.topics.party-management.concurrency}")
    private int partyConcurrency;

    private Map<String, Object> consumerConfigs(KafkaSslProperties kafkaSslProperties) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, SinkEventDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, enableAutoCommit);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);

        configureSsl(props, kafkaSslProperties);

        return props;
    }

    private void configureSsl(Map<String, Object> props, KafkaSslProperties kafkaSslProperties) {
        if (kafkaSslProperties.isEnabled()) {
            props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SSL.name());
            props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG,
                    new File(kafkaSslProperties.getTrustStoreLocation()).getAbsolutePath());
            props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, kafkaSslProperties.getTrustStorePassword());
            props.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, kafkaSslProperties.getKeyStoreType());
            props.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, kafkaSslProperties.getTrustStoreType());
            props.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG,
                    new File(kafkaSslProperties.getKeyStoreLocation()).getAbsolutePath());
            props.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, kafkaSslProperties.getKeyStorePassword());
            props.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, kafkaSslProperties.getKeyPassword());
        }
    }

    @Bean
    public ConsumerFactory<String, SinkEvent> invoiceConsumerFactory(KafkaSslProperties kafkaSslProperties) {
        Map<String, Object> config = consumerConfigs(kafkaSslProperties);
        config.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId + "-invoice");
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConsumerFactory<String, SinkEvent> pmConsumerFactory(KafkaSslProperties kafkaSslProperties) {
        Map<String, Object> config = consumerConfigs(kafkaSslProperties);
        config.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId + "-pm");
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SinkEvent> invContainerFactory(
            ConsumerFactory<String, SinkEvent> invoiceConsumerFactory) {
        var factory = createGeneralKafkaListenerFactory(invoiceConsumerFactory);
        factory.setBatchListener(true);
        factory.setBatchErrorHandler(new SeekToCurrentWithSleepBatchErrorHandler());
        factory.setConcurrency(invoiceConcurrency);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SinkEvent> pmContainerFactory(
            ConsumerFactory<String, SinkEvent> pmConsumerFactory) {
        var factory = createGeneralKafkaListenerFactory(pmConsumerFactory);
        factory.setBatchListener(true);
        factory.setBatchErrorHandler(new SeekToCurrentWithSleepBatchErrorHandler());
        factory.setConcurrency(partyConcurrency);
        return factory;
    }

    private ConcurrentKafkaListenerContainerFactory<String, SinkEvent> createGeneralKafkaListenerFactory(
            ConsumerFactory<String, SinkEvent> consumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, SinkEvent>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }

}
