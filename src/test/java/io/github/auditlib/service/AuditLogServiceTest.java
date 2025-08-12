package io.github.auditlib.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.auditlib.config.AuditProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditProperties auditProperties;

    @Mock
    private AuditProperties.Kafka kafkaProperties;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private CompletableFuture<SendResult<String, String>> future;

    private AuditLogServiceImpl auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogServiceImpl(auditProperties, objectMapper, kafkaTemplate);
    }

    /**
     * Тест для проверки успешной отправки события метода в Kafka.
     */
    @Test
    void sendMethodToKafka_LogMethodEvent() throws JsonProcessingException {

        Map<AuditProperties.OutputType, Boolean> result;

        Map<String, Object> event = createMethodEvent("test-correlation-id", "TestClass.testMethod");
        String expectedJson = "{\"correlationId\":\"" + "test-correlation-id" + "\",\"methodName\":\"" + "TestClass.testMethod" + "\"}";

        when(auditProperties.getOutputs()).thenReturn(List.of(AuditProperties.OutputType.KAFKA));
        when(auditProperties.getKafka()).thenReturn(kafkaProperties);
        when(kafkaProperties.getMethodTopic()).thenReturn("audit-method-logs");
        when(objectMapper.writeValueAsString(event)).thenReturn(expectedJson);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        when(future.whenComplete(any())).thenReturn(future);

        result = auditLogService.logMethodEvent(event);
        assertThat(result.get(AuditProperties.OutputType.KAFKA)).isTrue();
        assertThat(result.get(AuditProperties.OutputType.CONSOLE)).isNull();
        assertThat(result.get(AuditProperties.OutputType.FILE)).isNull();

        verify(objectMapper).writeValueAsString(event);
        verify(kafkaTemplate).send(eq("audit-method-logs"), eq("test-correlation-id"), eq(expectedJson));
        verify(future).whenComplete(any());
    }

    /**
     * Тест для проверки успешной отправки HTTP события в Kafka.
     */
    @Test
    void sendHttpToKafka_LogHttpEvent() throws JsonProcessingException {

        Map<String, Object> event = createHttpEvent("Incoming", "GET");
        String expectedJson = "{\"direction\":\"" + "Incoming" + "\",\"method\":\"" + "GET" + "\"}";

        when(auditProperties.getOutputs()).thenReturn(List.of(AuditProperties.OutputType.KAFKA));
        when(auditProperties.getKafka()).thenReturn(kafkaProperties);
        when(kafkaProperties.getHttpTopic()).thenReturn("audit-http-logs");
        when(objectMapper.writeValueAsString(event)).thenReturn(expectedJson);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        when(future.whenComplete(any())).thenReturn(future);

        auditLogService.logHttpEvent(event);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(objectMapper).writeValueAsString(event);
        verify(kafkaTemplate).send(eq("audit-http-logs"), keyCaptor.capture(), eq(expectedJson));

        String capturedKey = keyCaptor.getValue();
        assertThat(capturedKey).startsWith("Incoming_GET_");
        verify(future).whenComplete(any());
    }

    /**
     * Тест для проверки топика в который отправится сообщение метода
     */
    @Test
    void sendMethodTopic_UseCorrectTopicForMethodEvent() throws JsonProcessingException {

        Map<String, Object> event = createMethodEvent("test-id", "TestMethod");

        when(auditProperties.getOutputs()).thenReturn(List.of(AuditProperties.OutputType.KAFKA));
        when(auditProperties.getKafka()).thenReturn(kafkaProperties);
        when(kafkaProperties.getMethodTopic()).thenReturn("custom-method-topic");
        when(objectMapper.writeValueAsString(event)).thenReturn("{}");
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        when(future.whenComplete(any())).thenReturn(future);

        auditLogService.logMethodEvent(event);

        verify(kafkaTemplate).send(eq("custom-method-topic"), anyString(), anyString());
    }

    /**
     * Тест для проверки топика в который отправится HTTP сообщение
     */
    @Test
    void sendHttpTopic_UseCorrectTopicForHttpEvents() throws JsonProcessingException {
        Map<String, Object> event = createHttpEvent("Incoming", "PUT");

        when(auditProperties.getOutputs()).thenReturn(List.of(AuditProperties.OutputType.KAFKA));
        when(auditProperties.getKafka()).thenReturn(kafkaProperties);
        when(kafkaProperties.getHttpTopic()).thenReturn("custom-http-topic");
        when(objectMapper.writeValueAsString(event)).thenReturn("{}");
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        when(future.whenComplete(any())).thenReturn(future);

        auditLogService.logHttpEvent(event);

        verify(kafkaTemplate).send(eq("custom-http-topic"), anyString(), anyString());
    }

    private Map<String, Object> createMethodEvent(String correlationId, String methodName) {
        Map<String, Object> event = new HashMap<>();
        event.put("correlationId", correlationId);
        event.put("methodName", methodName);
        event.put("eventType", "START");
        event.put("logLevel", "DEBUG");
        event.put("timestamp", LocalDateTime.now());
        return event;
    }

    private Map<String, Object> createHttpEvent(String direction, String method) {
        Map<String, Object> event = new HashMap<>();
        event.put("direction", direction);
        event.put("method", method);
        event.put("uri", "/test/endpoint");
        event.put("statusCode", 200);
        event.put("timestamp", LocalDateTime.now());
        return event;
    }

}