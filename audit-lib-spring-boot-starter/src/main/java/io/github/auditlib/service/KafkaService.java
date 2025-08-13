package io.github.auditlib.service;

import io.github.auditlib.model.HttpAuditEvent;
import io.github.auditlib.model.MethodAuditEvent;

public interface KafkaService {

     boolean sendMethodEventToKafka(MethodAuditEvent event);

     boolean sendHttpEventToKafka(HttpAuditEvent event);

}
