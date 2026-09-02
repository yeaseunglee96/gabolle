# 멀티스테이지 빌드 — `docker build .` 한 번으로 jar 빌드부터 실행 이미지까지 만든다.
# (Jenkins처럼 그 자체가 컨테이너인 곳에서 돌릴 때는, 컨테이너 밖에서 먼저 jar를
#  만들어 마운트하는 방식이 호스트 경로 불일치로 깨진다 — 그래서 빌드까지 안에서 한다.)

FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre
COPY --from=build /app/build/libs/*.jar /app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "/app.jar"]
