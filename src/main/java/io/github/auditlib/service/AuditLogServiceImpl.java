package io.github.auditlib.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.auditlib.config.AuditProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Бизнес логика стартера
 */
@Service
public class AuditLogServiceImpl implements AuditLogService {

    // Использую отдельные логгеры для каждого типа операции+пути логгирования
    private final Logger methodConsoleLogger = LogManager.getLogger("AUDIT_METHOD_CONSOLE");
    private final Logger methodFileLogger = LogManager.getLogger("AUDIT_METHOD_FILE");
    private final Logger httpConsoleLogger = LogManager.getLogger("AUDIT_HTTP_CONSOLE");
    private final Logger httpFileLogger = LogManager.getLogger("AUDIT_HTTP_FILE");
    private final Logger logger = LogManager.getLogger(AuditLogServiceImpl.class);

    private final AuditProperties properties;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public AuditLogServiceImpl(AuditProperties properties,
                               ObjectMapper objectMapper,
                               KafkaTemplate<String, String> kafkaTemplate) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Логирование события метода
     * @param event Событие
     */
    @Override
    public Map<AuditProperties.OutputType, Boolean> logMethodEvent(Map<String, Object> event) {

        Map<AuditProperties.OutputType, Boolean> result = new HashMap<>();

        for (AuditProperties.OutputType target : properties.getOutputs()) {
            switch (target) {
                case CONSOLE -> {
                    logMethodToFileOrConsole(event, methodConsoleLogger);
                    result.put(target, true);
                }
                case FILE -> {
                    logMethodToFileOrConsole(event, methodFileLogger);
                    result.put(target, true);
                }
                case KAFKA -> {
                    logMethodToKafka(event);
                    result.put(target, true);
                }
                default ->  logMethodToFileOrConsole(event, methodConsoleLogger);
            }
        }
        return result;
    }

    /**
     * Логирование события метода в файл или консоль
     * @param event Событие
     * @param logger Логгер, куда будет отправлено сообщение
     */
    private void logMethodToFileOrConsole(Map<String, Object> event, Logger logger) {

        String formattedEvent = formatMethodEventToString(event);

        switch (event.get("logLevel").toString()) {
            case "TRACE" -> logger.trace(formattedEvent);
            case "DEBUG" -> logger.debug(formattedEvent);
            case "INFO" -> logger.info(formattedEvent);
            case "WARN" -> logger.warn(formattedEvent);
            case "ERROR" -> logger.error(formattedEvent);
            default -> logger.debug(formattedEvent);
        }
    }

    /**
     * Логирование HTTP события
     */
    @Override
    public void logHttpEvent(Map<String, Object> event) {
        for (AuditProperties.OutputType target : properties.getOutputs()) {
            switch (target) {
                case CONSOLE -> httpConsoleLogger.info(formatHttpEventToString(event));
                case FILE -> httpFileLogger.info(formatHttpEventToString(event));
                case KAFKA -> logHttpToKafka(event);
                default -> httpConsoleLogger.info(formatHttpEventToString(event));
            }
        }
    }

    /**
     * Форматирование события метода в строку для логов (файл/консоль)
     */
    private String formatMethodEventToString(Map<String, Object> event) {
        StringBuilder sb = new StringBuilder();

        LocalDateTime timestamp = (LocalDateTime) event.get("timestamp");
        sb.append(timestamp).append(" ");

        sb.append(event.get("logLevel")).append(" ");

        sb.append(event.get("eventType")).append(" ");

        sb.append(event.get("correlationId")).append(" ");

        sb.append(event.get("methodName"));

        String eventType = (String) event.get("eventType");
        switch (eventType) {
            case "START" -> {
                Object[] args = (Object[]) event.get("arguments");
                if (args != null) {
                    sb.append(" args = ").append(Arrays.toString(args));
                }
            }
            case "END" -> {
                Object result = event.get("result");
                if (result != null) {
                    sb.append(" result = ").append(result);
                }
            }
            case "ERROR" -> {
                String errorMessage = (String) event.get("errorMessage");
                if (errorMessage != null) {
                    sb.append(" error = ").append(errorMessage);
                }
            }
            default -> {
                sb.append(" Unknown type of event ");
            }
        }

        return sb.toString();
    }

    /**
     * Форматирование HTTP события в строку для логов (файл/консоль)
     */
    private String formatHttpEventToString(Map<String, Object> event) {
        StringBuilder sb = new StringBuilder();

        LocalDateTime timestamp = (LocalDateTime) event.get("timestamp");
        sb.append(timestamp).append(" ");

        sb.append(event.get("direction")).append(" ");

        sb.append(event.get("method")).append(" ");

        sb.append(event.get("statusCode")).append(" ");

        sb.append(event.get("uri"));

        Object requestBody = event.get("requestBody");
        if (requestBody != null) {
            sb.append(" RequestBody = ").append(requestBody);
        }

        Object responseBody = event.get("responseBody");
        if (responseBody != null) {
            sb.append(" ResponseBody = ").append(responseBody);
        }

        return sb.toString();
    }

    /**
     * Логирование события метода в кафку (как json объект)
     */
    public void logMethodToKafka(Map<String, Object> event) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(event);
            String topic = properties.getKafka().getMethodTopic();
            String correlationId = (String) event.get("correlationId");

            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(topic, correlationId, jsonMessage);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    logger.error("Failed to send method audit event to Kafka", ex);
                }
            });

        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize method audit event to JSON", e);
        }
    }

    /**
     * Логирование HTTP события в кафку (как json объект)
     */
    public void logHttpToKafka(Map<String, Object> event) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(event);
            String topic = properties.getKafka().getHttpTopic();
            String direction = (String) event.get("direction");
            String method = (String) event.get("method");
            String key = direction + "_" + method + "_" + System.currentTimeMillis();

            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(topic, key, jsonMessage);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    logger.error("Failed to send HTTP audit event to Kafka", ex);
                }
            });

        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize HTTP audit event to JSON", e);
        }
    }

}
