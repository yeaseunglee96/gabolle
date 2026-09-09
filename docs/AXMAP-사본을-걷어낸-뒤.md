# axMap 사본이 사라진 뒤 — 내 PC 에서 무엇을 고쳐야 하나

**2026-09-07 실측.** 읽는 데 5분, 고치는 데 2분.

이 문서는 **각자 PC** 만 다룬다. 브랜치에서 사본이 언제 어떻게 빠지는지는
[AXMAP-NPM-MIGRATION.md](AXMAP-NPM-MIGRATION.md) 에 있다.

> **사본** — `ci/axmap/`. 팀 저장소 안에 복사해 두고 돌리던 axMap 프로그램 덩어리.
> 이제 없앤다. axMap 은 **npm 꾸러미 `axmap-cli`** 하나로만 산다.

---

## 0. 지금 바로 (2분, 한 번이면 끝)

```bash
npm i -g axmap-cli@latest     # 이 PC 어디서든 axmap 명령이 뜬다
axmap setup                   # 홈에 앱·장부·커밋 훅·슬래시 명령을 붙인다
axmap doctor                  # 실제로 도는지 본다. 종료 코드 0 이면 끝
```

그리고 **훅 한 줄**을 고친다 → 2절. 이게 전부다.

윈도우·맥·리눅스 명령이 같다. 윈도우는 PowerShell 이든 Git Bash 든 상관없다.

---

## 1. 🔴 안 고치면 어떻게 알아채나 — **알아챌 수 없다**

이게 이 문서가 있는 이유다. 넷 중 **가장 중요한 하나가 아무 소리 없이 죽는다.**

| 무엇이 | 화면에 뜨는 것 | 실제로 벌어지는 일 |
|---|---|---|
| 🔴 **쪽지 알림** | **아무것도 안 뜬다** | 나에게 온 쪽지가 영영 안 보인다. 보낸 사람은 갔다고 믿는다 |
| MCP 도구(`ax_claim` 등) | 도구 목록에서 사라진다 | 눈에 보인다. AI 에게 "선점해줘" 가 안 먹힌다 |
| 커밋 훅 | `!! axMap: 검사 도구를 찾지 못했습니다` (홈 설치본도 없을 때) | **커밋은 그냥 통과한다.** claim 없이 고친 파일이 들어간다 |
| 문서에 적힌 `node ci/axmap/…` | `Cannot find module` | 눈에 보인다. 새 이름으로 바꾸면 된다 → 3절 |

**왜 쪽지만 조용한가.** 세션 시작·프롬프트마다 도는 훅 명령이 이렇게 생겼다.

```
node ci/axmap/tools/bus.mjs list --mine --unread --throttle 15 --quiet-if-empty 2>/dev/null || true
```

`2>/dev/null`(오류 메시지를 버린다) 과 `|| true`(실패해도 성공으로 친다)가 붙어 있다.
사본이 사라진 폴더에서 실제로 돌려 본 결과다.

```
$ node ci/axmap/tools/bus.mjs list … ; echo $?
Error: Cannot find module '…\ci\axmap\tools\bus.mjs'
1                                   ← 그냥 돌리면 이렇게 죽는다

$ node ci/axmap/tools/bus.mjs … 2>/dev/null || true ; echo $?
0                                   ← 훅에 적힌 그대로는 아무 말 없이 성공
```

**그리고 이 설정은 각자 PC 의 `.claude/settings.json` 에 있고 저장소에 커밋돼 있지 않다.**
MR 이 머지돼도 자동으로 안 고쳐진다. 각자 한 번씩 고쳐야 한다.

---

## 2. 🔴 쪽지 훅 고치기 — 한 줄만 바꾼다

### 방법 A — axMap 에게 시킨다 (권장)

```bash
axmap setup --hook
```

`--hook` 을 **줘야** 심는다. 안 주면 안 심고 안내만 한다(기본값). `~/.claude/settings.json` 의
`UserPromptSubmit` 에 홈 설치본의 절대 경로로 명령을 넣는다.

### 방법 B — 손으로 고친다

내 `.claude/settings.json`(저장소 폴더 안 또는 홈 `~/.claude/settings.json`)을 열고,
**`node ci/axmap/tools/bus.mjs` 를 `axmap bus` 로 바꾸기만 한다.** 뒤의 인자는 그대로다.

**바꾸기 전**

```json
"command": "node ci/axmap/tools/bus.mjs list --mine --unread --throttle 15 --quiet-if-empty 2>/dev/null || true"
```

**바꾼 뒤**

