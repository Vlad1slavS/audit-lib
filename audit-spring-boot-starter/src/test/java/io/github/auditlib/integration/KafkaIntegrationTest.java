package io.github.auditlib.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "audit.outputs=KAFKA",
                "audit.kafka.method-topic=test-audit-method-logs",
                "audit.kafka.http-topic=test-audit-http-logs",
                "audit.kafka.acks=all",
                "audit.kafka.retries=3"
        }
)
@Testcontainers
@AutoConfigureWebMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class KafkaIntegrationTest {

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("audit.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TestService testService;

    @Autowired
    private ObjectMapper objectMapper;

    private KafkaConsumer<String, String> methodConsumer;
    private KafkaConsumer<String, String> httpConsumer;

    private final String uniqueId = UUID.randomUUID().toString().substring(0, 8);
    private final String methodGroupId = "method-test-group-" + uniqueId;
    private final String httpGroupId = "http-test-group-" + uniqueId;

    @BeforeEach
    void setUp() throws InterruptedException {
        methodConsumer = createConsumer("test-audit-method-logs", methodGroupId);
        httpConsumer = createConsumer("test-audit-http-logs", httpGroupId);

        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    methodConsumer.poll(Duration.ofMillis(100));
                    httpConsumer.poll(Duration.ofMillis(100));
                });

    }

    @AfterEach
    void tearDown() {
        if (methodConsumer != null) {
            methodConsumer.close();
        }
        if (httpConsumer != null) {
            httpConsumer.close();
        }
    }

    private KafkaConsumer<String, String> createConsumer(String topic, String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 10000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3000);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 30000);

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(topic));
        return consumer;
    }

    @Test
    void MethodAuditLoggingToKafka() throws Exception {

        String result = testService.processData("test input");
        assertThat(result).isEqualTo("Processed: test input");

        List<JsonNode> methodEvents = consumeMethodMessages(2);

        assertThat(methodEvents).hasSize(2);

        JsonNode startEvent = null;
        JsonNode endEvent = null;

        for (JsonNode methodEvent : methodEvents) {
            if ("START".equals(methodEvent.get("eventType").asText())) {
                startEvent = methodEvent;
                break;
            }
        }

        if (startEvent == null) {
            throw new AssertionError("START event not found");
        }

        assertThat(startEvent.get("methodName").asText()).isEqualTo("TestService.processData");
        assertThat(startEvent.get("logLevel").asText()).isEqualTo("INFO");
        assertThat(startEvent.get("correlationId").asText()).isNotBlank();
        assertThat(startEvent.get("arguments")).isNotNull();
        assertThat(startEvent.get("timestamp")).isNotNull();

        for (JsonNode methodEvent : methodEvents) {
            if ("END".equals(methodEvent.get("eventType").asText())) {
                endEvent = methodEvent;
                break;
            }
        }

        if (endEvent == null) {
            throw new AssertionError("END event not found");
        }

        assertThat(endEvent.get("methodName").asText()).isEqualTo("TestService.processData");
        assertThat(endEvent.get("logLevel").asText()).isEqualTo("INFO");
        assertThat(endEvent.get("result").asText()).isEqualTo("Processed: test input");

        assertThat(startEvent.get("correlationId").asText())
                .isEqualTo(endEvent.get("correlationId").asText());
    }

    @Test
    void MethodErrorAuditLoggingToKafka() throws Exception {

        assertThrows(IllegalArgumentException.class, () -> {
            testService.processWithError("");
        });

        List<JsonNode> methodEvents = consumeMethodMessages(2);

        assertThat(methodEvents).hasSize(2);

        JsonNode errorEvent = null;

        for (JsonNode methodEvent : methodEvents) {
            if ("ERROR".equals(methodEvent.get("eventType").asText())) {
                errorEvent = methodEvent;
                break;
            }
        }

        if (errorEvent == null) {
            throw new AssertionError("ERROR event not found");
        }

        assertThat(errorEvent.get("methodName").asText()).isEqualTo("TestService.processWithError");
        assertThat(errorEvent.get("logLevel").asText()).isEqualTo("ERROR");
        assertThat(errorEvent.get("errorMessage").asText()).isEqualTo("Input cannot be null or empty");
        assertThat(errorEvent.get("correlationId").asText()).isNotBlank();

    }

    @Test
    void VoidMethodAuditLoggingToKafka() throws Exception {

        testService.voidMethod();

        List<JsonNode> methodEvents = consumeMethodMessages(2);

        assertThat(methodEvents).hasSize(2);

        JsonNode endEvent = null;
        JsonNode startEvent = null;

        for (JsonNode methodEvent : methodEvents) {
            if ("START".equals(methodEvent.get("eventType").asText())) {
                startEvent = methodEvent;
                break;
            }
        }

        for (JsonNode methodEvent : methodEvents) {
            if ("END".equals(methodEvent.get("eventType").asText())) {
                endEvent = methodEvent;
                break;
            }
        }

        if (endEvent == null || startEvent == null) {
            throw new AssertionError("END event not found");
        }

        assertThat(endEvent.get("methodName").asText()).isEqualTo("TestService.voidMethod");
        assertThat(startEvent.get("methodName").asText()).isEqualTo("TestService.voidMethod");
        assertTrue(startEvent.get("arguments").isEmpty());
        assertTrue(endEvent.get("result").isNull());
    }

    @Test
    void HttpIncomingRequestAuditLoggingToKafka() throws Exception {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>("{\"data\": \"test\"}", headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/test/process",
                request,
                String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        List<JsonNode> httpEvents = consumeHttpMessages(1);

        assertThat(httpEvents).hasSize(1);

        JsonNode httpEvent = httpEvents.get(0);
        assertThat(httpEvent.get("direction").asText()).isEqualTo("Incoming");
        assertThat(httpEvent.get("method").asText()).isEqualTo("POST");
        assertThat(httpEvent.get("uri").asText()).isEqualTo("/api/test/process");
        assertThat(httpEvent.get("statusCode").asInt()).isEqualTo(200);
        assertThat(httpEvent.get("requestBody").asText()).contains("test");
        assertThat(httpEvent.get("responseBody").asText()).contains("Processed");
        assertThat(httpEvent.get("timestamp")).isNotNull();

    }


    private List<JsonNode> consumeMethodMessages(int expectedCount) throws Exception {
        return consumeMessages(methodConsumer, expectedCount, "method");
    }

    private List<JsonNode> consumeHttpMessages(int expectedCount) throws Exception {
        return consumeMessages(httpConsumer, expectedCount, "http");
    }

    private List<JsonNode> consumeMessages(KafkaConsumer<String, String> consumer,
                                           int expectedCount,
                                           String type) throws Exception {
        List<JsonNode> messages = new ArrayList<>();

        await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

                    for (ConsumerRecord<String, String> record : records) {
                        JsonNode jsonNode = objectMapper.readTree(record.value());
                        messages.add(jsonNode);
                    }

                    assertThat(messages)
                            .withFailMessage("Expected %d messages but got %d", expectedCount, messages.size())
                            .hasSize(expectedCount);
                });

        return messages;
    }

}