# EC2 자동 배포

`main`에 push하면 GitHub Actions(`.github/workflows/deploy.yaml`)가 백엔드와 프론트 이미지를 arm64로 빌드해 GHCR에 올리고, SSH로 EC2에 접속해 `docker compose`로 DB·백엔드·프론트를 다시 띄운다. nginx(프론트 컨테이너)가 80 포트로 화면을 보여 주고 `/api`는 백엔드로 넘긴다.

## 1. EC2 만들기 (최초 1회)

AWS 콘솔 → 리전 **서울(ap-northeast-2)** → EC2 → 인스턴스 시작

| 항목 | 값 |
| --- | --- |
| 이름 | `jaetekong` |
| AMI | Ubuntu Server 24.04 LTS, 아키텍처 **64비트(Arm)** |
| 인스턴스 유형 | `t4g.micro` |
| 키 페어 | 새로 생성 → 유형 RSA, 형식 `.pem` → 내려받은 파일 보관 |
| 네트워크 설정 | 보안 그룹 생성: SSH(22) 허용, **인터넷에서 HTTP 트래픽 허용(80)** 체크 |
| 스토리지 | 20 GiB gp3 |
| 고급 세부 정보 → 사용자 데이터 | (선택) `deploy/ec2-user-data.sh` 내용. 비워 둬도 첫 배포 때 워크플로가 스왑 1GB와 Docker를 설치한다 |

인스턴스가 뜬 뒤 EC2 → **탄력적 IP** → 주소 할당 → 방금 만든 인스턴스에 연결. 이 IP가 서비스 주소가 된다.

## 2. GitHub Secrets 등록 (최초 1회)

저장소 → Settings → Secrets and variables → Actions → New repository secret

| 이름 | 값 |
| --- | --- |
| `EC2_HOST` | 탄력적 IP (예: `3.35.xxx.xxx`) |
| `EC2_SSH_KEY` | 내려받은 `.pem` 파일 내용 전체 (`-----BEGIN` 줄부터 `END-----` 줄까지) |
| `DB_PASSWORD` | 운영 DB 비밀번호로 쓸 임의의 긴 문자열 |

## 3. 배포

- `main`에 push하면 자동 배포된다.
- 수동 실행: 저장소 → Actions → Deploy to EC2 → Run workflow
- 배포가 끝나면 `http://<탄력적 IP>` 로 접속한다.

## 참고

- 백엔드 설정은 `application.yml`(git 미추적) 대신 `compose.prod.yaml`의 환경 변수로 넣는다.
- DB 데이터는 EC2의 Docker 볼륨 `postgres-data`에 남는다. 컨테이너를 다시 띄워도 유지된다.
- 메모리: JVM 힙 256MB 제한, 컨테이너별 메모리 상한, 스왑 1GB.
