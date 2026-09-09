# Git 협업 규칙

## 1. 브랜치 구조

```text
main
├─ front/main
│  └─ front/dev
│     └─ feat/front/{JIRA-KEY}-{short-description}
├─ back/main
│  └─ back/dev
│     └─ feat/back/{JIRA-KEY}-{short-description}
├─ bigData/main
│  └─ bigData/dev
│     └─ feat/bigData/{JIRA-KEY}-{short-description}
└─ common/dev
   └─ chore/common/{JIRA-KEY}-{short-description}
```

> 🔴 **정정 (2026-09-03) — 이 절은 `feature`로 적혀 있었는데, 실제로 도는 것은
> `feat`이다.** `docs/LOCAL_ROUTE_실행계획_v7.md` 10.2절이 이 어긋남을 먼저
> 실측했고, 루트 `CONTRIBUTING.md`(이 문서보다 위)가 `feat`를 쓰므로 이쪽을
> 고친다. 낡은 실측은 지우지 않고 정정 날짜를 남긴다.
>
> `common`은 `common/main`이 없다 — 버전 축과 파트 사다리에서 뺐다
> (실행계획_v7 3.3절, 2026-08-27 결정). `common/dev`가 `main`으로 바로 간다.

- `main`: FE와 BE가 통합되어 배포 가능한 상태만 유지한다.
- `front/main`, `back/main`: 각 파트의 배포 후보를 관리한다.
- `front/dev`, `back/dev`: 각 파트의 기능을 통합하고 검증한다.
- 작업 브랜치: 반드시 해당 파트의 `dev`에서 생성하고 해당 `dev`로 MR을 보낸다.

파트 브랜치가 코드 폴더 역할을 대신하지 않도록 각 브랜치에는 저장소 전체가 존재한다. 작업 범위만 `frontend/`, `backend/`로 제한한다.

## 2. 작업 브랜치 이름

```text
{type}/{scope}/{JIRA-KEY}-{short-description}
```

예시:

```text
feat/front/S15P21E201-123-search-page
feat/back/S15P21E201-124-search-api
feat/bigData/S15P21E201-9-bigdata-bootstrap
fix/front/S15P21E201-125-date-picker
refactor/back/S15P21E201-126-recommendation-service
chore/common/S15P21E201-127-project-settings
chore/front/S15P21E201-128-nginx-config
chore/back/S15P21E201-129-jenkins-pipeline
```

`type`은 아래 값만 사용한다.

- `feat`: 기능 추가
- `fix`: 버그 수정
- `refactor`: 동작 변경 없는 구조 개선
- `test`: 테스트 추가 또는 수정
- `docs`: 문서 변경
- `chore`: 빌드, 설정, 의존성, 인프라 작업
- `hotfix`: 운영 중인 `main`의 긴급 수정

브랜치의 설명은 소문자 영문과 하이픈으로 짧게 작성한다.

## 3. 작업 흐름

### 기능 개발

1. 담당 파트의 `dev`를 최신화한다.
2. Jira Task를 `진행 중`으로 변경한다.
3. 작업 브랜치를 생성한다.
4. 구현과 검증 후 담당 파트의 `dev`로 MR을 생성한다.
5. CI 성공과 1명 이상의 승인을 받은 뒤 squash merge한다.
6. 병합된 작업 브랜치는 삭제하고 Jira Task를 `완료`로 변경한다.

```bash
git switch front/dev
git pull --ff-only origin front/dev
git switch -c feat/front/S15P21E201-123-search-page
```

### 파트 안정화와 배포

```text
feat/front/*   → front/dev   (FE CI/CD)   → front/main   ┐
feat/back/*    → back/dev    (BE CI/CD)   → back/main    ├→ main
feat/bigData/* → bigData/dev (Data CI/CD) → bigData/main ┘
chore/common/* → common/dev ─────────────────────────────┘
```

- `dev → 파트 main`: 파트 단위 테스트가 통과한 배포 후보만 병합한다.
- `파트 main → main`: FE·BE 연동 확인 후 배포 MR을 생성한다.
- `main` 배포 시 `v0.1.0` 형식으로 태그한다.
- `front/dev`, `back/dev`를 기준으로 각 파트의 CI/CD를 실행한다.
- FE 빌드·Nginx 설정은 `frontend/`, BE 빌드·Jenkins 설정은 `backend/`에서 관리한다.
- 공용 문서와 루트 설정은 `chore/common/*`에서 작업하고, 병합 후 변경을 각 파트 브랜치에 동기화한다.
- `hotfix/*`는 `main`에서 생성한다. 수정 후 `main`에 병합하고 관련 파트의 `main`, `dev`에도 반영한다.

## 4. 커밋 메시지

```text
[JIRA-KEY] type(scope): summary
```

예시:

