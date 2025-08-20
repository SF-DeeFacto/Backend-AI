# ========================================
# 빌드 단계 (Build Stage)
# ========================================
FROM gradle:8.4-jdk17 AS build

# 작업 디렉토리 설정
WORKDIR /app

# Gradle 설정 파일과 빌드 스크립트 복사
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew ./

# 의존성 다운로드 및 캐싱
# 소스 코드 복사 전에 실행하여 캐싱을 효율적으로 활용합니다.
RUN gradle dependencies --no-daemon

# 소스 코드 전체 복사
COPY src ./src

# 애플리케이션 빌드
# 테스트를 건너뛰고 실행
RUN gradle build -x test --no-daemon

# ========================================
# 런타임 단계 (Runtime Stage)
# ========================================
FROM eclipse-temurin:17-jre-alpine
LABEL maintainer="deefacto-AI"
LABEL description="AI service for Deefacto Platform"

WORKDIR /app

RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

COPY --from=build /app/build/libs/*.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8085/actuator/health || exit 1

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "app.jar"]