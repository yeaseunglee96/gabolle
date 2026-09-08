# 멀티스테이지 빌드: jar 빌드와 실행 이미지를 분리한다.
FROM eclipse-temurin:17-jdk AS build

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre

RUN useradd --system --create-home --uid 10001 gabolle
WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar /app/gabolle-backend.jar

USER gabolle
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Seoul"
ENTRYPOINT ["java", "-jar", "/app/gabolle-backend.jar"]
