# 처음 온 사람에게 — clone 부터 첫 작업까지

읽는 데 5분, 따라 하는 데 3분이면 된다.
**막히면 그 자리에서 답을 얻을 수 있게** 만들어 두었으니 물어보러 가지 않아도 된다.

---

## 0. 이게 뭔데?

우리는 여행 서비스를 만든다. 그런데 여섯 명이 각자 AI 도구까지 켜고 붙으면
**같은 파일을 동시에 고치는 일**이 반드시 생긴다. git 은 그걸 나중에(머지할 때)
알려주는데, 그때는 이미 두 사람이 몇 시간씩 쓴 뒤다.

그래서 **axMap** 이라는 작은 도구를 같이 넣어 뒀다. 하는 일은 하나다.

> **파일을 고치기 전에 "나 이거 건드린다" 를 먼저 선언하게 만든다.**
> 남이 이미 잡고 있으면 그 자리에서 알려준다 — 누가, 무엇을, 왜, 언제까지.

코드 충돌이 나기 전에 **의도 충돌**을 먼저 터뜨리는 것이다.
당신이 할 일은 거의 없다. AI 도구를 쓰면 도구가 알아서 부른다.

> **이 저장소에 있는 것은 그 도구의 사본이다.** axMap 본체는 별도 저장소
> (`https://lab.ssafy.com/rleaderjoon/axmap`)에 있고, 여기 `ci/axmap/` 에는 팀이
> 실제로 부르는 파일만 복사해 뒀다 — **벤더링**(vendoring — 남의 코드를 내 저장소
> 안에 복사해 두고 그 사본으로 돌리는 것). 쓰는 데는 아무 차이가 없다.
> **다만 `ci/axmap/` 아래를 손으로 고치면 안 된다** — 왜인지는
> [../CONTRIBUTING.md](../CONTRIBUTING.md) 0.3 절에 있다.

---

## 1. 설치 — 명령 두 줄

```bash
git clone https://lab.ssafy.com/s15-bigdata-dist-sub1/S15P21E201.git
cd S15P21E201
bash setup.sh
```

Windows PowerShell 이면:

```powershell
powershell -ExecutionPolicy Bypass -File .\setup.ps1
```

필요한 것은 **Node 20 이상**과 **git** 뿐이다. 설치되는 패키지는 없다.

### 이름을 안 물어본다

장부(누가 뭘 잡고 있는지 적는 곳)에서 당신을 가리킬 이름이 필요한데,
`git config user.name` 을 그대로 쓴다. 이미 사람마다 다르니 새로 물을 게 없다.

혹시 설정이 안 돼 있으면 setup 이 **거기서 멈춘다.** 기본 이름을 대신 채우지 않는다 —
여러 사람이 같은 이름이 되면 서로의 선점을 못 보고 같은 파일을 조용히 함께 고치게 된다.

```bash
git config --global user.name "홍길동"
git config --global user.email "you@example.com"
```

---

## 2. 스스로 확인 — `doctor`

**남한테 "이거 된 거 맞아요?" 라고 물어보지 않아도 된다.**

```bash
npx -y axmap-cli@latest doctor
```

```
  OK  node       v20.11.0
  OK  저장소        /home/you/S15P21E201
  OK  내 이름       홍길동  (git config user.name)
  OK  장부         .axmap/ledger
  OK  장부 원격      origin  (git config axmap.remote)
  OK  원격 연결      닿습니다
  OK  커밋 훅       심겨 있습니다
  OK  MCP 설정     ./ci/axmap/mcp/server.mjs  (AI 도구가 승인만 하면 붙습니다)
  OK  선점 판정      지금 유효한 claim 이 있습니다

전부 정상입니다. 파일을 고치기 전에 claim 하는 것만 지키면 됩니다.
```

`!!` 가 있으면 그 줄에 **고치는 방법이 함께 적혀 있다.** `~~` 는 알아둘 것이지 오류는 아니다.

> 🔴 **`커밋 훅` 줄에 `!!` 가 나오고 "가리키는 파일이 없습니다" 라고 하면** — 이 저장소를
> 2026-08-26 이전부터 쓰고 있었다는 뜻이다. 그날 axMap 이 `axmap/` 에서 `ci/axmap/` 으로
> 자리를 옮겼는데, 훅은 옛 경로를 절대 경로로 박아 두고 있다. 한 줄로 고친다.
>
> ```bash
> npx -y axmap-cli@latest hook install
> ```
>
> **그냥 두면 안 된다.** 훅이 못 돌면 claim 없이 고친 파일도 그대로 커밋되고,
> 그 사실을 MR 이 빨개진 뒤에야 알게 된다.

