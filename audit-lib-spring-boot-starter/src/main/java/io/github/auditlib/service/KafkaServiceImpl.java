package io.github.auditlib.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.auditlib.config.AuditProperties;
import io.github.auditlib.model.HttpAuditEvent;
import io.github.auditlib.model.MethodAuditEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * Сервис для работы с Kafka в транзакционном режиме
 */
@Service
public class KafkaServiceImpl implements KafkaService {

    private final Logger logger = LogManager.getLogger(KafkaServiceImpl.class);

    private final AuditProperties properties;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaServiceImpl(AuditProperties properties,
                            ObjectMapper objectMapper,
                            KafkaTemplate<String, String> kafkaTemplate) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Логирование события метода в кафку (как json объект)
     * Используется транзакционная аннотация для exactly-once гарантии
     */
    @Override
    @Transactional("kafkaTransactionManager")
    public boolean sendMethodEventToKafka(MethodAuditEvent event) {
        String correlationId = event.getCorrelationId();
        try {
            String jsonMessage = objectMapper.writeValueAsString(event);
            String topic = properties.getKafka().getMethodTopic();
            int timeoutSeconds = properties.getKafka().getSendTimeoutSeconds();

            kafkaTemplate.send(topic, correlationId, jsonMessage)
                    .get(timeoutSeconds, TimeUnit.SECONDS);

            logger.debug("Method event sent successfully: correlationId={}", correlationId);
            return true;

        } catch (Exception e) {
            logger.error("Failed to send method event, correlationId={}", correlationId, e);
            throw new RuntimeException("Kafka send failed", e);
        }
    }

    /**
     * Логирование HTTP события в кафку (как json объект)
     * Используется транзакционная аннотация для exactly-once гарантии
     */
    @Transactional("kafkaTransactionManager")
    public boolean sendHttpEventToKafka(HttpAuditEvent event) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(event);
            String topic = properties.getKafka().getHttpTopic();
            int timeoutSeconds = properties.getKafka().getSendTimeoutSeconds();
            String direction = event.getDirection();
            String method = event.getMethod();
            String key = direction + "_" + method + "_" + System.currentTimeMillis();

            kafkaTemplate.send(topic, key, jsonMessage).get(timeoutSeconds, TimeUnit.SECONDS);

            logger.debug("HTTP audit event sent successfully: key={}", key);
            return true;

        } catch (Exception e) {
            logger.error("Failed to send HTTP audit event", e);
            throw new RuntimeException("Kafka send failed", e);
        }
    }

}
