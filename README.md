# 재테콩

개인 가계부·저축·자산 관리 웹앱입니다. 로그인 없이 한 사람이 사용하는 구조입니다.

## 개발환경

- Backend: Java 17, Spring Boot 4.1.1, Maven Wrapper
- Frontend: Vue 3, TypeScript, Vite, Vue Router, Pinia
- Database: PostgreSQL 16 (Docker Compose)
- 실행 방식: 브라우저 기반 반응형 웹앱

## 구조

```text
.
├─ backend/     # Spring Boot API
├─ frontend/    # Vue SPA
├─ infra/       # 추후 배포/운영 설정
└─ docker-compose.yml
```

## 실행

```powershell
docker compose up -d postgres
cd backend
.\mvnw.cmd spring-boot:run
```

새 터미널에서:

```powershell
cd frontend
npm run dev
```

프론트엔드는 `http://localhost:6050`, 백엔드는 `http://localhost:9080`을 사용합니다.

현재는 개발환경만 구성되어 있으며, 도메인 모델·화면 기능·DB 마이그레이션은 다음 단계에서 추가합니다.
