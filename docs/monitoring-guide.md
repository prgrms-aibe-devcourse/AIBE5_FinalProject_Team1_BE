# Prometheus/Grafana/Loki 모니터링 가이드

## 목적

Docker Compose 기반 EC2 배포 환경에서 백엔드 지표와 컨테이너 로그를 확인하기 위한 최소 모니터링 구성을 설명한다.

현재 구성은 아래 역할을 가진다.

- Prometheus: 백엔드/Redis/Kafka 지표 수집
- Grafana: 지표와 로그 조회 UI
- Loki: 컨테이너 로그 저장
- Promtail: Docker 컨테이너 로그 수집
- Redis exporter: Redis 지표 노출
- Kafka exporter: Kafka broker 지표 노출

## 서비스 구성

| 서비스 | 내부 주소 | host 노출 | 용도 |
| --- | --- | --- | --- |
| `prometheus` | `prometheus:9090` | `127.0.0.1:${PROMETHEUS_HOST_PORT:-9090}` | metric 수집/조회 |
| `grafana` | `grafana:3000` | `${GRAFANA_HOST_PORT:-3000}` | dashboard UI |
| `loki` | `loki:3100` | `127.0.0.1:${LOKI_HOST_PORT:-3100}` | log 저장/조회 API |
| `promtail` | 내부 전용 | 없음 | Docker log 수집 |
| `redis-exporter` | `redis-exporter:9121` | 없음 | Redis metric |
| `kafka-exporter` | `kafka-exporter:9308` | 없음 | Kafka metric |

Prometheus와 Loki는 기본적으로 host loopback에만 바인딩한다. EC2 외부에서 직접 접근하지 않고, Grafana를 통해 확인하는 구성을 기본으로 한다.

## EC2 `.env` 예시

```env
MANAGEMENT_PROMETHEUS_ENABLED=true

GRAFANA_HOST_PORT=3000
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=change-me

PROMETHEUS_HOST_PORT=9090
LOKI_HOST_PORT=3100
PROMTAIL_LOG_LEVEL=info
```

`GRAFANA_ADMIN_PASSWORD`는 운영 EC2에서 반드시 변경한다. `.env`는 git에 올리지 않는다.

## 실행

```bash
docker compose up -d
docker compose ps
```

Grafana 접속:

```text
http://{EC2_PUBLIC_IP}:3000
```

로그 확인:

```bash
docker compose logs -f app
docker compose logs -f prometheus
docker compose logs -f grafana
docker compose logs -f loki
docker compose logs -f promtail
```

## Prometheus 수집 대상

Prometheus 설정 파일:

```text
monitoring/prometheus/prometheus.yml
```

수집 대상:

- `app:8080/actuator/prometheus`
- `redis-exporter:9121`
- `kafka-exporter:9308`
- `prometheus:9090`

백엔드 Prometheus endpoint는 Spring Security와 JWT 필터에서 공개 경로로 허용한다.

## Grafana provisioning

Grafana는 기동 시 datasource와 dashboard를 자동 등록한다.

Datasource:

```text
monitoring/grafana/provisioning/datasources/datasources.yml
```

Dashboard provider:

```text
monitoring/grafana/provisioning/dashboards/dashboards.yml
```

기본 dashboard:

```text
monitoring/grafana/dashboards/codedock-overview.json
```

Grafana 접속 후 확인 순서:

1. `http://{EC2_PUBLIC_IP}:3000` 접속
2. `.env`의 `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD`로 로그인
3. Connections 또는 Data sources에서 `Prometheus datasource`가 연결됐는지 확인
4. Connections 또는 Data sources에서 `Loki datasource`가 연결됐는지 확인
5. Dashboards에서 `CodeDock Overview` dashboard가 열리는지 확인
6. Explore에서 Loki를 선택하고 `{service="app"}`으로 백엔드 로그가 조회되는지 확인

## 로그 수집

Promtail 설정 파일:

```text
monitoring/promtail/promtail-config.yml
```

Promtail은 Docker socket을 읽어 compose container log를 Loki로 전송한다.

기본 label:

- `container`
- `service`
- `compose_project`
- `stream`

Grafana Explore에서 Loki datasource를 선택하고 아래처럼 조회한다.

```logql
{service="app"}
```

## 보안그룹 정책

EC2 보안그룹 권장:

| 포트 | 외부 오픈 | 설명 |
| --- | --- | --- |
| `8080` | 필요 시 ALB/Nginx 앞단 기준 | 백엔드 API |
| `3000` | 관리자 IP만 허용 권장 | Grafana |
| `9090` | 열지 않음 | Prometheus |
| `3100` | 열지 않음 | Loki |
| `9121` | 열지 않음 | Redis exporter |
| `9308` | 열지 않음 | Kafka exporter |
| `6379` | 열지 않음 | Redis |
| `9092` | 열지 않음 | Kafka |

Prometheus와 Loki는 host에서 `127.0.0.1`로만 바인딩한다. 외부에서 직접 확인해야 하면 SSH 터널을 사용한다.

```bash
ssh -L 9090:127.0.0.1:9090 -L 3100:127.0.0.1:3100 ec2-user@{EC2_PUBLIC_IP}
```

## 주의사항

- `docker compose config` 출력에는 `.env`의 비밀번호와 secret이 펼쳐질 수 있으므로 전체 출력 공유 금지
- Grafana 기본 비밀번호 `admin`을 운영에서 그대로 사용하지 않음
- Prometheus/Loki/exporter 포트를 인터넷에 직접 열지 않음
- 이 구성은 단일 EC2 Compose 기준이며, 다중 인스턴스나 장기 로그 보관은 별도 설계가 필요함

## 후속 작업

- Slack/Discord alert 연동
- 운영용 dashboard 고도화
- Kafka consumer lag dashboard 보강
- 로그 retention/backup 정책 수립
- OpenTelemetry trace 도입 검토
