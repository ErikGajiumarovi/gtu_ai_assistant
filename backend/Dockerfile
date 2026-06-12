FROM gradle:8.14.3-jdk21 AS builder

WORKDIR /workspace

COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY backend ./backend
COPY shared ./shared
COPY frontend ./frontend

RUN gradle :backend:app:installDist --no-daemon

FROM eclipse-temurin:21-jre AS runtime

WORKDIR /opt/gtu-ai-assistant

RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng tesseract-ocr-rus \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /workspace/backend/app/build/install/app ./app

ENV APP_HOST=0.0.0.0
ENV APP_PORT=8080
ENV APP_AI_MODE=openai
ENV APP_AI_MODEL=gemma3:4b
ENV APP_PERSISTENCE_MODE=postgres

EXPOSE 8080

CMD ["./app/bin/app"]