```json
"command": "axmap bus list --mine --unread --throttle 15 --quiet-if-empty 2>/dev/null || true"
```

> `SessionStart` 와 `UserPromptSubmit` **둘 다에 있으면 둘 다** 바꾼다.
> `axmap` 은 npm 전역 폴더에 깔리고 그 폴더는 이미 PATH 에 있다 (이 PC 실측:
> `/c/Users/SSAFY/AppData/Roaming/npm/axmap`). 그래서 경로를 적을 필요가 없다.

### 살았는지 확인

```bash
axmap bus list --mine --no-mark --throttle 0
```

목록이 뜨면 산 것이다. (`--no-mark` — 읽음으로 찍지 않는다. `--throttle 0` — 아껴 두지 말고 지금 본다.)
아무것도 안 뜨면 진짜로 쪽지가 없는 것이다.

### MCP 등록은 저절로 넘어간다

`claude mcp list` 를 보면 `axmap` 이 두 곳에 잡혀 있고 **저장소 쪽이 홈을 이기고 있다.**

```
axmap: node ./ci/axmap/mcp/server.mjs            ← 저장소(project). 사본과 함께 사라진다
       node C:\Users\<나>\.axmap\app\mcp\server.mjs  ← 홈(user). 이게 남는다
```

`axmap setup` 을 **미리** 돌려 두면 홈 쪽이 이미 있으므로 사본이 빠질 때 끊기지 않는다.
안 돌려 뒀다면 그날 AI 도구에서 `ax_claim` 이 사라진다.

---

## 3. 옛 명령 ↔ 새 명령 대조표

`node ci/axmap/…` 로 시작하는 것을 전부 `axmap …` 로 바꾼다. **인자는 그대로다.**

| 옛 명령 | 새 명령 | 이 문서에서 |
|---|---|---|
| `node ci/axmap/bin/axmap.mjs status` | `axmap status` | ✅ 돌려 확인 |
| `node ci/axmap/bin/axmap.mjs claim <경로> --task --intent` | `axmap claim <경로> --task --intent` | ✅ 돌려 확인 |
| `node ci/axmap/bin/axmap.mjs release <경로>` | `axmap release <경로>` | ✅ 돌려 확인 |
| `node ci/axmap/bin/axmap.mjs renew` | `axmap renew` | ⬜ 안 돌림 |
| `node ci/axmap/bin/axmap.mjs doctor` | `axmap doctor` | ✅ 돌려 확인 |
| `node ci/axmap/bin/axmap.mjs verify` | `axmap verify` | ✅ 돌려 확인 |
| `node ci/axmap/bin/axmap.mjs init` | `axmap init` | ⬜ 안 돌림 (장부를 만든다) |
| `node ci/axmap/bin/axmap.mjs audit --fetch` | `axmap audit --fetch` | ⬜ 안 돌림 (CI 가 쓴다) |
| `node ci/axmap/bin/axmap.mjs hook install` | `axmap hook install` | ⬜ 안 돌림 (내 훅을 덮는다) |
| `node ci/axmap/tools/bus.mjs list` | `axmap bus list` | ✅ 돌려 확인 |
| `node ci/axmap/tools/bus.mjs post --to --subject` | `axmap bus post --to --subject` | ⬜ 안 돌림 (도움말로만 확인) |
| `node ci/axmap/tools/mr-target.mjs --source --target` | `axmap mr-target --source --target` | ✅ 돌려 확인 |
| `node ci/axmap/tools/version.mjs current` | `axmap version current` | ✅ 돌려 확인 |
| `node ci/axmap/tools/version.mjs next --branch` | `axmap version next --branch` | ✅ 돌려 확인 |
| `node ci/axmap/tools/version.mjs bump --branch --push` | `axmap version bump --branch --push` | ⬜ 안 돌림 (태그를 만든다) |
| `node ci/axmap/governance/gate.mjs --source --target` | `axmap gate --source --target` | ✅ 돌려 확인 |
| `node ci/axmap/governance/vote.mjs` | `axmap vote` | ⬜ 안 돌림 (표를 던진다) |
| `node ci/axmap/tools/promote.mjs --step` | `axmap promote --step` | ⬜ 안 돌림 (MR 을 만든다) |
| `node ci/axmap/mcp/server.mjs` | `axmap mcp` | ⬜ 직접 부를 일이 없다 |
| `node ci/verify-vendor.mjs` | **없어졌다** | 사본이 없으니 대조할 것도 없다 |

