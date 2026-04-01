# 빌드 스테이지
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn clean package -DskipTests

# 실행 스테이지
FROM eclipse-temurin:21-jre
WORKDIR /app

# 빌드된 New Relic Agent 복사
COPY --from=builder /app/target/newrelic /opt/newrelic

# 애플리케이션 JAR 복사
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

# New Relic Agent 활성화
ENV JAVA_TOOL_OPTIONS="-javaagent:/opt/newrelic/newrelic.jar"
CMD ["java", "-jar", "app.jar"]
