package io.github.auditlib.http;

import io.github.auditlib.service.AuditLogServiceImpl;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Интерцептор для логирования исходящих HTTP запросов
 */
@Component
public class OutgoingHttpInterceptor implements ClientHttpRequestInterceptor {

    private final AuditLogServiceImpl auditLogService;

    public OutgoingHttpInterceptor(AuditLogServiceImpl auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {

        ClientHttpResponse response = execution.execute(request, body);

        logOutgoingRequest(request, body, response);

        return response;

    }

    private void logOutgoingRequest(HttpRequest request, byte[] requestBody,
                                    ClientHttpResponse response) throws IOException {

        Map<String, Object> httpEvent = new HashMap<>();

        httpEvent.put("timestamp", LocalDateTime.now());
        httpEvent.put("direction", "Outgoing");
        httpEvent.put("method", request.getMethod().name());
        httpEvent.put("statusCode", response.getStatusCode().value());
        httpEvent.put("uri", request.getURI().toString());

        String requestBodyStr = requestBody.length > 0 ? new String(requestBody, StandardCharsets.UTF_8) : "{}";
        httpEvent.put("requestBody", requestBodyStr);

        String responseBody = getResponseBody(response);
        httpEvent.put("responseBody", responseBody.isEmpty() ? "{}" : responseBody);

        auditLogService.logHttpEvent(httpEvent);
    }

    private String getResponseBody(ClientHttpResponse response) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "";
        }
    }

}
