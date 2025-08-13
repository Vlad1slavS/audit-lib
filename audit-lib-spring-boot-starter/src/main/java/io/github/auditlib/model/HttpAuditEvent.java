package io.github.auditlib.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class HttpAuditEvent {

    private String correlationId;

    private LocalDateTime timestamp;

    private String eventType;

    private String logLevel;

    private String direction;

    private String method;

    private String uri;

    private Integer statusCode;

    private String requestBody;

    private String responseBody;

    public static HttpAuditEvent createIncomingEvent(String method, String uri,
                                                     String requestBody, Integer statusCode,
                                                     String responseBody) {
        return HttpAuditEvent.builder()
                .direction("Incoming")
                .method(method)
                .uri(uri)
                .requestBody(requestBody.isEmpty() ? "{}" : requestBody)
                .statusCode(statusCode)
                .responseBody(responseBody.isEmpty() ? "{}" : responseBody)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static HttpAuditEvent createOutgoingEvent(String method, String uri,
                                                     String requestBody, Integer statusCode,
                                                     String responseBody) {
        return HttpAuditEvent.builder()
                .direction("Outgoing")
                .method(method)
                .uri(uri)
                .requestBody(requestBody.isEmpty() ? "{}" : requestBody)
                .statusCode(statusCode)
                .responseBody(responseBody.isEmpty() ? "{}" : responseBody)
                .timestamp(LocalDateTime.now())
                .build();
    }

}
