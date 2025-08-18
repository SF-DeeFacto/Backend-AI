# Backend - AI

DeeFacto 스마트팩토리 AI 서비스입니다.  
AWS Bedrock을 이용한 AI 서비스-리포트, 임계치 추천 기능을 제공합니다.

---

# 🛠️ 프로젝트 환경 세팅 가이드 (for Backend)

## 💻 개발 환경

- Java 17 (Amazon Corretto)
- Spring Boot 3.5.4
- Gradle
- Spring Cloud Gateway
- Redis (세션 관리 / 토큰 블랙리스트)
- MySQL
- Docker + Helm + Jenkins (CI/CD)
- MSA 구조 (API Gateway + Auth Service + User Service)

---

## 📦 프로젝트 초기 셋업 (처음 클론할 경우)

```bash
# 의존성 설치
./gradlew build

# DB 로컬 실행 (도커)
docker-compose up -d mysql redis

# 마이그레이션 (Flyway 적용 시)
./gradlew flywayMigrate
```

---

## 🧩 주요 기능 / 구현 목록

- **임계치 추천**: MES 데이터와 센서 데이터를 이용한 임계치 추천
- **정기 리포트 생성**: 근무시간 이전 12시간에 대한 이슈 리포트 생성
- **비정기 리포트 생성**: 근무자의 요청에 따른 비정기 이슈 리포트 생성
  
- **리포트 조회**: 생성된 정기/비정기 리포트 조회
- **리포트 다운로드**: 권한 있는 리포트에 대해 다운로드
- **리포트 삭제**: 본인이 생성한 비정기 리포트 삭제

---

## 🛠️ Backend 프로젝트 개발 가이드

### 📁 폴더 구조 및 설정 규칙

- 모든 설정은 `application.yml` 사용
- 환경별 설정 분리: `application-dev.yml`, `application-prod.yml`
- 설정 클래스는 `XXConfig.java` 네이밍  
  예: `RedisConfig.java`, `JwtConfig.java`

---

### ⚙️ 환경 변수 및 민감 정보 관리

- 모든 민감 정보는 `.env` 또는 환경변수로 관리
- 주요 환경 변수 예시:

```env
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/...
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...

JWT_SECRET_KEY=...
REDIS_HOST=localhost
REDIS_PORT=6379
```

---

## 🧑‍💻 커밋 메시지 컨벤션 (`|` 구분자 사용)

```bash
[type] | sprint | JIRA-KEY | 기능 요약 | 담당자
```

- **type**: feat, fix, docs, config, refactor, test, chore, style 등
- **sprint**: sprint0, sprint1, ...
- **JIRA-KEY**: 예) LC-109, LC-191 등
- **기능 요약**: 핵심 변경 내용
- **담당자**: 실명 또는 닉네임

### 📌 예시

```
feat    | sprint1 | LC-109  | 회원가입 API 구현        | SUNGMIN
fix     | sprint1 | LC-191  | 로그인 비밀번호 오류 수정 | SUNGMIN
config  | sprint1 | LC-000  | Redis 설정 추가           | SUNGMIN
docs    | sprint1 | LC-999  | README 작성               | SUNGMIN
```

---

## 🌐 주요 URL

| 유형     | URL                              |
|----------|-----------------------------------|
| Swagger  | http://localhost:8080/swagger-ui.html |
| Redis UI | http://localhost:8081 (외부 툴 사용 시) |

---

## 🔒 보안 / 주의사항

- `.env`, `certs/` 등 민감 파일은 **Git 커밋 금지**
- `.gitignore`에 기본 포함되어 있음
- 운영환경 키는 CI/CD or ArgoCD에서 주입

---

## 🧪 테스트

- 테스트 코드는 `src/test/java` 디렉토리에 작성
- JUnit 5 + Mockito 기반 테스트
- `@SpringBootTest`, `@WebFluxTest`, `@DataJpaTest` 등 사용

---

## 🚧 기타 운영 참고

- Jenkins + ArgoCD로 배포 연동
- `Jenkinsfile`, `Dockerfile`, `helm/` 디렉터리 확인
- 공통 에러 핸들링은 `@RestControllerAdvice` 기준 적용
