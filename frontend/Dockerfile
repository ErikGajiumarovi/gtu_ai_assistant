FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY shared/ shared/
COPY backend/ backend/
COPY frontend/ frontend/

RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN ./gradlew :frontend:jsBrowserDistribution --no-daemon && find /app/frontend/dist -maxdepth 2 -type f | head -20

FROM nginx:1.29-alpine AS runtime

COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/frontend/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
