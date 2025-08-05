package io.github.auditlib.service;

import io.github.auditlib.config.AuditProperties;

import java.util.Map;

public interface AuditLogService {

    Map<AuditProperties.OutputType, Boolean> logMethodEvent(Map<String, Object> event);

    void logHttpEvent(Map<String, Object> event);

}
