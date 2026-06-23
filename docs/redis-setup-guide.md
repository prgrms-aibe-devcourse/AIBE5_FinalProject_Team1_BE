# Redis 연결 설정 가이드

## 목적

백엔드 애플리케이션에서 Redis를 공통 인프라로 사용할 수 있도록 로컬, Docker Compose, EC2 배포 환경의 연결 방식을 정리한다.

현재 단계의 Redis는 연결과 공통 템플릿 설정까지만 담당한다. WebSocket Pub/Sub, STOMP broker relay, refresh token 저장소 이전, 캐시 적용 대상 선정은 별도 이슈에서 다룬다.

## 환경변수

| 이름 | 기본값 | 설명 |
| --- | --- | --- |
| `REDIS_HOST` | `localhost` | Redis 서버 호스트 |
| `REDIS_PORT` | `6379` | Redis 서버 포트 |
| `REDIS_PASSWORD` | 빈 값 | Redis 비밀번호. 비어 있으면 인증 없이 연결 |
| `REDIS_DATABASE` | `0` | 사용할 Redis logical database index |
| `REDIS_TIMEOUT` | `2s` | Redis 명령 타임아웃 |
| `REDIS_CONNECT_TIMEOUT` | `2s` | Redis 연결 타임아웃 |

## 로컬 실행

로컬 PC에서 Redis를 직접 실행하면 아래 값으로 연결한다.

```properties
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DATABASE=0
REDIS_TIMEOUT=2s
REDIS_CONNECT_TIMEOUT=2s
```

Redis를 Docker로만 띄우고 백엔드는 IDE에서 실행하는 경우에도 host port를 기본값으로 열었다면 `localhost:6379`를 사용한다.

## Docker Compose 실행

Compose 내부에서 백엔드 컨테이너는 Redis 컨테이너를 `localhost`가 아니라 service name으로 찾아야 한다.

```properties
REDIS_HOST=redis
REDIS_PORT=6379
```

`docker-compose.yml`의 app 서비스는 위 값을 자동으로 주입한다. Redis 비밀번호를 사용하려면 `.env`에 아래처럼 값을 넣는다.

```properties
REDIS_PASSWORD=change-me
```

이 값이 있으면 Redis 컨테이너는 `--requirepass`로 실행되고, app 컨테이너도 같은 비밀번호로 접속한다. 값이 비어 있으면 인증 없는 개발용 Redis로 실행한다.

## EC2 배포

단일 EC2 + Docker Compose 배포에서는 Redis도 같은 compose 네트워크에 올리는 방식을 기본으로 한다.

```properties
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD={운영용_비밀번호}
```

운영 서버에서는 Redis host port를 외부에 열 필요가 없다. 외부 접근이 필요하지 않다면 보안그룹에서 Redis 포트 `6379`를 열지 않는다.

## Spring Bean

`RedisConfig`는 `RedisConnectionFactory`가 있을 때만 아래 공통 빈을 등록한다.

- `StringRedisTemplate`
- `RedisTemplate<String, Object>`

테스트 환경처럼 Redis 자동 설정이 꺼진 경우에는 위 빈을 등록하지 않는다. 이 정책 덕분에 Redis 서버가 없어도 일반 단위 테스트가 깨지지 않는다.

## Health Check

Actuator health에 Redis 상태가 포함된다.

```bash
curl http://localhost:8080/actuator/health
```

Redis 연결이 실패하면 애플리케이션 프로세스가 즉시 종료되는 것이 아니라 health 상태가 `DOWN`이 될 수 있다. 배포 환경에서는 Docker healthcheck와 모니터링에서 이 값을 보고 재시작 또는 장애 대응을 판단한다.

## 제외 범위

이번 설정은 Redis 연결 기반만 만든다. 아래 작업은 별도 이슈에서 진행한다.

- Redis Pub/Sub 기반 WebSocket 확장
- STOMP broker relay 전환
- refresh token Redis 저장
- 캐시 적용 대상 선정
- Redis Cluster/Sentinel 구성
