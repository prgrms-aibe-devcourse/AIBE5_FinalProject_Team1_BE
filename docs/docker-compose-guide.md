# 백엔드 Docker Compose 실행 가이드

## 목적

백엔드 애플리케이션, Redis, Kafka를 Docker Compose로 함께 실행한다.

이 구성은 로컬 검증과 AWS EC2 배포의 기본 단위로 사용한다. GitHub Actions 기반 CI/CD는 별도 이슈에서 이미지 빌드와 서버 배포 자동화를 추가한다.

## 사전 준비

1. Docker Desktop 또는 Docker Engine을 실행한다.
2. 프로젝트 루트에서 `.env.example`을 복사해 `.env`를 만든다.
3. `.env`에 DB, JWT, OAuth, 외부 API 값을 채운다.

```bash
cp .env.example .env
```

Windows PowerShell에서는 아래처럼 복사한다.

```powershell
Copy-Item .env.example .env
```

## Oracle DB 주소

Oracle DB가 Docker 밖의 로컬 PC에서 실행 중이면 컨테이너 내부의 `localhost`로 접근할 수 없다.

Docker Desktop 환경에서는 아래처럼 host 주소를 사용한다.

```properties
DB_URL=jdbc:oracle:thin:@host.docker.internal:1521:orcl
```

AWS 배포 환경에서는 실제 Oracle 접속 주소를 넣는다.

## 실행

```bash
docker compose up -d --build
```

## 상태 확인

백엔드 컨테이너 로그를 확인한다.

```bash
docker compose logs -f app
```

Actuator health endpoint를 확인한다.

```bash
curl http://localhost:8080/actuator/health
```

정상 응답 예시는 아래와 같다.

```json
{
  "status": "UP"
}
```

## 중지

```bash
docker compose down
```

볼륨까지 삭제하려면 아래 명령을 사용한다.

```bash
docker compose down -v
```

## 서비스 주소

Compose 내부에서 백엔드는 아래 주소로 Redis와 Kafka에 연결한다.

- Redis: `redis:6379`
- Kafka: `kafka:9092`

컨테이너 내부에서 `localhost`는 호스트 PC가 아니라 자기 자신의 컨테이너를 의미하므로 사용하지 않는다.

## AWS 담당자 확인 사항

- EC2 보안그룹에서 필요한 포트를 연다.
- 서버에 `.env`를 직접 생성하고 민감 정보를 채운다.
- 운영 프론트 주소에 맞춰 OAuth redirect, CORS, WebSocket origin을 설정한다.
- HTTPS와 도메인은 Nginx 또는 로드밸런서에서 별도로 처리한다.
