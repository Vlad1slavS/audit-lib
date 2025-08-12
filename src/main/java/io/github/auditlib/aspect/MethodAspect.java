package io.github.auditlib.aspect;

import io.github.auditlib.annotation.AuditLog;
import io.github.auditlib.service.AuditLogServiceImpl;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Аспект для перехвата вызова и реализации доп логики для аннотированных методов
 */
@Aspect
@Component
public class MethodAspect {

    private final AuditLogServiceImpl auditLogService;

    public MethodAspect(AuditLogServiceImpl auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Around("@annotation(auditLog)")
    public Object logMethodExecution(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {

        String correlationId = UUID.randomUUID().toString();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName();

        Map<String, Object> startEvent = createStartEvent(correlationId, methodName,
                joinPoint.getArgs(), auditLog.logLevel().name());
        auditLogService.logMethodEvent(startEvent);

        try {

            Object result = joinPoint.proceed();
            Map<String, Object> endEvent = createEndEvent(correlationId, methodName,
                    result, auditLog.logLevel().name());
            auditLogService.logMethodEvent(endEvent);
            return result;

        } catch (Throwable throwable) {

            Map<String, Object> errorEvent = createErrorEvent(correlationId, methodName,
                    throwable, auditLog.logLevel().name());

            auditLogService.logMethodEvent(errorEvent);
            throw throwable;
        }
    }

    private Map<String, Object> createStartEvent(String correlationId, String methodName,
                                                 Object[] args, String logLevel) {
        Map<String, Object> event = new HashMap<>();
        event.put("correlationId", correlationId);
        event.put("methodName", methodName);
        event.put("eventType", "START");
        event.put("logLevel", logLevel);
        event.put("arguments", args);
        event.put("timestamp", LocalDateTime.now());
        return event;

    }

    private Map<String, Object> createEndEvent(String correlationId, String methodName,
                                               Object result, String logLevel) {
        Map<String, Object> event = new HashMap<>();
        event.put("correlationId", correlationId);
        event.put("methodName", methodName);
        event.put("eventType", "END");
        event.put("logLevel", logLevel);
        event.put("result", result);
        event.put("timestamp", LocalDateTime.now());
        return event;

    }

    private Map<String, Object> createErrorEvent(String correlationId, String methodName,
                                                 Throwable throwable, String logLevel) {
        Map<String, Object> event = new HashMap<>();
        event.put("correlationId", correlationId);
        event.put("methodName", methodName);
        event.put("eventType", "ERROR");
        event.put("logLevel", logLevel);
        event.put("errorMessage", throwable.getMessage());
        event.put("timestamp", LocalDateTime.now());
        return event;

    }

}
