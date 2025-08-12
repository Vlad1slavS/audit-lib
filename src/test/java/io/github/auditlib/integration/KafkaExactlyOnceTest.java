package io.github.auditlib.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.auditlib.service.AuditLogServiceImpl;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Тесты для проверки exactly-once семантики в Kafka
 */
@SpringBootTest(
        classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "audit.outputs=KAFKA",
                "audit.kafka.method-topic=test-method-topic",
                "audit.kafka.http-topic=test-http-topic",
                "audit.kafka.acks=all",
                "audit.kafka.retries=100000",
                "audit.kafka.enable-idempotence=true",
                "spring.kafka.producer.properties.enable.idempotence=true",
                "spring.kafka.producer.properties.max.in.flight.requests.per.connection=5",
                "spring.kafka.producer.properties.retries=2147483647"
        }
)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class KafkaExactlyOnceTest {

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("audit.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.producer.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private AuditLogServiceImpl auditLogService;

    @Autowired
    private TestService testService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaConsumer<String, String> methodConsumer;
    private KafkaConsumer<String, String> httpConsumer;

    private final String uniqueId = UUID.randomUUID().toString().substring(0, 8);
    private final String methodGroupId = "test-method-group-" + uniqueId;
    private final String httpGroupId = "test-http-group-" + uniqueId;

    @BeforeEach
    void setUp() throws InterruptedException {

        methodConsumer = createConsumer("test-method-topic", methodGroupId);
        httpConsumer = createConsumer("test-http-topic", httpGroupId);

        Thread.sleep(2000);

        methodConsumer.poll(Duration.ofMillis(100));
        httpConsumer.poll(Duration.ofMillis(100));

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
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(topic));
        return consumer;
    }

    /**
     * Тест проверяет, что при множественных вызовах одного метода
     * с одним correlationId не происходит дублирования сообщений
     */
    @Test
    void NoDuplicateMethodEventsWithSameCorrelationId() throws Exception {

        for (int i = 0; i < 5; i++) {
            testService.processData("test-data-" + i);
            Thread.sleep(50);
        }

        List<JsonNode> methodEvents = consumeMethodMessages(10);

        assertThat(methodEvents).hasSize(10);

        Set<String> correlationIds = new HashSet<>();
        Map<String, Long> startEvents = new HashMap<>();
        Map<String, Long> endEvents = new HashMap<>();

        for (JsonNode node : methodEvents){
            correlationIds.add(node.get("correlationId").asText());
        }

        assertThat(correlationIds).hasSize(5);

        for (JsonNode event : methodEvents) {
            String eventType = event.get("eventType").asText();
            String correlationId = event.get("correlationId").asText();

            if ("START".equals(eventType)) {
                startEvents.put(correlationId, startEvents.getOrDefault(correlationId, 0L) + 1);
            } else if ("END".equals(eventType)) {
                endEvents.put(correlationId, endEvents.getOrDefault(correlationId, 0L) + 1);
            }
        }

        startEvents.values().forEach(count -> assertThat(count).isEqualTo(1));
        endEvents.values().forEach(count -> assertThat(count).isEqualTo(1));
    }


    @Test
    void IdempotentProducerConfiguration() throws Exception {

        testService.processData("idempotent-test");

        List<JsonNode> methodEvents = consumeMethodMessages(2);
        assertThat(methodEvents).hasSize(2);

        methodEvents.forEach(event -> {
            assertThat(event.get("correlationId")).isNotNull();
            assertThat(event.get("timestamp")).isNotNull();
            assertThat(event.get("methodName")).isNotNull();
            assertThat(event.get("eventType")).isNotNull();
        });
    }

    /**
     * Тест проверяет уникальность ключей для HTTP событий
     */
    @Test
    void HttpEventKeyUniqueness() throws Exception {

        Map<String, Object> httpEvent1 = createHttpEvent("Outgoing", "POST", "/api/test");
        Map<String, Object> httpEvent2 = createHttpEvent("Outgoing", "POST", "/api/test");

        auditLogService.logHttpEvent(httpEvent1);
        Thread.sleep(50);
        auditLogService.logHttpEvent(httpEvent2);

        List<JsonNode> httpEvents = consumeHttpMessages(2);

        assertThat(httpEvents).hasSize(2);

        List<String> timestamps = new ArrayList<>();

        for (JsonNode event : httpEvents) {
            timestamps.add(event.get("timestamp").asText());
        }

        assertThat(timestamps.get(0)).isNotEqualTo(timestamps.get(1));
    }


    private Map<String, Object> createHttpEvent(String direction, String method, String uri) {
        Map<String, Object> event = new HashMap<>();
        event.put("direction", direction);
        event.put("method", method);
        event.put("uri", uri);
        event.put("statusCode", 200);
        event.put("requestBody", "{}");
        event.put("responseBody", "{}");
        event.put("timestamp", LocalDateTime.now());
        return event;
    }

    private List<JsonNode> consumeMethodMessages(int expectedCount) throws Exception {
        return consumeMessages(methodConsumer, expectedCount, "method");
    }

    private List<JsonNode> consumeHttpMessages(int expectedCount) throws Exception {
        return consumeMessages(httpConsumer, expectedCount, "http");
    }

    private List<JsonNode> consumeMessages(KafkaConsumer<String, String> consumer, int expectedCount, String type) throws Exception {
        List<JsonNode> messages = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        while (messages.size() < expectedCount && (System.currentTimeMillis() - startTime) < TimeUnit.SECONDS.toMillis(60)) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

            for (ConsumerRecord<String, String> record : records) {
                try {
                    JsonNode jsonNode = objectMapper.readTree(record.value());
                    messages.add(jsonNode);
                } catch (Exception e) {
                    System.err.println("Failed to parse message: " + record.value());
                    throw e;
                }
            }

            if (messages.size() < expectedCount) {
                Thread.sleep(500);
            }
        }

        if (messages.size() < expectedCount) {
            fail("Expected " + expectedCount + " messages but got " + messages.size() + " within timeout. " +
                    "This might indicate duplicate messages were filtered out or messages were lost.");
        }

        return messages;
    }

}
