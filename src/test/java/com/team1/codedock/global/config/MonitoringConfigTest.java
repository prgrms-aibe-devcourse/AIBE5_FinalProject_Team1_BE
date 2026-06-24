package com.team1.codedock.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringConfigTest {

    @Test
    @DisplayName("application.properties는 Prometheus actuator endpoint와 registry 플래그를 노출함")
    void applicationPropertiesExposePrometheusEndpointAndRegistryFlag() throws IOException {
        Properties properties = loadProperties("src/main/resources/application.properties");

        assertThat(properties)
                .containsEntry("management.endpoints.web.exposure.include", "health,info,prometheus")
                .containsEntry("management.prometheus.metrics.export.enabled",
                        "${MANAGEMENT_PROMETHEUS_ENABLED:true}");
    }

    @Test
    @DisplayName("build.gradle은 Prometheus registry 의존성을 유지함")
    void buildGradleKeepsPrometheusRegistryDependency() throws IOException {
        String buildGradle = Files.readString(Path.of("build.gradle"));

        assertThat(buildGradle)
                .contains("spring-boot-starter-actuator")
                .contains("io.micrometer:micrometer-registry-prometheus");
    }

    @Test
    @DisplayName(".env.example은 모니터링 기본 환경변수를 제공함")
    void envExampleProvidesMonitoringDefaults() throws IOException {
        Properties properties = loadProperties(".env.example");

        assertThat(properties)
                .containsEntry("MANAGEMENT_PROMETHEUS_ENABLED", "true")
                .containsEntry("GRAFANA_HOST_PORT", "3000")
                .containsEntry("GRAFANA_ADMIN_USER", "admin")
                .containsEntry("GRAFANA_ADMIN_PASSWORD", "admin")
                .containsEntry("PROMETHEUS_HOST_PORT", "9090")
                .containsEntry("LOKI_HOST_PORT", "3100")
                .containsEntry("PROMTAIL_LOG_LEVEL", "info");
    }

    @Test
    @DisplayName("docker-compose.yml은 모니터링 서비스와 저장소 볼륨을 포함함")
    void dockerComposeIncludesMonitoringServicesAndVolumes() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(compose)
                .contains("  redis-exporter:")
                .contains("  kafka-exporter:")
                .contains("  prometheus:")
                .contains("  grafana:")
                .contains("  loki:")
                .contains("  promtail:")
                .contains("  prometheus-data:")
                .contains("  grafana-data:")
                .contains("  loki-data:");
    }

    @Test
    @DisplayName("docker-compose.yml은 Prometheus와 Loki를 host loopback에만 노출함")
    void dockerComposeBindsPrometheusAndLokiToLoopbackOnly() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(compose)
                .contains("\"127.0.0.1:${PROMETHEUS_HOST_PORT:-9090}:9090\"")
                .contains("\"127.0.0.1:${LOKI_HOST_PORT:-3100}:3100\"")
                .contains("\"${GRAFANA_HOST_PORT:-3000}:3000\"")
                .doesNotContain("\"${PROMETHEUS_HOST_PORT:-9090}:9090\"")
                .doesNotContain("\"${LOKI_HOST_PORT:-3100}:3100\"");
    }

    @Test
    @DisplayName("docker-compose.yml은 backend Prometheus 지표와 exporter 의존성을 연결함")
    void dockerComposeConnectsBackendMetricsAndExporters() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(compose)
                .contains("MANAGEMENT_PROMETHEUS_ENABLED: ${MANAGEMENT_PROMETHEUS_ENABLED:-true}")
                .contains("image: oliver006/redis_exporter:v1.62.0")
                .contains("REDIS_ADDR: redis://redis:6379")
                .contains("REDIS_PASSWORD: ${REDIS_PASSWORD:-}")
                .contains("image: danielqsj/kafka-exporter:v1.8.0")
                .contains("--kafka.server=kafka:9092")
                .contains("./monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro");
    }

    @Test
    @DisplayName("docker-compose.yml은 Promtail이 Docker 로그를 읽고 Loki 이후에 시작되도록 구성함")
    void dockerComposeConfiguresPromtailDockerLogCollection() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(compose)
                .contains("image: grafana/promtail:3.2.1")
                .contains("- -log.level=${PROMTAIL_LOG_LEVEL:-info}")
                .contains("./monitoring/promtail/promtail-config.yml:/etc/promtail/promtail-config.yml:ro")
                .contains("/var/run/docker.sock:/var/run/docker.sock:ro")
                .contains("      loki:\n        condition: service_started");
    }

    @Test
    @DisplayName("Prometheus 설정은 backend, Redis, Kafka target을 scrape함")
    void prometheusConfigScrapesBackendRedisAndKafka() throws IOException {
        String prometheus = Files.readString(Path.of("monitoring/prometheus/prometheus.yml"));

        assertThat(prometheus)
                .contains("scrape_interval: 15s")
                .contains("job_name: codedock-backend")
                .contains("metrics_path: /actuator/prometheus")
                .contains("app:8080")
                .contains("redis-exporter:9121")
                .contains("kafka-exporter:9308")
                .contains("service: codedock-backend")
                .contains("service: codedock-redis")
                .contains("service: codedock-kafka");
    }

    @Test
    @DisplayName("Grafana datasource provisioning은 Prometheus와 Loki를 자동 등록함")
    void grafanaDatasourceProvisioningRegistersPrometheusAndLoki() throws IOException {
        String datasources = Files.readString(Path.of(
                "monitoring/grafana/provisioning/datasources/datasources.yml"));

        assertThat(datasources)
                .contains("uid: Prometheus")
                .contains("type: prometheus")
                .contains("url: http://prometheus:9090")
                .contains("isDefault: true")
                .contains("uid: Loki")
                .contains("type: loki")
                .contains("url: http://loki:3100");
    }

    @Test
    @DisplayName("Grafana dashboard provisioning은 repo dashboard 경로를 사용함")
    void grafanaDashboardProvisioningUsesRepositoryDashboardPath() throws IOException {
        String dashboards = Files.readString(Path.of(
                "monitoring/grafana/provisioning/dashboards/dashboards.yml"));
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(dashboards)
                .contains("path: /var/lib/grafana/dashboards")
                .contains("editable: true");
        assertThat(compose)
                .contains("GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH: /var/lib/grafana/dashboards/codedock-overview.json")
                .contains("./monitoring/grafana/dashboards:/var/lib/grafana/dashboards:ro");
    }

    @Test
    @DisplayName("기본 Grafana dashboard는 backend 지표와 Loki 로그 패널을 포함함")
    void grafanaDashboardIncludesBackendMetricsAndLokiLogPanel() throws IOException {
        String dashboard = Files.readString(Path.of("monitoring/grafana/dashboards/codedock-overview.json"));

        assertThat(dashboard)
                .contains("\"uid\": \"Prometheus\"")
                .contains("\"uid\": \"Loki\"")
                .contains("up{job=\\\"codedock-backend\\\"}")
                .contains("up{job=\\\"redis\\\"}")
                .contains("up{job=\\\"kafka\\\"}")
                .contains("sum by (method, status) (rate(http_server_requests_seconds_count{job=\\\"codedock-backend\\\"}[5m]))")
                .contains("{service=\\\"app\\\"}");
    }

    @Test
    @DisplayName("Promtail 설정은 Docker container label을 Loki label로 전달함")
    void promtailConfigMapsDockerContainerLabelsToLokiLabels() throws IOException {
        String promtail = Files.readString(Path.of("monitoring/promtail/promtail-config.yml"));

        assertThat(promtail)
                .contains("url: http://loki:3100/loki/api/v1/push")
                .contains("docker_sd_configs")
                .contains("host: unix:///var/run/docker.sock")
                .contains("target_label: container")
                .contains("target_label: service")
                .contains("target_label: compose_project")
                .contains("target_label: stream")
                .contains("- docker: {}");
    }

    @Test
    @DisplayName("Loki 설정은 단일 EC2 Compose에 맞는 filesystem 저장소와 retention을 사용함")
    void lokiConfigUsesFilesystemStorageAndRetention() throws IOException {
        String loki = Files.readString(Path.of("monitoring/loki/loki-config.yml"));

        assertThat(loki)
                .contains("auth_enabled: false")
                .contains("http_listen_port: 3100")
                .contains("chunks_directory: /loki/chunks")
                .contains("rules_directory: /loki/rules")
                .contains("schema: v13")
                .contains("retention_period: 168h")
                .contains("retention_enabled: true");
    }

    @Test
    @DisplayName("모니터링 가이드는 EC2 포트 정책과 secret 공유 금지를 설명함")
    void monitoringGuideDocumentsEc2PortsAndSecretCaution() throws IOException {
        String guide = Files.readString(Path.of("docs/monitoring-guide.md"));

        assertThat(guide)
                .contains("Grafana")
                .contains("Prometheus")
                .contains("Loki")
                .contains("`3000`")
                .contains("`9090`")
                .contains("`3100`")
                .contains("`9121`")
                .contains("`9308`")
                .contains("`6379`")
                .contains("`9092`")
                .contains(".env")
                .contains("docker compose config")
                .contains("secret")
                .contains("SSH");
    }

    private Properties loadProperties(String path) throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(Path.of(path))) {
            properties.load(inputStream);
        }
        return properties;
    }
}
