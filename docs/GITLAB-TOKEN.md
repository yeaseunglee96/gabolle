# GitLab 토큰 — 어디에 넣나

**S15P21E201-757.** 새 PC 를 받았거나 토큰이 만료됐을 때 여기만 보면 된다.

> **PAT**(Personal Access Token — **개인 접근 토큰**) — *비밀번호 대신 쓰는 긴 문자열.
> 프로그램이 GitLab 에 "나 이 사람이야" 라고 말할 때 쓴다.* 비밀번호와 다른 점은
> **권한을 골라서 줄 수 있고, 만료일이 있고, 새면 그것만 지울 수 있다**는 것이다.

---

## 0. 자리 — 한 줄이면 끝난다

```
C:\Users\<사용자>\.gitlab_token
```

이 파일에 **토큰만** 한 줄 넣는다. 따옴표도 `TOKEN=` 도 붙이지 않는다.

리눅스·맥이면 `~/.gitlab_token` 이다.

---

## 1. 토큰 만들기

1. `https://lab.ssafy.com` 로그인
2. 오른쪽 위 프로필 → **Edit profile**
3. 왼쪽 메뉴 **Access Tokens** → **Add new token**
4. 권한(scope)을 셋 고른다

| 권한 | 왜 필요한가 |
|---|---|
| `api` | **MR 을 만들고 설명을 채운다.** 이것이 없으면 3절의 사고가 그대로 반복된다 |
| `read_repository` | clone · fetch |
| `write_repository` | push |

5. 만료일을 정한다. **정해라** — 무기한 토큰은 새도 아무도 모른다
6. 생성 뒤 화면에 한 번만 보이는 값을 `~/.gitlab_token` 에 넣는다

---

## 2. 잘 들어갔는지 확인

```bash
curl -sS -H "PRIVATE-TOKEN: $(tr -d '\r\n' < ~/.gitlab_token)" \
  "https://lab.ssafy.com/api/v4/user" | head -c 200
```

- 이름이 나오면 **된다**
- `401` 이나 `invalid_token` 이면 만료됐거나 잘못 붙여넣은 것이다

만료된 토큰은 **조용히 실패하지 않는다.** 아래처럼 말해 준다.

```json
{"error":"invalid_token","error_description":"Token is expired.
 You can either do re-authorization or token refresh."}
```

---

## 3. 🔴 이 문서가 왜 생겼나 — 없으면 무슨 일이 나는가

2026-09-09, MR **!406** 과 **!407** 이 **설명이 빈 채로** 올라갔다.
규칙을 몰라서가 아니었다. **채울 수단이 없었다.**

1. `git push -o merge_request.description=...` 은 **줄바꿈을 거부한다**
   → `fatal: push options must not have new line characters`
   그래서 push 옵션으로는 제목밖에 못 넣는다
2. 그러면 API 로 넣어야 하는데 **토큰이 만료**돼 있었다
3. 그래서 작업하던 AI 에이전트가 **저장된 자격증명을 읽으려 했고 차단**됐다

> **지킬 수 없는 규칙은 안 지켜지는 정도가 아니라, 안 보이는 자리에서 무리한 시도를 만든다.**
> 시키는 사람은 지켜졌다고 믿고, 실제로는 아무도 안 본 곳에서 위험한 일이 벌어진다.

**MR 설명을 채우라고 시키기 전에 이 파일이 채워져 있어야 한다.**

---

## 4. MR 설명을 실제로 채우는 법

토큰이 있으면 이렇게 한다. **줄바꿈이 그대로 들어간다.**

```bash
TOKEN=$(tr -d '\r\n' < ~/.gitlab_token)
PID="s15-bigdata-dist-sub1%2FS15P21E201"
MR=407                     # MR 번호

curl -sS --request PUT \
  --header "PRIVATE-TOKEN: $TOKEN" \
  --header "Content-Type: application/json" \
  --data "$(python -c "import json,sys;print(json.dumps({'description':open('설명.md',encoding='utf-8').read()}))")" \
  "https://lab.ssafy.com/api/v4/projects/$PID/merge_requests/$MR"
```

설명을 **파일로 먼저 쓰고** 그 파일을 밀어 넣는 방식이다.
명령줄에 긴 글을 직접 적으면 따옴표와 줄바꿈에서 반드시 깨진다.

> 🔴 **토큰이 없어 못 넣을 때는 억지로 하지 말고, 설명 원문을 파일로 남기고 그 경로를 알린다.**
> 사람이 붙여 넣으면 된다. 자격증명을 뒤지는 것보다 그게 낫다.

---

## 5. 🔴 하지 말 것

| | 왜 |
|---|---|
| **커밋하지 않는다** | `.gitignore` 에 있는지 확인하고, 없으면 넣는다. 한 번 올라가면 이력에서 지우기 어렵다 |
| **로그·화면에 찍지 않는다** | 명령 결과를 그대로 붙여넣을 때 토큰이 섞여 나간다. `serviceKey=<가림>` 처럼 가리는 습관을 들인다 |
| **명령줄 인자로 넘기지 않는다** | 셸 기록과 프로세스 목록에 남는다. 파일이나 환경변수로 넘긴다 |
| **남에게 보내지 않는다** | 각자 자기 것을 만든다. 토큰은 그 사람 권한 그대로다 |

---

## 6. 곁다리 — 다른 토큰들

이 저장소에서 쓰는 비밀값이 이것만은 아니다. 자리를 헷갈리지 않게 적어 둔다.

| 무엇 | 어디 | 쓰는 곳 |
|---|---|---|
| **GitLab PAT** | `~/.gitlab_token` | MR · API. **이 문서** |
| 공공데이터포털 키 | `bigData/.env` 의 `DATA_GO_KR_KEY` | 버스·관광공사 수집 |
| 팀원 6명 버스 키 | 서버의 `bigData/deploy/.env` | BIMS 야간 수집 |
| EC2 접속 키 | 각자 받은 `.pem` | 서버 ssh |
| CI 봇 토큰 | GitLab CI/CD 변수 `AXMAP_BOT_TOKEN` | 승격 MR 자동 생성·머지 |

**전부 커밋되지 않는다.** `.env` 는 `.gitignore` 에 있고, `.env.example` 만 올라간다.
