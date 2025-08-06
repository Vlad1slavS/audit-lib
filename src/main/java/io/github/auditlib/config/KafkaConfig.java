package io.github.auditlib.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурация кафки
 */
@Configuration
@EnableTransactionManagement
public class KafkaConfig {

    @Bean
    public KafkaTemplate<String, String> auditKafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ProducerFactory<String, String> auditProducerFactory(AuditProperties properties) {
        Map<String, Object> configProps = new HashMap<>();

        AuditProperties.Kafka kafkaProps = properties.getKafka();

        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProps.getBootstrapServers());
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        configProps.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "audit-transaction-");
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, kafkaProps.isEnableIdempotence());
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, kafkaProps.getMaxInFlightRequestsPerConnection());
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        configProps.put(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, 60000);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTransactionManager kafkaTransactionManager(ProducerFactory<String, String> producerFactory) {
        return new KafkaTransactionManager(producerFactory);
    }

}
