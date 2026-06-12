# syntax=docker/dockerfile:1.7

FROM gradle:8.14.3-jdk21 AS builder

WORKDIR /workspace

COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY backend/app/build.gradle.kts ./backend/app/build.gradle.kts
COPY backend/application/build.gradle.kts ./backend/application/build.gradle.kts
COPY backend/domain/build.gradle.kts ./backend/domain/build.gradle.kts
COPY backend/infrastructure/build.gradle.kts ./backend/infrastructure/build.gradle.kts
COPY backend/presentation/build.gradle.kts ./backend/presentation/build.gradle.kts
COPY shared/api-models/build.gradle.kts ./shared/api-models/build.gradle.kts

RUN --mount=type=cache,target=/home/gradle/.gradle,sharing=locked \
    --mount=type=cache,target=/workspace/.gradle,sharing=locked \
    gradle :backend:app:dependencies --configuration runtimeClasspath --no-daemon

COPY backend ./backend
COPY shared ./shared

RUN --mount=type=cache,target=/home/gradle/.gradle,sharing=locked \
    --mount=type=cache,target=/workspace/.gradle,sharing=locked \
    gradle :backend:app:installDist --no-daemon

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
