package io.github.auditlib.integration;

import io.github.auditlib.annotation.AuditLog;
import org.springframework.stereotype.Service;

@Service
public class TestService {

    @AuditLog(logLevel = AuditLog.LogLevel.INFO)
    public String processData(String input) {
        return "Processed: " + input;
    }

    @AuditLog(logLevel = AuditLog.LogLevel.DEBUG)
    public Integer calculateSum(Integer a, Integer b) {
        return a + b;
    }

    @AuditLog(logLevel = AuditLog.LogLevel.ERROR)
    public String processWithError(String input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Input cannot be null or empty");
        }
        return "Success: " + input;
    }

    @AuditLog
    public void voidMethod() {
    }
}