```text
[S15P21E201-123] feat(front): 여행 검색 화면 추가
[S15P21E201-124] feat(back): 여행지 검색 API 추가
[S15P21E201-125] fix(front): 날짜 선택 범위 오류 수정
[S15P21E201-128] chore(front): Nginx 프록시 설정 추가
[S15P21E201-129] chore(back): Jenkins 파이프라인 설정 추가
```

- `type`: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`
- `scope`: `front`, `back`, `common`
- 한 커밋에는 하나의 논리적인 변경만 담는다.
- 파일을 확인하지 않은 채 `git add .`로 전부 올리지 않는다.
- 공유한 작업 브랜치와 보호 브랜치에는 force push하지 않는다.

## 5. MR 규칙

- MR 제목은 커밋과 같은 형식을 사용한다.
- MR 하나는 원칙적으로 Jira Task 하나만 처리한다.
- 작성자는 본인의 MR을 승인하지 않는다.
- 기능 MR은 squash merge하고 병합된 브랜치는 삭제한다.
- 리뷰가 시작된 뒤에는 이력을 강제로 다시 쓰지 않는다.

### 승인 인원 — 사람 수를 방아쇠로 삼는다

| 단계 | 조건 | 파트 `dev`로 가는 MR |
| --- | --- | --- |
| **지금** | `governance/policy.json`의 투표권자가 **5명 미만** | **승인 1명** |
| **전환** | 투표권자가 **5명 이상**이 되는 날 | **승인 2명** |

날짜가 아니라 조건이 방아쇠다 — 저절로 전환된다. 사람 수는 여기 숫자로 박지 않는다.
`governance/policy.json`의 `voters`를 세는 것이 언제나 맞는 답이다 (숫자를 문서에
적으면 명단이 늘 때마다 그 줄이 낡는다).

이 승인 수는 **일상 MR**(파트 `dev`로 가는 것)에만 걸린다. 최상위 `main`으로 가는
MR과 `governance`·`.gitlab-ci.yml`·`ci` 경로를 바꾸는 MR은 별도로 **정족수**
(`governance` 잡, `governance/policy.json`이 세는 표)가 걸리고, 지금 값은 2표다.
정족수를 바꾸려면 `governance/policy.json`을 고치는 MR 자체가 2표를 받아야 한다.
리뷰 승인과 정족수는 막는 지점이 다르다 — 둘은 서로를 대체하지 않는다.

### 승인 코멘트 형식

승인 버튼만 누르면 "통과됨"이라는 사실만 남고, 무엇을 보고 승인했는지는 사라진다.
승인 코멘트에 한 줄을 남긴다.

```
승인 — 무엇을 보고 승인하는가 / 무엇을 안 봤는가
```

```
승인 — 일정 생성 API 의 시간창 제약이 기존 견본과 같은 결과를 내는지 확인.
      성능은 안 봤음 (동시 요청 미검증)

승인 — 화면만 확인. 서버 계약은 백엔드 담당이 봐야 함
```

"무엇을 안 봤는가"가 더 중요하다 — 두 명이 승인했는데 둘 다 화면만 봤다면 서버는
아무도 안 본 것이다. 그 사실이 적혀 있어야 다음 사람이 안다.

## 6. 보호 브랜치

아래는 직접 push, force push, 삭제를 금지하고 MR로만 변경한다.

- `main`
- `*/main` — 모든 파트의 파트 브랜치 (`front/main`, `back/main`, `bigData/main`, …)
- `*/dev` — 모든 파트의 dev 브랜치 (`front/dev`, `back/dev`, `bigData/dev`, `common/dev`, …)

> 🔴 **정정 (2026-09-03)** — 예전엔 `front`·`back`만 나열돼 있었다. 파트가 늘 때마다
> 이 목록을 손으로 갱신해야 하는 것이 문제라, GitLab 설정 자체를 와일드카드
> (`*/dev`, `*/main`)로 바꿨다 — 실측: `GET /projects/:id/protected_branches`.
> 새 파트가 생겨도 이 절을 고칠 필요가 없다.

`main`은 배포 담당자 또는 Maintainer만 병합한다. 파트 브랜치는 해당 파트 구성원이 병합할 수 있으나 본인 MR에는 다른 팀원의 승인이 필요하다.

## 7. 빈 저장소 최초 설정

빈 원격 저장소에는 MR의 대상 브랜치가 없으므로 최초 설정에 한해 다음 순서로 진행한다.

1. Jira에 프로젝트 초기화 Task를 생성한다.
2. 로컬 `main`에 Jira 키가 포함된 최초 커밋을 만든다.
3. 원격 `main`에 최초 1회 push한다.
4. `front/main`, `front/dev`, `back/main`, `back/dev`를 `main` 기준으로 생성한다.
5. 다섯 브랜치를 보호하고 이후 직접 push를 금지한다.

최초 설정 이후에는 이 예외를 다시 적용하지 않는다.