> **왜 이런 게 따로 있나** — 이 도구의 실패는 대부분 **조용하다.** 원격에 못 닿거나,
> 훅이 안 심겼거나 하면 선점은 성공한 것처럼 보이는데 실제로는 아무도 못 본다.
> 오류가 안 나는 고장이라 확인할 방법이 따로 있어야 한다.

---

## 3. AI CLI 를 켠다

### 먼저 — axMap 을 이 PC 에 한 번 설치한다

**저장소에는 MCP 설정이 없다.** 각자 한 번 돌린다 (어느 폴더에서 돌려도 된다).

```bash
npm i -g axmap-cli
axmap setup
```

`setup` 이 이 PC 에 있는 AI CLI 를 찾아 **각자의 홈 설정**에 `axmap` 을 등록한다 —
Claude Code 는 `~/.claude.json`, Codex 는 `~/.codex/config.toml`,
Antigravity 는 `~/.gemini/config/mcp_config.json`. 저장소에는 아무것도 안 남는다.

> 🔴 **2026-08-31 이전에는 저장소의 `.mcp.json` 으로 Claude Code 만 clone 으로
> 붙었다.** 그 파일을 뺐다 (S15P21E201-509). 같은 이름 `axmap` 이 저장소와 홈
> 두 곳에 잡혀 `claude mcp list` 가 `Conflicting scopes` 로 경고했고, **저장소 쪽이
> 홈을 이겨서** npm 판을 깔아도 이 저장소에서는 안 쓰였기 때문이다.

### 그 다음 — 이 폴더에서 켠다

```bash
claude
```

**`axmap` 도구를 신뢰할지 물어보면 "예".** 승인 한 번이면 AI 가 도구를 쓸 수 있게 된다.

