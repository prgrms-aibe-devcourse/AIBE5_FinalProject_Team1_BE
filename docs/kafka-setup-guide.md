# Kafka 연결 설정 가이드

## 목적

백엔드 애플리케이션에서 Kafka를 비동기 이벤트 처리 기반으로 사용할 수 있도록 로컬, Docker Compose, EC2 배포 환경의 연결 방식을 정리한다.

현재 단계의 Kafka는 연결과 공통 설정까지만 담당한다. GitHub webhook 이벤트 발행, AI 분석 요청 큐, 알림 이벤트 처리, ActivityLog 적재는 별도 이슈에서 다룬다.

## 환경변수

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Spring Boot 애플리케이션이 접속할 Kafka bootstrap servers |
| `KAFKA_CLIENT_ID` | `codedock-backend` | Kafka client id |
| `KAFKA_CONSUMER_GROUP_ID` | `codedock` | 기본 consumer group id |
| `KAFKA_CONSUMER_AUTO_OFFSET_RESET` | `earliest` | 초기 offset이 없을 때 읽기 시작 위치 |
| `KAFKA_CONSUMER_ENABLE_AUTO_COMMIT` | `false` | consumer offset 자동 커밋 여부 |
| `KAFKA_PRODUCER_ACKS` | `all` | producer 전송 확인 수준 |
| `KAFKA_PRODUCER_RETRIES` | `3` | producer 재시도 횟수 |
| `KAFKA_PRODUCER_ENABLE_IDEMPOTENCE` | `true` | producer idempotence 활성화 여부 |
| `KAFKA_PRODUCER_MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION` | `5` | idempotence와 함께 사용할 in-flight 요청 상한 |
| `KAFKA_LISTENER_ACK_MODE` | `record` | `@KafkaListener` 컨테이너 ack mode |
| `KAFKA_LISTENER_MISSING_TOPICS_FATAL` | `false` | topic이 없을 때 애플리케이션 기동 실패 처리 여부 |

## 로컬 실행

로컬 PC에서 Kafka를 직접 실행하면 아래 값으로 연결한다.

```properties
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_CLIENT_ID=codedock-backend-local
KAFKA_CONSUMER_GROUP_ID=codedock
```

Docker Compose로 Kafka만 띄우고 백엔드는 IDE에서 실행하는 경우에는 host port를 사용한다.

```properties
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

host PC의 Kafka 포트가 충돌나서 `.env`에서 `KAFKA_HOST_PORT=19092`로 바꿨다면, IDE 실행 시에는 아래처럼 맞춘다.

```properties
KAFKA_BOOTSTRAP_SERVERS=localhost:19092
```

## Docker Compose 실행

Compose 내부에서 백엔드 컨테이너는 Kafka 컨테이너를 `localhost`가 아니라 service name으로 찾아야 한다.

```properties
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
```

`docker-compose.yml`의 app 서비스는 기본적으로 위 값을 주입한다.

host PC에서 Kafka에 직접 접근할 때는 아래 포트를 사용한다.

```properties
KAFKA_HOST_PORT=9092
```

이 값은 host PC에 노출되는 포트만 바꾼다. Compose 내부 통신은 계속 `kafka:9092`를 사용한다.

### 외부 listener 주의사항

현재 Compose의 Kafka `EXTERNAL` listener는 host PC에서 직접 Kafka를 확인하기 위한 로컬 개발용 경로다.

- Compose 내부 app 컨테이너: `kafka:9092` 사용
- host PC의 Kafka CLI/테스트 도구: `localhost:${KAFKA_HOST_PORT}` 사용
- 다른 서버나 외부 클라이언트: 기본 설정으로 직접 접속하지 않음

운영 EC2 배포에서는 백엔드와 Kafka가 같은 Compose 네트워크 안에서 통신하므로, 보안그룹에서 Kafka 포트 `9092`를 인터넷에 열지 않는다. 외부 클라이언트가 Kafka에 직접 접속해야 하는 별도 요구가 생기면 `KAFKA_ADVERTISED_LISTENERS`의 `EXTERNAL` host를 EC2 private DNS 또는 별도 내부 DNS로 바꾸고, 네트워크 접근 제어와 인증 방식을 다시 설계한다.

### 설정 출력 주의사항

`docker compose config`는 `.env` 값을 치환한 최종 설정을 출력한다. 이 출력에는 `DB_PASSWORD`, `JWT_SECRET`, `GITHUB_CLIENT_SECRET` 같은 민감 정보가 그대로 포함될 수 있다.

- 팀 채팅, 이슈, PR 코멘트에 `docker compose config` 전체 출력을 붙이지 않음
- 공유가 필요하면 민감 정보를 마스킹한 뒤 일부 구간만 공유함
- CI/CD 로그에서도 `.env` 전체 출력이나 compose config 전체 출력은 남기지 않음

## Health Check

Compose의 Kafka 컨테이너는 `kafka-broker-api-versions.sh`로 broker 응답 여부를 확인한다.

```yaml
healthcheck:
  test: ["CMD-SHELL", "/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 >/dev/null 2>&1 || exit 1"]
```

Spring Boot Actuator는 기본으로 Kafka health contributor를 제공하지 않는다. 애플리케이션 health는 DB, Redis 등 Spring Boot가 제공하는 health indicator를 기준으로 확인하고, Kafka broker 준비 상태는 Compose healthcheck와 로그로 확인한다.

## 기본 직렬화 전략

현재 공통 설정은 문자열 기반 메시지를 기본으로 한다.

- key serializer: `StringSerializer`
- value serializer: `StringSerializer`
- key deserializer: `StringDeserializer`
- value deserializer: `StringDeserializer`

도메인 DTO를 Kafka에 직접 싣는 단계에서는 JSON 직렬화, topic naming, message envelope, 재처리 정책을 별도 이슈에서 정한다.

## Redis와 Kafka 역할 구분

Redis는 빠르게 변하는 현재 상태와 TTL 기반 데이터에 적합하다.

- 온라인 상태/presence
- 캐시
- rate limit
- 짧게 유지되는 세션성 데이터
- 단순 Pub/Sub

Kafka는 나중에 다시 처리할 수 있어야 하는 이벤트 흐름에 적합하다.

- GitHub webhook 이벤트
- PR/Issue 변경 이벤트
- AI 분석 요청 큐
- 알림 이벤트 발행/소비
- ActivityLog 적재

## 후속 작업

이번 설정은 Kafka 연결 기반만 만든다. 아래 작업은 별도 이슈에서 진행한다.

- GitHub webhook Kafka 이벤트 발행
- AI 분석 요청 Kafka 큐 전환
- 알림 이벤트 Kafka 발행/소비
- topic naming 규칙 정의
- 실패 이벤트 재처리 정책 정의
- 운영 모니터링 고도화
