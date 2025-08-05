package io.github.auditlib.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Конфигурационный класс стартера (пример audit.kafka.acks=all)
 */
@ConfigurationProperties(prefix = "audit")
@Data
public class AuditProperties {

    private List<OutputType> outputs = new ArrayList<>();
    private File file = new File();
    private Kafka kafka = new Kafka();

    @Data
    public static class File {
        private String path = "logs/audit.log";
        private String maxFileSize = "1MB";
        private int maxFiles = 10;

    }

    @Data
    public static class Kafka {

        private String bootstrapServers = "localhost:9092";
        private String methodTopic = "audit-method-logs";
        private String httpTopic = "audit-http-logs";
        private String acks = "all";
        private int retries = Integer.MAX_VALUE;

    }

    public enum OutputType {
        CONSOLE, FILE, KAFKA
    }

}
