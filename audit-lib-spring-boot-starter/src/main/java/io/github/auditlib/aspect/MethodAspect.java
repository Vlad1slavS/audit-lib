package io.github.auditlib.aspect;

import io.github.auditlib.annotation.AuditLog;
import io.github.auditlib.model.MethodAuditEvent;
import io.github.auditlib.service.AuditLogServiceImpl;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
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

        MethodAuditEvent startEvent = MethodAuditEvent.createStartEvent(
                correlationId, methodName, joinPoint.getArgs(), auditLog.logLevel().name());
        auditLogService.logMethodEvent(startEvent);

        try {
            Object result = joinPoint.proceed();

            MethodAuditEvent endEvent = MethodAuditEvent.createEndEvent(
                    correlationId, methodName, result, auditLog.logLevel().name());
            auditLogService.logMethodEvent(endEvent);

            return result;

        } catch (Throwable throwable) {
            MethodAuditEvent errorEvent = MethodAuditEvent.createErrorEvent(
                    correlationId, methodName, throwable, auditLog.logLevel().name());
            auditLogService.logMethodEvent(errorEvent);

            throw throwable;
        }
    }

}
