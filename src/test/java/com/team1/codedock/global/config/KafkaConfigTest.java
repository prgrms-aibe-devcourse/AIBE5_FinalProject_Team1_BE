package com.team1.codedock.global.config;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConfigTest {

    private final ApplicationContextRunner kafkaContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class));

    @Test
    @DisplayName("Spring Kafka 자동 설정은 실제 broker 연결 없이 공통 Kafka 빈을 생성함")
    void kafkaAutoConfigurationCreatesCommonBeansWithoutRunningBroker() {
        kafkaContextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(KafkaProperties.class);
            assertThat(context).hasSingleBean(KafkaTemplate.class);
            assertThat(context).hasSingleBean(ProducerFactory.class);
            assertThat(context).hasSingleBean(ConsumerFactory.class);
            assertThat(context).hasSingleBean(KafkaAdmin.class);
            assertThat(context).hasBean("kafkaListenerContainerFactory");
        });
    }

    @Test
    @DisplayName("Kafka Producer 설정은 환경변수 기반 프로퍼티를 반영함")
    void kafkaProducerFactoryBindsExternalizedProperties() {
        kafkaContextRunner
                .withPropertyValues(
                        "spring.kafka.bootstrap-servers=kafka:9092",
                        "spring.kafka.client-id=codedock-test-client",
                        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
                        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
                        "spring.kafka.producer.acks=all",
                        "spring.kafka.producer.retries=5",
                        "spring.kafka.producer.properties.enable.idempotence=true",
                        "spring.kafka.producer.properties.max.in.flight.requests.per.connection=5"
                )
                .run(context -> {
                    DefaultKafkaProducerFactory<?, ?> producerFactory =
                            (DefaultKafkaProducerFactory<?, ?>) context.getBean(ProducerFactory.class);

                    Map<String, Object> configs = producerFactory.getConfigurationProperties();

                    assertThat(asStringList(configs.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)))
                            .containsExactly("kafka:9092");
                    assertConfigValue(configs, CommonClientConfigs.CLIENT_ID_CONFIG, "codedock-test-client");
                    assertConfigClass(configs, ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
                    assertConfigClass(configs, ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
                    assertConfigValue(configs, ProducerConfig.ACKS_CONFIG, "all");
                    assertConfigValue(configs, ProducerConfig.RETRIES_CONFIG, "5");
                    assertConfigValue(configs, ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
                    assertConfigValue(configs, ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");
                });
    }

    @Test
    @DisplayName("Kafka Consumer 설정은 환경변수 기반 프로퍼티를 반영함")
    void kafkaConsumerFactoryBindsExternalizedProperties() {
        kafkaContextRunner
                .withPropertyValues(
                        "spring.kafka.bootstrap-servers=kafka:9092",
                        "spring.kafka.client-id=codedock-test-client",
                        "spring.kafka.consumer.group-id=codedock-test-group",
                        "spring.kafka.consumer.auto-offset-reset=latest",
                        "spring.kafka.consumer.enable-auto-commit=false",
                        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer"
                )
                .run(context -> {
                    DefaultKafkaConsumerFactory<?, ?> consumerFactory =
                            (DefaultKafkaConsumerFactory<?, ?>) context.getBean(ConsumerFactory.class);

                    Map<String, Object> configs = consumerFactory.getConfigurationProperties();

                    assertThat(asStringList(configs.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG)))
                            .containsExactly("kafka:9092");
                    assertConfigValue(configs, CommonClientConfigs.CLIENT_ID_CONFIG, "codedock-test-client");
                    assertConfigValue(configs, ConsumerConfig.GROUP_ID_CONFIG, "codedock-test-group");
                    assertConfigValue(configs, ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
                    assertConfigValue(configs, ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
                    assertConfigClass(configs, ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
                    assertConfigClass(configs, ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
                });
    }

    @Test
    @DisplayName("Kafka Listener 설정은 record 단위 ack와 topic 미존재 허용 정책을 반영함")
    void kafkaListenerFactoryBindsAckAndMissingTopicPolicy() {
        kafkaContextRunner
                .withPropertyValues(
                        "spring.kafka.listener.ack-mode=record",
                        "spring.kafka.listener.missing-topics-fatal=false"
                )
                .run(context -> {
                    KafkaProperties kafkaProperties = context.getBean(KafkaProperties.class);
                    ConcurrentKafkaListenerContainerFactory<?, ?> listenerFactory =
                            context.getBean("kafkaListenerContainerFactory", ConcurrentKafkaListenerContainerFactory.class);

                    assertThat(kafkaProperties.getListener().getAckMode())
                            .isEqualTo(ContainerProperties.AckMode.RECORD);
                    assertThat(kafkaProperties.getListener().isMissingTopicsFatal()).isFalse();
                    assertThat(listenerFactory.getContainerProperties().getAckMode())
                            .isEqualTo(ContainerProperties.AckMode.RECORD);
                });
    }

    @Test
    @DisplayName("Kafka Admin 설정도 같은 bootstrap servers를 사용함")
    void kafkaAdminUsesSameBootstrapServers() {
        kafkaContextRunner
                .withPropertyValues("spring.kafka.bootstrap-servers=kafka:9092")
                .run(context -> {
                    KafkaAdmin kafkaAdmin = context.getBean(KafkaAdmin.class);

                    assertThat(asStringList(kafkaAdmin.getConfigurationProperties()
                            .get(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG)))
                            .containsExactly("kafka:9092");
                });
    }

    @Test
    @DisplayName("Kafka bootstrap servers는 여러 broker 주소도 보존함")
    void kafkaBootstrapServersSupportMultipleBrokers() {
        kafkaContextRunner
                .withPropertyValues("spring.kafka.bootstrap-servers=kafka-1:9092,kafka-2:9092")
                .run(context -> {
                    DefaultKafkaProducerFactory<?, ?> producerFactory =
                            (DefaultKafkaProducerFactory<?, ?>) context.getBean(ProducerFactory.class);
                    DefaultKafkaConsumerFactory<?, ?> consumerFactory =
                            (DefaultKafkaConsumerFactory<?, ?>) context.getBean(ConsumerFactory.class);

                    assertThat(asStringList(producerFactory.getConfigurationProperties()
                            .get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)))
                            .containsExactly("kafka-1:9092", "kafka-2:9092");
                    assertThat(asStringList(consumerFactory.getConfigurationProperties()
                            .get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG)))
                            .containsExactly("kafka-1:9092", "kafka-2:9092");
                });
    }

    @Test
    @DisplayName("application.properties는 Kafka 설정을 환경변수 placeholder로 노출함")
    void applicationPropertiesExposeKafkaEnvironmentPlaceholders() throws IOException {
        Properties properties = loadProperties("src/main/resources/application.properties");

        assertThat(properties)
                .containsEntry("spring.kafka.bootstrap-servers", "${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}")
                .containsEntry("spring.kafka.client-id", "${KAFKA_CLIENT_ID:codedock-backend}")
                .containsEntry("spring.kafka.consumer.group-id", "${KAFKA_CONSUMER_GROUP_ID:codedock}")
                .containsEntry("spring.kafka.consumer.auto-offset-reset", "${KAFKA_CONSUMER_AUTO_OFFSET_RESET:earliest}")
                .containsEntry("spring.kafka.consumer.enable-auto-commit", "${KAFKA_CONSUMER_ENABLE_AUTO_COMMIT:false}")
                .containsEntry("spring.kafka.producer.acks", "${KAFKA_PRODUCER_ACKS:all}")
                .containsEntry("spring.kafka.producer.retries", "${KAFKA_PRODUCER_RETRIES:3}")
                .containsEntry("spring.kafka.producer.properties.enable.idempotence",
                        "${KAFKA_PRODUCER_ENABLE_IDEMPOTENCE:true}")
                .containsEntry("spring.kafka.listener.missing-topics-fatal",
                        "${KAFKA_LISTENER_MISSING_TOPICS_FATAL:false}");
    }

    @Test
    @DisplayName("docker-compose.yml은 app 컨테이너에 Kafka 환경변수를 전달함")
    void dockerComposePassesKafkaEnvironmentVariablesToAppContainer() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(compose)
                .contains("KAFKA_BOOTSTRAP_SERVERS: ${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}")
                .contains("KAFKA_CLIENT_ID: ${KAFKA_CLIENT_ID:-codedock-backend}")
                .contains("KAFKA_CONSUMER_GROUP_ID: ${KAFKA_CONSUMER_GROUP_ID:-codedock}")
                .contains("KAFKA_CONSUMER_ENABLE_AUTO_COMMIT: ${KAFKA_CONSUMER_ENABLE_AUTO_COMMIT:-false}")
                .contains("KAFKA_PRODUCER_ACKS: ${KAFKA_PRODUCER_ACKS:-all}")
                .contains("KAFKA_LISTENER_MISSING_TOPICS_FATAL: ${KAFKA_LISTENER_MISSING_TOPICS_FATAL:-false}");
    }

    @Test
    @DisplayName("docker-compose.yml은 Kafka broker healthcheck를 유지함")
    void dockerComposeKeepsKafkaHealthcheck() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(compose)
                .contains("kafka-broker-api-versions.sh --bootstrap-server localhost:9092")
                .contains("condition: service_healthy");
    }

    @Test
    @DisplayName("docker-compose.yml은 app 내부 Kafka 주소와 host 점검 주소를 분리함")
    void dockerComposeSeparatesInternalKafkaAddressFromHostAccessAddress() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(compose)
                .contains("KAFKA_BOOTSTRAP_SERVERS: ${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}")
                .contains("- \"${KAFKA_HOST_PORT:-9092}:29092\"")
                .contains("KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,EXTERNAL://localhost:${KAFKA_HOST_PORT:-9092}");
    }

    @Test
    @DisplayName("Kafka 가이드는 외부 listener와 compose 설정 출력 주의사항을 설명함")
    void kafkaGuideDocumentsExternalListenerAndComposeConfigSecretCaution() throws IOException {
        String guide = Files.readString(Path.of("docs/kafka-setup-guide.md"));

        assertThat(guide)
                .contains("외부 listener 주의사항")
                .contains("보안그룹에서 Kafka 포트 `9092`를 인터넷에 열지 않는다")
                .contains("KAFKA_ADVERTISED_LISTENERS")
                .contains("docker compose config")
                .contains("민감 정보가 그대로 포함될 수 있다")
                .contains("마스킹");
    }

    private Properties loadProperties(String path) throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(Path.of(path))) {
            properties.load(inputStream);
        }
        return properties;
    }

    private List<String> asStringList(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(String::valueOf)
                    .toList();
        }
        return List.of(String.valueOf(value));
    }

    private void assertConfigClass(Map<String, Object> configs, String key, Class<?> expectedClass) {
        Object actual = configs.get(key);
        if (actual instanceof Class<?> actualClass) {
            assertThat(actualClass).isEqualTo(expectedClass);
            return;
        }
        assertThat(String.valueOf(actual)).isEqualTo(expectedClass.getName());
    }

    private void assertConfigValue(Map<String, Object> configs, String key, String expectedValue) {
        assertThat(String.valueOf(configs.get(key))).isEqualTo(expectedValue);
    }
}
