FROM gradle:8.14.3-jdk21 AS builder

WORKDIR /workspace

COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY backend ./backend

RUN gradle :backend:app:installDist --no-daemon

FROM eclipse-temurin:21-jre AS runtime

WORKDIR /opt/gtu-ai-assistant

COPY --from=builder /workspace/backend/app/build/install/app ./app

ENV APP_HOST=0.0.0.0
ENV APP_PORT=8080
ENV APP_AI_MODE=openai
ENV APP_AI_MODEL=gpt-oss:20b
ENV APP_PERSISTENCE_MODE=postgres

EXPOSE 8080

CMD ["./app/bin/app"]
