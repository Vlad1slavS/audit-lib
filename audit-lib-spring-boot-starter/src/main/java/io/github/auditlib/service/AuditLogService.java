package io.github.auditlib.service;

import io.github.auditlib.config.AuditProperties;
import io.github.auditlib.model.HttpAuditEvent;
import io.github.auditlib.model.MethodAuditEvent;

import java.util.Map;

public interface AuditLogService {

    Map<AuditProperties.OutputType, Boolean> logMethodEvent(MethodAuditEvent event);

    void logHttpEvent(HttpAuditEvent event);

}
