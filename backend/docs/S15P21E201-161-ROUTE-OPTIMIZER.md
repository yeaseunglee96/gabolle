# 경로 최적화(OR-Tools) 실행 환경 — S15P21E201-161

하루 동선 순서를 정하는 프로그램은 자바가 아니라 파이썬이다 —
`backend/solver/route_optimizer.py`, [Google OR-Tools](https://developers.google.com/optimization)
(**제약·경로 최적화용 오픈소스 라이브러리**) 기반. 서버는 이 파일을 서브프로세스로 불러
표준입력에 JSON 을 주고 표준출력에서 JSON 을 받는다
(`com.gabolle.backend.optimizer.RouteOptimizerAvailabilityCheck`).

이 프로그램이 없어도(또는 ortools 가 안 깔려도) 서버는 컴파일·기동 실패 없이 뜬다 — 실제
요청이 올 때 서브프로세스 실행이 실패하면 그 순간 조용히 대체 계산(직선거리 등)으로
넘어간다. **조용히 안 도는 것이 이 티켓에서 가장 위험한 상태다** — 그래서 기동 시점에
한 번 시험 삼아 불러 보고, 안 되면 경고를 남긴다(서버는 그래도 뜬다).

## 왜 python3.13 인가 — 3.14 로는 안 된다

`ortools==9.14.6206` 은 PyPI 에 **cp313(파이썬 3.13)까지만** 휠(**미리 컴파일해 둔 설치
파일**)을 낸다(2026-09-08 확인). 이 저장소가 쓰는 실행 이미지·CI 이미지
(`eclipse-temurin:17-jre`/`-jdk`, Ubuntu 26.04)의 apt 가 기본으로 주는 `python3` 는
**3.14** 다 — 여기서는 그냥 `apt install python3.13` 이 안 된다(그 패키지 자체가 없다).
소스 빌드도 현실적이지 않다 — ortools 는 거대한 C++ 코드다.

그래서 배포·CI 가 각자 다른 방법으로 3.13 을 따로 구한다:

| | 방법 | 왜 |
|---|---|---|
| 배포 이미지(`Dockerfile`) | 공식 `python:3.13-slim`(Debian) 스테이지에서 만들어 `/usr/local` 을 통째로 실행 이미지에 복사 | 도커 멀티스테이지 빌드는 서로 다른 베이스 이미지 사이를 자유롭게 복사할 수 있다. glibc 는 뒤로 호환되므로(오래된 바이너리가 더 새 glibc 에서 돈다) Debian → 더 새로운 Ubuntu 방향 복사는 안전하다 |
| CI(`.gitlab-ci.yml` 의 `backend:build`) | [uv](https://docs.astral.sh/uv/)(파이썬 인터프리터 자체를 내려받아 까는 도구, root·apt 필요 없음)로 3.13 을 직접 설치 | 이 러너에는 도커 소켓이 없어(Testcontainers 도 같은 이유로 못 쓴다 — `S3FileStorageTest` 참고) 배포 이미지처럼 다른 스테이지에서 복사해 오는 방법을 못 쓴다 |

두 경로 모두 로컬에서 실제로 빌드·실행해 `import ortools` 부터 실제 경로 풀이까지
확인했다(2026-09-08).

## 확인하는 법

### 로컬 개발 PC

```bash
cd backend
python3 -m venv .venv-optimizer   # 아무 3.9~3.13 인터프리터면 된다
.venv-optimizer/bin/pip install -r solver/requirements-optimizer.txt
echo '{"places": []}' | .venv-optimizer/bin/python3 solver/route_optimizer.py
# → {"status": "OPTIMAL", "orderedPlaceIds": [], "arrivals": []}
```

그다음 그 인터프리터를 가리키고 테스트를 돌린다:

```bash
GABOLLE_ROUTE_OPTIMIZER_PYTHON=$(pwd)/.venv-optimizer/bin/python3 \
  ./gradlew test --tests "com.gabolle.backend.optimizer.RouteOptimizerAvailabilityCheckTest"
```

환경변수를 안 주면 `python3`(PATH 의 기본 파이썬)를 쓴다 — 거기 ortools 가 없으면 이
테스트는 **그대로 빨갛게 실패한다.** 의도된 동작이다 — 로컬에서도 이 환경은 갖춰야 한다는
것이 이 테스트의 주장이다(`RouteOptimizerAvailabilityCheckTest` 클래스 주석 참고).

### CI

`backend:build` 잡이 매번 uv 로 3.13 을 받고 `GABOLLE_ROUTE_OPTIMIZER_PYTHON` 을 그
경로로 맞춰 둔다 — 이 문서를 손대지 않아도 자동으로 확인된다. 이 잡은 건너뛴 테스트가
하나라도 있으면 빌드를 실패시키므로(S15P21E201-266 후속), ortools 설치 단계를 일부러
지우면 이 테스트가 CI 를 빨갛게 만든다 — 실측 확인함(2026-09-08).

### 배포 환경

컨테이너 기동 로그에서 확인한다.

```
INFO ... RouteOptimizerAvailabilityCheck : 경로 최적화(OR-Tools) 실행 환경 확인됨 — ...
```

이 줄이 아니라 `WARN ... 경로 최적화(OR-Tools) 실행 환경을 못 찾았다` 가 보이면 서버는
떴지만 최적화는 대체 계산으로 도는 중이다 — `GABOLLE_ROUTE_OPTIMIZER_PYTHON`/
`GABOLLE_ROUTE_OPTIMIZER_SCRIPT` 환경변수와 `Dockerfile` 의 `pyoptimizer` 스테이지가
실제로 이미지에 들어갔는지 확인한다.

## 설정

`RouteOptimizerProperties`(`gabolle.route-optimizer.*`), `application.properties` 참고.

| 프로퍼티 | 환경변수 | 기본값 |
|---|---|---|
| `python-executable` | `GABOLLE_ROUTE_OPTIMIZER_PYTHON` | `python3` |
| `script-path` | `GABOLLE_ROUTE_OPTIMIZER_SCRIPT` | `solver/route_optimizer.py` |
| `timeout-seconds` | `GABOLLE_ROUTE_OPTIMIZER_TIMEOUT_SECONDS` | `5` |

## 참고

- 상세설계서 v2 4장 F-REC-03 필요 기술 표, 기획서 v7 7.4절
- 참고 구현(팀 것이 아니라 초기 리서치용 참고 코드): `ref/local-route/server/solver/route_optimizer.py`
- 상위 스토리: 하루 동선이 지그재그로 낭비되지 않는 일정을 받을 수 있다
- 실제 추천/일정 흐름에 이 최적화 결과를 배선하는 일은 이 티켓 범위 밖이다 — 여기서는
  실행 환경이 CI·배포 양쪽에서 실제로 도는지만 확인한다
