# 빌드 스테이지
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn clean package -DskipTests

# 실행 스테이지
FROM eclipse-temurin:21-jre
WORKDIR /app
# New Relic 에이전트 다운로드
RUN curl -o newrelic.jar https://download.newrelic.com/newrelic/java-agent/newrelic-agent/current/newrelic.jar

# 환경변수 설정
ENV NEW_RELIC_LICENSE_KEY=${NEW_RELIC_LICENSE_KEY}
ENV NEW_RELIC_APP_NAME=${NEW_RELIC_APP_NAME}
ENV NEW_RELIC_ENVIRONMENT=${NEW_RELIC_ENVIRONMENT}

COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
CMD ["java", "-javaagent:/app/newrelic.jar", "-jar", "app.jar"]
