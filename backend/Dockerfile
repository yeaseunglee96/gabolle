# 멀티스테이지 빌드: jar 빌드와 실행 이미지를 분리한다.
FROM eclipse-temurin:17-jdk AS build

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
RUN ./gradlew bootJar --no-daemon

# ── 경로 최적화(OR-Tools) 전용 파이썬 스테이지 (S15P21E201-161) ────────────────
#
# 🔴 실행 이미지(eclipse-temurin:17-jre, Ubuntu 26.04)의 apt 가 주는 python3 는 3.14 다.
#    ortools==9.14.6206 는 PyPI 에 cp313 까지만 휠을 낸다(2026-09-08 확인) — 3.14 에는
#    설치할 방법이 없다(소스 빌드는 ortools 가 거대한 C++ 코드라 CI/배포에서 비현실적).
#    그래서 공식 python:3.13-slim(Debian) 스테이지에서 따로 만들어 통째로 복사한다.
#    glibc 는 뒤로 호환된다(오래된 바이너리가 더 새 glibc 에서 돈다) — Debian
#    trixie(13)보다 Ubuntu 26.04 쪽이 더 새것이라 이 방향은 안전하다. 로컬에서 실제로
#    빌드해 이 스테이지 → 실행 이미지로 복사한 인터프리터로 import 부터 실제 풀이까지
#    확인했다(비-root(uid 10001)로도 확인 — 실행 이미지의 gabolle 유저와 같은 조건).
FROM python:3.13-slim AS pyoptimizer
COPY solver/requirements-optimizer.txt /tmp/requirements-optimizer.txt
RUN pip install --no-cache-dir --root-user-action=ignore -r /tmp/requirements-optimizer.txt

FROM eclipse-temurin:17-jre

RUN useradd --system --create-home --uid 10001 gabolle
WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar /app/gabolle-backend.jar
COPY --from=pyoptimizer /usr/local /usr/local/python3.13
COPY solver ./solver

USER gabolle
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Seoul"
ENV GABOLLE_ROUTE_OPTIMIZER_PYTHON="/usr/local/python3.13/bin/python3.13"
ENV GABOLLE_ROUTE_OPTIMIZER_SCRIPT="/app/solver/route_optimizer.py"
ENTRYPOINT ["java", "-jar", "/app/gabolle-backend.jar"]