⬜ 는 **부작용이 있어 일부러 안 돌린 것**이고, `axmap --help` 에 그 이름으로 적혀 있다.

> 🔴 **`axmap release --help` 는 절대 치지 마라.** 도움말이 안 나오고 **선점을 전부 반납한다**
> (S15P21E201-692). 오늘 두 에이전트가 이걸로 남의 작업 선점을 날렸다.
> `release` 는 **반드시 경로를 주고** 부른다 — `axmap release docs/내파일.md`.

**팀 CI 는 조금 다르게 부른다.** CI 는 매번 새 컨테이너라 설치할 곳이 없어서
`npx`(설치하지 않고 그 자리에서 한 번 실행하는 명령)를 쓴다.

```
npx -y axmap-cli@$AXMAP_VERSION mr-target      # CI 가 쓰는 형태
axmap mr-target                                 # 내 PC 가 쓰는 형태
```

내 PC 에서도 설치가 싫으면 `npx -y axmap-cli@latest <명령>` 이 그대로 된다.

---

## 4. 안 바꿔도 되는 것 — 여기는 손대지 마라

| | 그대로인가 |
|---|---|
| **내가 잡아 둔 claim** | 그대로다. 사본이 없는 브랜치에서 `axmap status` 를 돌려 같은 5건을 그대로 봤다 |
| **장부·쪽지함·표** | 그대로다. `.axmap/ledger` · `axmap/claims` · `axmap/bus` · `axmap/votes` — 자리도 이름도 안 바뀐다 |
| **선점 규칙** | 그대로다. 고치기 전에 claim, 끝나면 release |
| **슬래시 명령** | 그대로다. `/ax` `/ax-start` `/ax-done` `/ax-inbox` `/ax-tell` `/ax-vote` `/ax-ballot` — 안에 적힌 대체 명령만 새 이름으로 이미 바뀌어 있다 |
| **`governance/policy.json`** | 팀 저장소에 남는다. 도구는 나가고 **데이터는 남는다** |
| **브랜치 사다리·커밋 메시지 규칙** | 그대로다 |
| **git pre-commit 훅** | 요즘 심은 것이면 다시 안 심어도 된다. 홈(`~/.axmap/app/bin/axmap.mjs`)을 먼저 찾고 없을 때만 저장소를 뒤지기 때문이다. **내 것이 그런지 확인** — `.git/hooks/pre-commit` 을 열어 `.axmap/app` 이라는 글자가 있으면 안전하고, `ci/axmap` 만 있으면 `axmap hook install` 로 다시 심는다 |

> 🔴 **커밋 훅에 한 가지 함정이 있다.** 홈에도 없고 저장소에도 없으면 훅은
> 경고 한 줄을 찍고 **종료 코드 0 으로 통과시킨다.** 커밋은 막히지 않고,
> 선점 검사만 조용히 사라진다. 그래서 `axmap setup` 을 **미리** 돌려 두는 것이 중요하다.
> (팀 CI 의 `claims` 잡은 훅과 무관하게 도니까, 나중에 MR 에서 잡힌다.)

---

## 5. 되돌리는 법

| 무엇이 깨졌나 | 무엇을 하나 |
|---|---|
| 내 PC 에서 새 판이 이상하다 | `npm i -g axmap-cli@1.1.0` — 판 번호를 못 박는다. `npm view axmap-cli version` 으로 지금 최신을 본다 (2026-09-07 기준 **1.1.1**) |
| 아직 사본이 있는 브랜치로 돌아가고 싶다 | 그 브랜치에서는 `node ci/axmap/…` 옛 경로가 **그대로 돈다.** 두 방식이 당분간 같이 산다 |
| **팀 파이프라인이 axMap 때문에 빨갛다** | `Settings → CI/CD → Variables` 의 **`AXMAP_VERSION`** 을 어제 번호(예: `1.1.0`)로 바꾼다. 기본값은 `latest` 다. **커밋도 MR 도 없이 1분**이면 옛 판으로 돌아간다 |
| 훅을 되돌리고 싶다 | `.claude/settings.json` 의 그 한 줄을 옛 문자열로 되돌린다. 다른 것은 안 건드렸다 |

---

## 6. 왜 이렇게 바꾸나 — 그리고 치르는 값

