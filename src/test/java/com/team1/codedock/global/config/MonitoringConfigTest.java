package com.team1.codedock.global.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringConfigTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static Map<String, Object> dockerCompose;
    private static Map<String, Object> services;

    @BeforeAll
    static void loadComposeYaml() throws IOException {
        dockerCompose = loadYaml("docker-compose.yml");
        services = map(dockerCompose.get("services"));
    }

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
    @DisplayName(".env.example은 exporter 외부 노출 포트를 제공하지 않음")
    void envExampleDoesNotExposeExporterHostPorts() throws IOException {
        String envExample = Files.readString(Path.of(".env.example"));

        assertThat(envExample)
                .doesNotContain("REDIS_EXPORTER_HOST_PORT")
                .doesNotContain("KAFKA_EXPORTER_HOST_PORT")
                .doesNotContain("PROMTAIL_HOST_PORT");
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
    @DisplayName("docker-compose.yml은 YAML 구조상 모니터링 서비스 6개를 정확히 등록함")
    void dockerComposeYamlRegistersExpectedMonitoringServices() {
        assertThat(services.keySet())
                .contains(
                        "prometheus",
                        "grafana",
                        "loki",
                        "promtail",
                        "redis-exporter",
                        "kafka-exporter"
                );

        assertThat(service("prometheus").get("image")).isEqualTo("prom/prometheus:v2.55.1");
        assertThat(service("grafana").get("image")).isEqualTo("grafana/grafana:11.2.2");
        assertThat(service("loki").get("image")).isEqualTo("grafana/loki:3.2.1");
        assertThat(service("promtail").get("image")).isEqualTo("grafana/promtail:3.2.1");
        assertThat(service("redis-exporter").get("image")).isEqualTo("oliver006/redis_exporter:v1.62.0");
        assertThat(service("kafka-exporter").get("image")).isEqualTo("danielqsj/kafka-exporter:v1.8.0");
    }

    @Test
    @DisplayName("docker-compose.yml은 YAML 구조상 모니터링 볼륨을 정확히 등록함")
    void dockerComposeYamlRegistersExpectedMonitoringVolumes() {
        Map<String, Object> volumes = map(dockerCompose.get("volumes"));

        assertThat(volumes.keySet())
                .contains("prometheus-data", "grafana-data", "loki-data")
                .contains("redis-data", "kafka-data");
    }

    @Test
    @DisplayName("docker-compose.yml에 참조된 모니터링 설정 파일은 repo에 실제로 존재함")
    void dockerComposeReferencedMonitoringFilesExistAndAreNonEmpty() throws IOException {
        List<Path> configFiles = List.of(
                Path.of("monitoring/prometheus/prometheus.yml"),
                Path.of("monitoring/loki/loki-config.yml"),
                Path.of("monitoring/promtail/promtail-config.yml"),
                Path.of("monitoring/grafana/provisioning/datasources/datasources.yml"),
                Path.of("monitoring/grafana/provisioning/dashboards/dashboards.yml"),
                Path.of("monitoring/grafana/dashboards/codedock-overview.json")
        );

        assertThat(configFiles)
                .allSatisfy(path -> {
                    assertThat(path).exists().isRegularFile();
                    assertThat(Files.size(path)).isPositive();
                });
    }

    @Test
    @DisplayName("docker-compose.yml은 모니터링 컨테이너 이미지를 명시 버전으로 고정함")
    void dockerComposePinsMonitoringImagesToExplicitVersions() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(compose)
                .contains("image: oliver006/redis_exporter:v1.62.0")
                .contains("image: danielqsj/kafka-exporter:v1.8.0")
                .contains("image: prom/prometheus:v2.55.1")
                .contains("image: grafana/loki:3.2.1")
                .contains("image: grafana/promtail:3.2.1")
                .contains("image: grafana/grafana:11.2.2")
                .doesNotContain("image: prom/prometheus:latest")
                .doesNotContain("image: grafana/grafana:latest")
                .doesNotContain("image: grafana/loki:latest")
                .doesNotContain("image: grafana/promtail:latest");
    }

    @Test
    @DisplayName("docker-compose.yml은 YAML 구조상 모니터링 서비스 이미지에 latest 태그를 사용하지 않음")
    void dockerComposeYamlDoesNotUseLatestImageTagForMonitoringServices() {
        List<String> monitoringServices = List.of(
                "prometheus",
                "grafana",
                "loki",
                "promtail",
                "redis-exporter",
                "kafka-exporter"
        );

        assertThat(monitoringServices)
                .allSatisfy(serviceName -> assertThat(String.valueOf(service(serviceName).get("image")))
                        .contains(":")
                        .doesNotEndWith(":latest"));
    }

    @Test
    @DisplayName("docker-compose.yml은 모든 장기 실행 서비스에 재시작 정책을 둠")
    void dockerComposeKeepsRestartPolicyForLongRunningServices() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(countOccurrences(compose, "restart: unless-stopped")).isGreaterThanOrEqualTo(9);
    }

    @Test
    @DisplayName("docker-compose.yml은 YAML 구조상 모든 모니터링 서비스에 재시작 정책을 둠")
    void dockerComposeYamlKeepsRestartPolicyForMonitoringServices() {
        assertThat(List.of("prometheus", "grafana", "loki", "promtail", "redis-exporter", "kafka-exporter"))
                .allSatisfy(serviceName -> assertThat(service(serviceName).get("restart"))
                        .isEqualTo("unless-stopped"));
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
    @DisplayName("docker-compose.yml은 YAML 구조상 Prometheus/Loki만 loopback에 묶고 exporter는 publish하지 않음")
    void dockerComposeYamlKeepsInternalMonitoringPortsPrivate() {
        assertThat(stringList(service("prometheus").get("ports")))
                .containsExactly("127.0.0.1:${PROMETHEUS_HOST_PORT:-9090}:9090");
        assertThat(stringList(service("loki").get("ports")))
                .containsExactly("127.0.0.1:${LOKI_HOST_PORT:-3100}:3100");
        assertThat(stringList(service("grafana").get("ports")))
                .containsExactly("${GRAFANA_HOST_PORT:-3000}:3000");

        assertThat(service("redis-exporter")).doesNotContainKey("ports");
        assertThat(service("kafka-exporter")).doesNotContainKey("ports");
        assertThat(service("promtail")).doesNotContainKey("ports");
        assertThat(stringList(service("redis-exporter").get("expose"))).containsExactly("9121");
        assertThat(stringList(service("kafka-exporter").get("expose"))).containsExactly("9308");
    }

    @Test
    @DisplayName("docker-compose.yml은 exporter 포트를 host에 직접 노출하지 않음")
    void dockerComposeDoesNotPublishExporterPortsToHost() throws IOException {
        String compose = normalizeLineEndings(Files.readString(Path.of("docker-compose.yml")));

        assertThat(compose)
                .contains("  redis-exporter:\n")
                .contains("    expose:\n      - \"9121\"")
                .contains("  kafka-exporter:\n")
                .contains("    expose:\n      - \"9308\"")
                .doesNotContain(":9121:9121")
                .doesNotContain(":9308:9308")
                .doesNotContain("${REDIS_EXPORTER_HOST_PORT")
                .doesNotContain("${KAFKA_EXPORTER_HOST_PORT");
    }

    @Test
    @DisplayName("docker-compose.yml은 모니터링 설정 파일을 read-only로 마운트함")
    void dockerComposeMountsMonitoringConfigFilesAsReadOnly() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(compose)
                .contains("./monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro")
                .contains("./monitoring/loki/loki-config.yml:/etc/loki/loki-config.yml:ro")
                .contains("./monitoring/promtail/promtail-config.yml:/etc/promtail/promtail-config.yml:ro")
                .contains("./monitoring/grafana/provisioning:/etc/grafana/provisioning:ro")
                .contains("./monitoring/grafana/dashboards:/var/lib/grafana/dashboards:ro");
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
    @DisplayName("docker-compose.yml은 Prometheus를 backend healthcheck 이후에 시작함")
    void dockerComposeStartsPrometheusAfterBackendHealthcheck() throws IOException {
        String compose = normalizeLineEndings(Files.readString(Path.of("docker-compose.yml")));

        assertThat(compose)
                .contains("  prometheus:\n")
                .contains("      app:\n        condition: service_healthy")
                .contains("      redis-exporter:\n        condition: service_started")
                .contains("      kafka-exporter:\n        condition: service_started");
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
    @DisplayName("docker-compose.yml은 Grafana 관리자 계정과 회원가입 차단을 환경변수로 구성함")
    void dockerComposeConfiguresGrafanaAdminAndDisablesSignup() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(compose)
                .contains("GF_SECURITY_ADMIN_USER: ${GRAFANA_ADMIN_USER:-admin}")
                .contains("GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}")
                .contains("GF_USERS_ALLOW_SIGN_UP: \"false\"")
                .contains("grafana-data:/var/lib/grafana");
    }

    @Test
    @DisplayName("docker-compose.yml은 YAML 구조상 Grafana signup 차단과 관리자 계정 환경변수를 유지함")
    void dockerComposeYamlConfiguresGrafanaSecurityEnvironment() {
        Map<String, Object> environment = map(service("grafana").get("environment"));

        assertThat(environment)
                .containsEntry("GF_SECURITY_ADMIN_USER", "${GRAFANA_ADMIN_USER:-admin}")
                .containsEntry("GF_SECURITY_ADMIN_PASSWORD", "${GRAFANA_ADMIN_PASSWORD:-admin}")
                .containsEntry("GF_USERS_ALLOW_SIGN_UP", "false")
                .containsEntry("GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH",
                        "/var/lib/grafana/dashboards/codedock-overview.json");
    }

    @Test
    @DisplayName("docker-compose.yml은 Prometheus와 Loki 저장소를 named volume에 분리함")
    void dockerComposeKeepsMonitoringDataInNamedVolumes() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(compose)
                .contains("prometheus-data:/prometheus")
                .contains("loki-data:/loki")
                .contains("grafana-data:/var/lib/grafana")
                .doesNotContain("./prometheus-data:/prometheus")
                .doesNotContain("./loki-data:/loki")
                .doesNotContain("./grafana-data:/var/lib/grafana");
    }

    @Test
    @DisplayName("docker-compose.yml은 YAML 구조상 설정 파일은 read-only, 데이터는 named volume으로 분리함")
    void dockerComposeYamlSeparatesReadOnlyConfigFromWritableDataVolumes() {
        assertThat(stringList(service("prometheus").get("volumes")))
                .contains(
                        "./monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro",
                        "prometheus-data:/prometheus"
                );
        assertThat(stringList(service("loki").get("volumes")))
                .contains(
                        "./monitoring/loki/loki-config.yml:/etc/loki/loki-config.yml:ro",
                        "loki-data:/loki"
                );
        assertThat(stringList(service("grafana").get("volumes")))
                .contains(
                        "grafana-data:/var/lib/grafana",
                        "./monitoring/grafana/provisioning:/etc/grafana/provisioning:ro",
                        "./monitoring/grafana/dashboards:/var/lib/grafana/dashboards:ro"
                );
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
    @DisplayName("Prometheus 설정은 scrape job 이름과 target label을 분리해 장애 지점을 구분함")
    void prometheusConfigSeparatesJobsAndServiceLabels() throws IOException {
        String prometheus = Files.readString(Path.of("monitoring/prometheus/prometheus.yml"));

        assertThat(prometheus)
                .contains("job_name: prometheus")
                .contains("job_name: codedock-backend")
                .contains("job_name: redis")
                .contains("job_name: kafka")
                .contains("service: codedock-backend")
                .contains("service: codedock-redis")
                .contains("service: codedock-kafka")
                .doesNotContain("host.docker.internal");
    }

    @Test
    @DisplayName("Prometheus 설정은 scrape job을 4개로 제한해 의도하지 않은 대상 수집을 막음")
    void prometheusConfigKeepsExpectedScrapeJobCount() throws IOException {
        String prometheus = Files.readString(Path.of("monitoring/prometheus/prometheus.yml"));

        assertThat(countOccurrences(prometheus, "job_name:")).isEqualTo(4);
        assertThat(prometheus)
                .doesNotContain("localhost:8080")
                .doesNotContain("127.0.0.1:8080")
                .doesNotContain("localhost:6379")
                .doesNotContain("localhost:9092");
    }

    @Test
    @DisplayName("Prometheus 설정은 YAML 구조상 scrape job과 target을 정확히 매핑함")
    void prometheusYamlMapsScrapeJobsToExpectedTargets() throws IOException {
        Map<String, Object> prometheus = loadYaml("monitoring/prometheus/prometheus.yml");
        List<Map<String, Object>> scrapeConfigs = mapList(prometheus.get("scrape_configs"));

        assertThat(scrapeConfigs)
                .extracting(config -> config.get("job_name"))
                .containsExactly("prometheus", "codedock-backend", "redis", "kafka");
        assertThat(firstTarget(scrapeConfigs.get(0))).isEqualTo("localhost:9090");
        assertThat(scrapeConfigs.get(1).get("metrics_path")).isEqualTo("/actuator/prometheus");
        assertThat(firstTarget(scrapeConfigs.get(1))).isEqualTo("app:8080");
        assertThat(firstTarget(scrapeConfigs.get(2))).isEqualTo("redis-exporter:9121");
        assertThat(firstTarget(scrapeConfigs.get(3))).isEqualTo("kafka-exporter:9308");
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
    @DisplayName("Grafana datasource provisioning은 외부 주소가 아닌 compose 내부 service name을 사용함")
    void grafanaDatasourcesUseComposeInternalServiceNames() throws IOException {
        String datasources = Files.readString(Path.of(
                "monitoring/grafana/provisioning/datasources/datasources.yml"));

        assertThat(datasources)
                .contains("url: http://prometheus:9090")
                .contains("url: http://loki:3100")
                .doesNotContain("localhost:9090")
                .doesNotContain("localhost:3100")
                .doesNotContain("127.0.0.1:9090")
                .doesNotContain("127.0.0.1:3100");
    }

    @Test
    @DisplayName("Grafana provisioning 설정은 apiVersion 1과 고정 uid를 사용함")
    void grafanaProvisioningUsesApiVersionOneAndStableUids() throws IOException {
        String datasources = Files.readString(Path.of(
                "monitoring/grafana/provisioning/datasources/datasources.yml"));
        String dashboards = Files.readString(Path.of(
                "monitoring/grafana/provisioning/dashboards/dashboards.yml"));

        assertThat(datasources)
                .contains("apiVersion: 1")
                .contains("uid: Prometheus")
                .contains("uid: Loki");
        assertThat(dashboards)
                .contains("apiVersion: 1")
                .contains("name: CodeDock")
                .contains("folder: CodeDock");
    }

    @Test
    @DisplayName("Grafana datasource provisioning은 YAML 구조상 datasource 2개만 등록함")
    void grafanaDatasourceYamlRegistersOnlyPrometheusAndLoki() throws IOException {
        Map<String, Object> datasourceConfig =
                loadYaml("monitoring/grafana/provisioning/datasources/datasources.yml");
        List<Map<String, Object>> datasources = mapList(datasourceConfig.get("datasources"));

        assertThat(datasourceConfig.get("apiVersion")).isEqualTo(1);
        assertThat(datasources).hasSize(2);
        assertThat(datasources)
                .extracting(dataSource -> dataSource.get("uid"))
                .containsExactly("Prometheus", "Loki");
        assertThat(datasources)
                .extracting(dataSource -> dataSource.get("url"))
                .containsExactly("http://prometheus:9090", "http://loki:3100");
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
    @DisplayName("기본 Grafana dashboard JSON은 유효하며 핵심 패널 5개를 포함함")
    void grafanaDashboardJsonIsValidAndContainsExpectedPanels() throws IOException {
        JsonNode dashboard = objectMapper.readTree(Path.of("monitoring/grafana/dashboards/codedock-overview.json").toFile());
        List<JsonNode> panels = StreamSupport.stream(dashboard.path("panels").spliterator(), false)
                .toList();

        assertThat(dashboard.path("uid").asText()).isEqualTo("codedock-overview");
        assertThat(dashboard.path("title").asText()).isEqualTo("CodeDock Overview");
        assertThat(panels)
                .hasSize(5)
                .extracting(panel -> panel.path("title").asText())
                .containsExactly(
                        "Backend Up",
                        "Redis Up",
                        "Kafka Up",
                        "HTTP Requests",
                        "Backend Logs"
                );
        assertThat(panels)
                .extracting(panel -> panel.path("datasource").path("uid").asText())
                .contains("Prometheus", "Loki");
    }

    @Test
    @DisplayName("기본 Grafana dashboard의 PromQL과 LogQL 쿼리는 datasource 용도와 일치함")
    void grafanaDashboardQueriesMatchDatasourcePurpose() throws IOException {
        JsonNode dashboard = objectMapper.readTree(Path.of("monitoring/grafana/dashboards/codedock-overview.json").toFile());
        List<JsonNode> panels = StreamSupport.stream(dashboard.path("panels").spliterator(), false)
                .toList();

        JsonNode backendUpPanel = panels.get(0);
        JsonNode redisUpPanel = panels.get(1);
        JsonNode kafkaUpPanel = panels.get(2);
        JsonNode httpRequestsPanel = panels.get(3);
        JsonNode backendLogsPanel = panels.get(4);

        assertThat(firstExpr(backendUpPanel)).isEqualTo("up{job=\"codedock-backend\"}");
        assertThat(firstExpr(redisUpPanel)).isEqualTo("up{job=\"redis\"}");
        assertThat(firstExpr(kafkaUpPanel)).isEqualTo("up{job=\"kafka\"}");
        assertThat(firstExpr(httpRequestsPanel))
                .isEqualTo("sum by (method, status) (rate(http_server_requests_seconds_count{job=\"codedock-backend\"}[5m]))");
        assertThat(firstExpr(backendLogsPanel)).isEqualTo("{service=\"app\"}");
        assertThat(backendLogsPanel.path("datasource").path("uid").asText()).isEqualTo("Loki");
    }

    @Test
    @DisplayName("기본 Grafana dashboard는 새로고침 주기와 기본 조회 시간을 제한함")
    void grafanaDashboardKeepsBoundedRefreshAndTimeRange() throws IOException {
        JsonNode dashboard = objectMapper.readTree(Path.of("monitoring/grafana/dashboards/codedock-overview.json").toFile());

        assertThat(dashboard.path("refresh").asText()).isEqualTo("30s");
        assertThat(dashboard.path("time").path("from").asText()).isEqualTo("now-1h");
        assertThat(dashboard.path("time").path("to").asText()).isEqualTo("now");
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
    @DisplayName("Promtail 설정은 position 파일을 사용해 재시작 후 로그 중복 전송을 줄임")
    void promtailConfigUsesPositionFileForRestartSafety() throws IOException {
        String promtail = Files.readString(Path.of("monitoring/promtail/promtail-config.yml"));

        assertThat(promtail)
                .contains("positions:")
                .contains("filename: /tmp/promtail-positions.yml")
                .contains("refresh_interval: 10s");
    }

    @Test
    @DisplayName("Promtail 설정은 Docker service discovery 하나만 사용함")
    void promtailConfigUsesOnlyDockerServiceDiscovery() throws IOException {
        String promtail = Files.readString(Path.of("monitoring/promtail/promtail-config.yml"));

        assertThat(promtail)
                .contains("job_name: docker-containers")
                .contains("docker_sd_configs")
                .doesNotContain("static_configs")
                .doesNotContain("kubernetes_sd_configs");
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
    @DisplayName("Loki 설정은 단일 노드 filesystem storage에 필요한 디렉터리를 모두 분리함")
    void lokiConfigSeparatesFilesystemDirectoriesForSingleNodeStorage() throws IOException {
        String loki = Files.readString(Path.of("monitoring/loki/loki-config.yml"));

        assertThat(loki)
                .contains("path_prefix: /loki")
                .contains("chunks_directory: /loki/chunks")
                .contains("rules_directory: /loki/rules")
                .contains("working_directory: /loki/compactor")
                .contains("delete_request_store: filesystem");
    }

    @Test
    @DisplayName("SecurityConfig와 JwtAuthFilter는 /actuator/prometheus를 공개 endpoint로 유지함")
    void securityLayerKeepsPrometheusEndpointPublic() throws IOException {
        String securityConfig = Files.readString(Path.of(
                "src/main/java/com/team1/codedock/global/config/SecurityConfig.java"));
        String jwtAuthFilter = Files.readString(Path.of(
                "src/main/java/com/team1/codedock/global/security/JwtAuthFilter.java"));

        assertThat(securityConfig)
                .contains("\"/actuator/health\"")
                .contains("\"/actuator/prometheus\"");
        assertThat(jwtAuthFilter)
                .contains("path.equals(\"/actuator/health\")")
                .contains("path.equals(\"/actuator/prometheus\")");
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

    @Test
    @DisplayName("모니터링 가이드는 Grafana 접속과 datasource 확인 방법을 설명함")
    void monitoringGuideDocumentsGrafanaAccessAndDatasourceChecks() throws IOException {
        String guide = Files.readString(Path.of("docs/monitoring-guide.md"));

        assertThat(guide)
                .contains("http://{EC2_PUBLIC_IP}:3000")
                .contains("GRAFANA_ADMIN_USER")
                .contains("GRAFANA_ADMIN_PASSWORD")
                .contains("Prometheus datasource")
                .contains("Loki datasource")
                .contains("{service=\"app\"}");
    }

    private Properties loadProperties(String path) throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(Path.of(path))) {
            properties.load(inputStream);
        }
        return properties;
    }

    private String firstExpr(JsonNode panel) {
        return panel.path("targets").path(0).path("expr").asText();
    }

    private String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n");
    }

    private int countOccurrences(String value, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(pattern, index)) >= 0) {
            count++;
            index += pattern.length();
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(String path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(Path.of(path))) {
            return new Yaml().loadAs(inputStream, Map.class);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private static Map<String, Object> service(String serviceName) {
        return map(services.get(serviceName));
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        return ((List<Object>) value).stream()
                .map(String::valueOf)
                .toList();
    }

    private String firstTarget(Map<String, Object> scrapeConfig) {
        List<Map<String, Object>> staticConfigs = mapList(scrapeConfig.get("static_configs"));
        return stringList(staticConfigs.get(0).get("targets")).get(0);
    }
}
