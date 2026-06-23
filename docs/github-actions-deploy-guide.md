# GitHub Actions Docker 배포 가이드

## 목적

GitHub Actions로 백엔드 테스트, Docker 이미지 빌드, GHCR push, EC2 배포를 자동화한다.

## Workflow 동작

`.github/workflows/backend-ci-cd.yml`은 아래 흐름으로 동작한다.

### Pull Request

`main` 대상 PR이 열리면 테스트만 실행한다.

```text
PR -> ./gradlew test
```

### main push

`main`에 merge 또는 push되면 테스트, 이미지 빌드, 이미지 push, EC2 배포가 순서대로 실행된다.

```text
main push
-> ./gradlew test
-> Docker image build
-> GHCR push
-> EC2 SSH 접속
-> EC2 배포 디렉터리가 git clone이면 git pull
-> docker compose pull app
-> docker compose up -d
```

## Docker 이미지

이미지는 GHCR에 push한다.

```text
ghcr.io/{github-repository-owner}/codedock-backend:latest
ghcr.io/{github-repository-owner}/codedock-backend:sha-{commit-sha}
```

현재 workflow에서는 아래 값을 이미지 이름으로 사용한다.

```yaml
IMAGE_NAME: ghcr.io/${{ github.repository_owner }}/codedock-backend
```

## GitHub Actions Secrets

Repository Settings > Secrets and variables > Actions에 아래 Secrets를 등록한다.

| Secret | 설명 |
|---|---|
| `EC2_HOST` | 배포 대상 EC2 public IP 또는 도메인 |
| `EC2_USERNAME` | SSH 접속 사용자명. 예: `ubuntu`, `ec2-user` |
| `EC2_SSH_PRIVATE_KEY` | EC2 접속용 private key 내용 |
| `EC2_SSH_PORT` | SSH 포트. 기본값은 `22` |
| `EC2_DEPLOY_PATH` | EC2에서 `docker-compose.yml`과 `.env`가 있는 디렉터리 |
| `GHCR_USERNAME` | EC2에서 GHCR에 로그인할 GitHub 사용자명 |
| `GHCR_TOKEN` | EC2에서 GHCR image pull에 사용할 token |

`GHCR_TOKEN`은 최소 `read:packages` 권한이 필요하다. 패키지가 private이면 EC2에서 pull할 때 로그인이 필요하다.

이미지 push는 GitHub Actions의 `GITHUB_TOKEN`을 사용하므로 별도 push token은 필요 없다.

## EC2 사전 준비

EC2에는 아래가 준비되어 있어야 한다.

- Docker Engine
- Docker Compose plugin
- 배포 디렉터리
- `docker-compose.yml`
- `.env`

예시:

```bash
mkdir -p ~/codedock-backend
cd ~/codedock-backend
```

배포 디렉터리를 git clone으로 관리하면 workflow가 배포 시 `main` 최신 compose 파일을 pull한다.

```bash
git clone https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team1_BE.git ~/codedock-backend
cd ~/codedock-backend
```

`EC2_DEPLOY_PATH`는 위 경로와 맞춘다.

```text
EC2_DEPLOY_PATH=/home/ubuntu/codedock-backend
```

## EC2 .env 설정

EC2의 `.env`는 git에 올리지 않고 서버에 직접 작성한다.

운영 배포에서는 `APP_IMAGE`를 GHCR 이미지로 지정한다.

```properties
APP_IMAGE=ghcr.io/prgrms-aibe-devcourse/codedock-backend:latest
APP_PORT=8080
REDIS_HOST_PORT=6379
KAFKA_HOST_PORT=9092
SPRING_PROFILES_ACTIVE=docker

DB_URL=jdbc:oracle:thin:@{oracle-host}:1521:orcl
DB_USERNAME=
DB_PASSWORD=
JWT_SECRET=

GITHUB_CLIENT_ID=
GITHUB_CLIENT_SECRET=

OAUTH2_REDIRECT_URI=https://{domain}/oauth/callback
OAUTH2_POPUP_REDIRECT_URI=https://{domain}/oauth/popup-callback
APP_BASE_URL=https://{domain}

APP_CORS_ALLOWED_ORIGIN_PATTERNS=https://{domain}
APP_WEBSOCKET_ALLOWED_ORIGIN_PATTERNS=https://{domain}
APP_LOCAL_NETWORK_ORIGIN_ENABLED=false

GROQ_API_KEY=
GROQ_MODEL=llama-3.3-70b-versatile
```

## 수동 배포 확인

GitHub Actions를 붙이기 전에 EC2에서 한 번 수동으로 확인한다.

```bash
cd /path/to/deploy
docker login ghcr.io
docker compose pull app
docker compose up -d
docker compose ps
curl http://localhost:8080/actuator/health
```

## 주의 사항

- `APP_IMAGE`를 설정하지 않으면 compose는 로컬 기본 이미지인 `codedock-backend:local`을 사용한다.
- 운영 서버의 `.env`에는 민감 정보가 들어가므로 절대 git에 올리지 않는다.
- GitHub OAuth callback URL과 webhook payload URL은 운영 도메인 기준으로 별도 등록해야 한다.
- HTTPS, nginx reverse proxy, 도메인 설정은 별도 인프라 이슈에서 처리한다.
