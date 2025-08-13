package io.github.auditlib.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MethodAuditEvent {

    private String correlationId;

    private LocalDateTime timestamp;

    private String eventType;

    private String logLevel;

    private String methodName;

    private Object[] arguments;

    private Object result;

    private String errorMessage;

    public static MethodAuditEvent createStartEvent(String correlationId, String methodName,
                                                    Object[] args, String logLevel) {
        return MethodAuditEvent.builder()
                .correlationId(correlationId)
                .methodName(methodName)
                .eventType("START")
                .logLevel(logLevel)
                .arguments(args)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static MethodAuditEvent createEndEvent(String correlationId, String methodName,
                                                  Object result, String logLevel) {
        return MethodAuditEvent.builder()
                .correlationId(correlationId)
                .methodName(methodName)
                .eventType("END")
                .logLevel(logLevel)
                .result(result)
                .timestamp(LocalDateTime.now())
                .build();

    }

    public static MethodAuditEvent createErrorEvent(String correlationId, String methodName,
                                                    Throwable throwable, String logLevel) {
        return MethodAuditEvent.builder()
                .correlationId(correlationId)
                .methodName(methodName)
                .eventType("ERROR")
                .logLevel(logLevel)
                .errorMessage(throwable.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
    }

}