| | |
|---|---|
| **얻는 것** | 고칠 곳이 한 곳이다. 사본은 고칠 때마다 axMap 저장소와 팀 저장소 **두 곳**을 맞춰야 했고, 지문이 어긋나면 팀이 멈췄다 |
| | 오늘 실제로 **`common/dev` 에만 새 방식이 있고 나머지 브랜치에는 없는 상태**가 며칠 이어졌다. 같은 저장소인데 브랜치마다 명령이 다르면 아무도 문서를 못 믿는다 |
| | 사본이 홈 등록을 **이겨서**, npm 판을 깔아 놓고도 팀 저장소에서는 옛 코드가 돌았다 |
| 🔴 **치르는 값** | **CI 가 파이프라인마다 npm 을 열 번쯤 부른다.** 실측: 잡 8개가 각자 `npm view` 를 한 번씩 하고, 그 위에 `npx -y axmap-cli` 가 6군데 있다 |
| | 그래서 **npm 이 안 되는 날 팀 코드가 한 줄도 안 바뀌었는데 파이프라인이 빨개진다.** 사본은 원래 이걸 막으려고 있던 것이다 |
| **되돌리는 손잡이** | 위 5절의 `AXMAP_VERSION`. 자세한 대비는 [CI-RESILIENCE.md](CI-RESILIENCE.md) |

---

## 7. 문제가 생겼을 때

**먼저 `axmap doctor`.** 줄마다 `OK` · `~~`(알아둘 것) · `!!`(고쳐야 할 것) 가 붙는다.
이 PC 의 실측 결과다.

```
  OK  node       v24.18.0
  OK  저장소        C:/Users/SSAFY/Desktop/S15P21E201
  OK  내 이름       janghyojoon  (git config user.name)
  OK  장부         .axmap\ledger
  OK  원격 연결      닿습니다
  OK  커밋 훅       심겨 있습니다
  OK  MCP 설정     ./ci/axmap/mcp/server.mjs
  ~~  MCP 설정     홈에도 등록돼 있습니다 (claude · agy) — 이 저장소에서는 위의 .mcp.json 이 먼저 쓰입니다
  OK  선점 판정      지금 유효한 claim 5건 (그중 내 것 1건)
  OK  버전         1.1.1 — 최신입니다
```

사본이 빠진 브랜치에서는 `MCP 설정` 줄이 홈 하나로 줄어든다. 그게 정상이다.

| 증상 | 먼저 해 볼 것 |
|---|---|
| `axmap: command not found` | `npm i -g axmap-cli@latest`. 그래도 안 되면 터미널을 껐다 켠다 (PATH 갱신) |
| 쪽지가 안 뜬다 | `axmap bus list --mine --no-mark --throttle 0` → 뜨면 훅 문제(2절), 안 뜨면 진짜 없는 것 |
| `쪽지함을 못 열었습니다` | `axmap bus-repair` |
| AI 도구에 `ax_*` 가 없다 | `axmap setup` 한 번 → **AI CLI 를 완전히 껐다 켠다** |
| `장부가 없습니다` | `axmap init` |
| 커밋이 막힌다 | 고치려는 파일을 `axmap claim` 하지 않은 것이다. `axmap status` 로 남의 것인지 먼저 본다 |

그래도 안 되면 **쪽지로 물어본다** — Claude Code 면 `/ax-tell`, 손으로는
`axmap bus post --to <상대> --subject "<제목>" < 본문.md`. 쪽지는 커밋도 MR 도 없이 바로 간다.
axMap 자체의 문제는 axMap 저장소(`https://lab.ssafy.com/rleaderjoon/axmap`) 몫이다.

---

## 용어

| 말 | 뜻 |
|---|---|
| **사본**(벤더링) | 남의 코드를 내 저장소 안에 복사해 두고 그 사본으로 돌리는 것. 여기서는 `ci/axmap/` |
| **npm** | Node 프로그램을 인터넷에서 받아 설치하는 곳이자 그 명령 |
| **`npx`** | 설치하지 않고 그 자리에서 한 번만 실행하는 명령 |
| **훅**(hook) | 정해진 순간에 자동으로 돌아가는 명령. 여기 둘이 나온다 — **git 커밋 훅**(커밋 직전에 도는 검사)과 **하네스 훅**(AI 도구가 세션 시작·프롬프트마다 도는 명령. `.claude/settings.json` 에 적는다) |
| **MCP** | AI 도구가 외부 프로그램을 "도구" 로 부를 수 있게 해주는 규격 |
| **종료 코드** | 프로그램이 끝나면서 내는 숫자. 0 이면 성공, 그 외는 실패 |
| **잡**(job) | 파이프라인이 돌리는 작업 하나 |
| **장부** | 누가 무엇을 잡고 있는지 적는 곳. `.axmap/ledger` · `axmap/claims` 브랜치 |
