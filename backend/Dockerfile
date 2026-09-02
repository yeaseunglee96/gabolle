# Jenkins가 먼저 `./gradlew bootJar`로 jar를 만든 뒤, 이 Dockerfile은 그 결과물만 담는다.
FROM eclipse-temurin:17-jre

ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "/app.jar"]