> **Codex(`codex`) 나 Antigravity(`agy`) 를 쓴다면 [3.5 절](#35-claude-code-가-아닌-ai-cli-를-쓴다면)로 간다.**
> 붙기는 똑같이 붙는데, 설정 파일과 부르는 방법이 다르다.

> **MCP** — AI 도구가 외부 프로그램을 "도구" 로 부를 수 있게 해주는 규격.
> 여기서는 AI 가 우리 선점 장치를 직접 부를 수 있게 해준다.
> 사람이 명령을 대신 쳐 줄 필요가 없다는 뜻이다.

### 붙었는지 확인

AI 에게 이렇게 쳐 본다.

```
ax_brief 로 이 저장소를 파악하고, ax_status 로 지금 누가 뭘 잡고 있는지 봐줘.
그 다음 내가 건드릴 경로를 ax_claim 해줘. 할 일은 여행 상세 화면 만들기야.
```

AI 가 이 순서로 움직이면 정상이다.

1. `ax_brief` — 이 저장소가 뭘 만드는 곳인지, 이미 정해진 게 뭔지 읽는다
2. `ax_status` — 지금 누가 어디를 잡고 있나 본다
3. `ax_claim` — 건드릴 파일을 선언한다

도구가 안 보이면 `/mcp` 를 쳐서 `axmap` 이 연결됐는지 확인한다.

> 🟢 **쪽지(`ax_inbox` · `ax_send`)는 이제 된다** (2026-08-26 정정).
> 고아 브랜치 `axmap/bus` 로 **즉시** 오간다 — 커밋도 MR 도 claim 도 필요 없다.
> 나에게 온 쪽지가 있으면 **아무 도구를 불러도** 결과 끝에 뜬다.
>
> 이 자리에는 원래 *"쪽지 도구는 안 된다"* 고 적혀 있었다. 그 문장이 버그보다
> 오래 살았다 — 에이전트가 이 문서를 먼저 읽고 **시도조차 하지 않아서**, 실제로
> 온 쪽지가 그동안 묻혔다. 낡은 실측은 지우지 말고 정정한 날짜와 함께 남긴다.

---

## 3.5. Claude Code 가 아닌 AI CLI 를 쓴다면

우리 MCP 서버는 **표준 규격**(stdio + 줄바꿈 JSON-RPC 2.0)이라 MCP 를 지원하는
CLI 면 무엇이든 붙는다. 다른 것은 **자동으로 붙느냐**뿐이다.

> 예전에는 저장소에 든 `.mcp.json` 을 알아서 찾아 주는 **Claude Code 만** 그냥
> 붙었다. 지금은 셋이 같다 — `axmap setup` 이 각자의 홈 설정에 넣어 준다.

### 🟢 이제 손으로 안 넣어도 된다 (2026-08-31 정정)

`axmap-cli@0.3.0` 부터 `axmap setup` 이 **세 CLI 를 모두** 각자의 홈 설정에 등록한다.
3절의 두 줄이면 끝난다.

이 자리에는 원래 *"🔴 지금은 손으로 넣어야 한다 (2026-08-26)"* 고 적혀 있었다.
그때는 등록기(`tools/mcp-register.mjs`)가 axMap 저장소로 나가고 팀 사본에는 없어서
`setup` 이 `!!` 경고로 끝났다. 지금은 그 등록기가 **npm 꾸러미 안에 함께 온다.**
낡은 실측은 지우지 말고 **정정한 날짜와 함께** 남긴다 — 다음 사람이 같은 것을
다시 재보지 않게.

아래 손으로 넣는 방법은 **`setup` 이 실패했을 때의 예비책**으로 남긴다.

경로는 **axMap 이 설치된 절대 경로**로 적는다 — `npm root -g` 가 알려 주는 폴더
아래의 `axmap-cli/mcp/server.mjs` 다. 전역 설정 파일이라 상대 경로는 안 통한다.

**Antigravity** — `~/.gemini/config/mcp_config.json`

```json
{
  "mcpServers": {
    "axmap": {
      "command": "node",
      "args": ["<clone 한 절대경로>/ci/axmap/mcp/server.mjs"],
      "env": { "AXMAP_ACTOR": "agent", "AXMAP_TTL": "45m" }
    }
  }
}
```

**Codex** — `~/.codex/config.toml`

```toml
[mcp_servers.axmap]
command = "node"
args = ["<clone 한 절대경로>/ci/axmap/mcp/server.mjs"]
env = { AXMAP_ACTOR = "agent", AXMAP_TTL = "45m" }
```

> 🔴 **이미 있는 파일을 통째로 덮어쓰지 마라.** 다른 MCP 서버가 이미 적혀 있으면
> `axmap` 항목 하나만 더한다.

> `AXMAP_AGENT` 는 **어느 파일에도 적지 않는다** — 바로 아래 절이 그 이유다.

**정해졌다 (2026-08-31)** — 등록 도구는 팀 사본에 넣지 않는다. npm 꾸러미
`axmap-cli` 안에 함께 오고 각자 `axmap setup` 으로 돌린다. 사본에 넣으면 벤더
지문(`ci/axmap/manifest.sha256`)이 어긋나 `ci:vendor` 잡이 빨개지기 때문이다.

### 어디에 무엇이 들어가나

| CLI | 설정 파일 | 확인했나 |
|---|---|---|
| **Claude Code** (`claude`) | `~/.claude.json` (전역) — `axmap setup` 이 넣는다 | ✅ 2026-08-31 실물 확인 |
| **Antigravity** (`agy`) | `~/.gemini/config/mcp_config.json` (전역) | ✅ 이 PC 의 `agy` 1.1.10 으로 확인 |
| **Codex** (`codex`) | `~/.codex/config.toml` (전역) | ⚠️ **확인 못 했다** — 아래 참고 |

**Antigravity 에서 확인한 것** — 전역 파일에 적으면 `ax_*` 도구가 실제로 뜬다.
저장소의 `.mcp.json` 은 **읽지 않았다**(같은 조건에서 도구가 아예 안 나왔다).
그 파일은 2026-08-31 에 저장소에서 빠졌으니 이제 어느 CLI 도 그것으로 붙지 않는다.
공식 문서가 말하는 작업 폴더용 `.agents/mcp_config.json` 도 **CLI 에서는 안 먹었다** —
IDE 쪽 경로로 보인다. 그래서 전역 파일만 쓴다.
서버는 **CLI 를 켠 폴더를 대상으로** 잡으므로, 전역에 한 번만 넣어도 저장소마다 따로
넣을 필요가 없다.

**Codex 에서 확인 못 한 것** — 이 PC 에 `codex` 가 없어서 **실물로 돌려보지 못했다.**
설정 위치(`~/.codex/config.toml`)와 형식(`[mcp_servers.<이름>]`), 그리고
`codex mcp add` 라는 명령이 있다는 것까지는 [공식 문서](https://learn.chatgpt.com/docs/extend/mcp?surface=cli)로
확인했지만 **동작은 못 봤다.** `codex mcp add` 를 써도 되고, 위의 TOML 을 손으로 넣어도
된다. **`codex` 를 쓰는 사람이 한 번 돌려보고 결과를 이 표에 적어 주면 된다.**

### 🔴 `AXMAP_AGENT` 를 설정에 적지 않는다

어느 설정 파일에도 이름을 박지 않는다. 박는 순간 그 값이 **모두의 이름**이 되고,
장부에는 한 명만 존재하게 된다. 겹침 판정은 자기 claim 을 겹침으로 보지 않으므로
그때부터 팀 전원이 서로의 영역을 아무 경고 없이 덮어쓴다. 락이 조용히 여러 명에게
발급된 것이고, 이 도구가 막으려던 사고 그 자체다.

이름은 `git config user.name` 으로 저절로 떨어진다. 그대로 두면 된다.

### 슬래시 명령 대신 — 그대로 쳐서 같은 일을 시키는 문장

**아래 문장을 그대로 치면** AI 가 정해진 순서로 움직인다. 어느 CLI 에서든 통한다.

> 🔴 **2026-08-26 실측 — `/ax`·`/ax-start`·`/ax-done`·`/ax-tell` 슬래시 명령은
> 지금 이 브랜치에 없다.** 원래도 **Claude Code 전용**이라 다른 CLI 로는 옮길 수
> 없었는데(그 CLI 들이 `.claude/commands/` 를 읽지 않는다), 지금은 Claude Code 에서도
> 안 뜬다 — 그 폴더가 이 브랜치에 없다. **아래 문장이 지금 유일한 길이다.**

| 하고 싶은 일 | 이렇게 친다 |
|---|---|
| **일을 시작한다** | `ax_brief 로 이 저장소를 파악하고, ax_status 로 지금 누가 뭘 잡고 있는지 봐줘. 그리고 내가 건드릴 경로만 좁게 골라서 ax_claim 해줘. intent 에는 팀원이 읽을 한 문장을 적어줘. 겹쳐서 거부되면 재시도하지 말고 비어 있는 다른 작업을 제안해줘. 할 일은 — <할 일>` |
| **지금 장부를 본다** | `ax_status 로 지금 장부에 뭐가 있는지 보여주고, 이번 대화에서 내가 건드릴 것 같은 경로를 ax_check 로 확인해서 겹치는 게 있는지 한 문단으로 정리해줘. 겹치면 재시도하지 말고 비어 있는 다른 영역을 제안해줘.` |
| **끝났으니 반납한다** | `ax_status 로 내가 지금 뭘 잡고 있는지 확인하고, 작업이 끝났으면 ax_release 해줘. "아무것도 반납하지 못했다" 고 나오면 그냥 넘어가지 말고 알려줘 — 잡을 때와 이름이 다르다는 뜻이야. 아직 더 걸릴 것 같으면 ax_release 대신 ax_renew 를 불러줘.` |
| **쪽지를 보낸다** | `ax_send 로 <상대> 에게 쪽지를 보내줘. 제목과 본문은 이렇게: … 상대가 알아야 결정이 달라지는 것만 보낸다.` |
| **쪽지를 읽는다** | `ax_inbox 로 나에게 온 쪽지를 보여줘. 읽을 게 있으면 본문까지 열어서 무엇을 해야 하는지 정리해줘.` |

> `ax_brief` · `ax_status` · `ax_claim` 같은 것이 **MCP 도구 이름**이다.
> 사람이 직접 부르는 명령이 아니라 AI 가 부르는 것이고, 위처럼 이름을 대 주면
> AI 가 그걸 골라 쓴다. 무엇이 있는지는 [../CONTRIBUTING.md](../CONTRIBUTING.md) 1절 표에 있고,
> 각 도구의 인자까지 보려면 **axMap 저장소**(`https://lab.ssafy.com/rleaderjoon/axmap`)
> 의 `mcp/README.md` 를 본다.

붙었는지 확인하려면 그냥 이렇게 쳐 본다.

```
ax_status 를 불러서 결과를 그대로 보여줘.
```

숫자든 "비어 있다" 든 **장부 내용이 나오면 붙은 것**이다.
"그런 도구가 없다" 고 하면 3.5 절의 설정을 다시 확인하고 CLI 를 다시 켠다.

---

## 4. 쓰는 법 — 손으로 칠 때

AI 도구를 쓰면 위의 `ax_*` 도구가 알아서 부른다. 사람이 직접 칠 수도 있다.

```bash
npx -y axmap-cli@latest status
npx -y axmap-cli@latest claim FE/src/pages/Trip.tsx --task S15P21E201-144 --intent "여행 상세 화면"
npx -y axmap-cli@latest release
npx -y axmap-cli@latest renew --ttl 30m
```

---

## 5. 거부당하면 — 재시도하지 않는다

```
claim 거부 - 다른 에이전트가 점유 중인 경로가 있습니다.

  x FE/src/pages/Trip.tsx
      점유자 : 김민수
      작업   : 여행 카드 컴포넌트 분리
      시작   : 2026-08-25T09:00:00.000Z
      TTL    : 24분 남음
```

**같은 요청은 몇 번을 보내도 같은 답이 온다.** 기다리며 놀지도 않는다.
메시지에 있는 이름·작업·남은 시간을 보고 **비어 있는 다른 곳**으로 간다.
정 급하면 그 사람에게 직접 말하면 된다 — 그러라고 이름을 적어 두는 것이다.

> **TTL** (Time To Live) — 선점이 살아 있는 시간. 기본 30분이고, 지나면 저절로 풀린다.
> 오래 걸릴 것 같으면 `ax_renew`(손으로는 `npx -y axmap-cli@latest renew`)로
> 연장한다. 깜빡 잊고 반납 안 해도 남이 영원히 막히지 않게 하려고 있는 장치다.

---

## 6. 커밋할 때

```bash
git commit -m "[S15P21E201-144] feat: [FE] 여행 상세 화면 구현"
```

**대괄호 Jira 키를 맨 앞에** 적어야 티켓에 코드가 자동으로 붙는다.

선점하지 않은 파일을 커밋하려 하면 **훅(git 이 커밋 직전에 자동으로 돌리는 검사)이
막는다.** 막혔다면 그 파일을 선점하거나, 커밋에서 빼면 된다.

브랜치는 `feat/S15P21E201-144-trip-detail` 처럼 만든다.
MR 은 `main` 이 아니라 **자기 파트의 `dev`** 로 올린다.
전체 규칙은 [../CONTRIBUTING.md](../CONTRIBUTING.md) 에 있다.

---

## 7. 끝나면 반납한다

AI 에게: `작업이 끝났으니 ax_release 로 반납해줘.`
손으로: `npx -y axmap-cli@latest release`

반납을 안 해도 TTL 이 지나면 풀리지만, **끝났으면 바로 반납한다.**
안 그러면 다른 사람이 최대 30분을 헛되이 기다린다.

반납했는데 *"아무것도 반납하지 못했습니다"* 가 나오면 **그냥 넘어가지 마라.**
잡을 때와 이름이 다르다는 뜻이고, 락은 그대로 남아 있다.

---

## 막히면

1. `npx -y axmap-cli@latest doctor` — 대부분 여기서 답이 나온다
2. [../CONTRIBUTING.md](../CONTRIBUTING.md) — 팀 전체 규칙 (0.3 절이 "사본을 왜 손으로 고치면 안 되나")
3. [HANDOVER.md](HANDOVER.md) — 지금 무엇이 열려 있고 무엇이 고장 나 있는지 (4.6 절)
4. 도구 **자체**를 고쳐야 한다면 axMap 저장소로 간다 —
   `https://lab.ssafy.com/rleaderjoon/axmap`. **이 저장소의 `ci/axmap/` 을 고치는
   것이 아니다**
5. 그래도 안 되면 팀에 물어본다. 물어본 내용은 이 문서에 한 줄로 남긴다 —
   **당신이 막힌 곳에서 다음 사람도 막힌다.**
