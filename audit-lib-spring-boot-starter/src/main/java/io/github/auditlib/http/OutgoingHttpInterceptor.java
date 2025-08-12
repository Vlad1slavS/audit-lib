package io.github.auditlib.http;

import io.github.auditlib.model.HttpAuditEvent;
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

        String requestBodyStr = requestBody.length > 0 ?
                new String(requestBody, StandardCharsets.UTF_8) : "";
        String responseBody = getResponseBody(response);

        HttpAuditEvent event = HttpAuditEvent.createOutgoingEvent(
                request.getMethod().name(),
                request.getURI().toString(),
                requestBodyStr,
                response.getStatusCode().value(),
                responseBody
        );

        auditLogService.logHttpEvent(event);
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
