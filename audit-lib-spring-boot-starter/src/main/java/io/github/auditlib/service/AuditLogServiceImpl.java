package io.github.auditlib.service;

import io.github.auditlib.config.AuditProperties;
import io.github.auditlib.model.HttpAuditEvent;
import io.github.auditlib.model.MethodAuditEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

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

    private final AuditProperties properties;
    private final KafkaService kafkaService;

    public AuditLogServiceImpl(AuditProperties properties,
                               KafkaService kafkaService) {
        this.properties = properties;
        this.kafkaService = kafkaService;
    }

    /**
     * Логирование события метода
     * @param event Событие
     */
    @Override
    public Map<AuditProperties.OutputType, Boolean> logMethodEvent(MethodAuditEvent event) {

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
                    kafkaService.sendMethodEventToKafka(event);
                    result.put(target, true);
                }
                default -> logMethodToFileOrConsole(event, methodConsoleLogger);
            }
        }
        return result;
    }

    /**
     * Логирование события метода в файл или консоль
     * @param event Событие
     * @param logger Логгер, куда будет отправлено сообщение
     */
    private void logMethodToFileOrConsole(MethodAuditEvent event, Logger logger) {

        String formattedEvent = formatMethodEventToString(event);

        switch (event.getLogLevel()) {
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
    public void logHttpEvent(HttpAuditEvent event) {
        for (AuditProperties.OutputType target : properties.getOutputs()) {
            switch (target) {
                case CONSOLE -> httpConsoleLogger.info(formatHttpEventToString(event));
                case FILE -> httpFileLogger.info(formatHttpEventToString(event));
                case KAFKA -> kafkaService.sendHttpEventToKafka(event);
                default -> httpConsoleLogger.info(formatHttpEventToString(event));
            }
        }
    }

    /**
     * Форматирование события метода в строку для логов (файл/консоль)
     */
    private String formatMethodEventToString(MethodAuditEvent event) {
        StringBuilder sb = new StringBuilder();

        sb.append(event.getTimestamp()).append(" ");
        sb.append(event.getLogLevel()).append(" ");
        sb.append(event.getEventType()).append(" ");
        sb.append(event.getCorrelationId()).append(" ");
        sb.append(event.getMethodName());

        switch (event.getEventType()) {
            case "START" -> {
                Object[] args = event.getArguments();
                if (args != null) {
                    sb.append(" args = ").append(Arrays.toString(args));
                }
            }
            case "END" -> {
                Object result = event.getResult();
                if (result != null) {
                    sb.append(" result = ").append(result);
                }
            }
            case "ERROR" -> {
                String errorMessage = event.getErrorMessage();
                if (errorMessage != null) {
                    sb.append(" error = ").append(errorMessage);
                }
            }
            default -> sb.append(" Unknown type of event ");
        }

        return sb.toString();
    }

    /**
     * Форматирование HTTP события в строку для логов (файл/консоль)
     */
    private String formatHttpEventToString(HttpAuditEvent event) {
        StringBuilder sb = new StringBuilder();

        sb.append(event.getTimestamp()).append(" ");
        sb.append(event.getDirection()).append(" ");
        sb.append(event.getMethod()).append(" ");
        sb.append(event.getStatusCode()).append(" ");
        sb.append(event.getUri());

        String requestBody = event.getRequestBody();
        if (requestBody != null) {
            sb.append(" RequestBody = ").append(requestBody);
        }

        String responseBody = event.getResponseBody();
        if (responseBody != null) {
            sb.append(" ResponseBody = ").append(responseBody);
        }

        return sb.toString();
    }

}
