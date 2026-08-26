#!/usr/bin/env bash
# axMap 설치 — clone 한 뒤 한 번만 실행한다.
#
#   bash setup.sh
#
# 하는 일은 넷뿐이다.
#   1. 이름이 정해져 있는지 확인한다 (없으면 여기서 멈춘다)
#   2. 장부를 만든다              (axmap init)
#   3. 커밋 훅을 심는다           (axmap hook install)
#   4. (전역 CLI 사용자를 위한 안내를 찍는다)
#
# Claude Code 는 4번이 필요 없다. `.mcp.json` 이 저장소에 들어 있어 이 폴더를 열 때
# 스스로 발견하고 승인을 묻는다. 그 편의가 Claude Code 전용이라서 나머지 CLI
# (Codex, Antigravity `agy`)는 각자의 설정 파일에 적어 줘야 하고, 그걸 4번이 한다.

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

ok()   { printf '  OK  %s\n' "$1"; }
warn() { printf '  !!  %s\n' "$1"; }
fail() { printf '중단: %s\n' "$1" >&2; exit 1; }

echo
echo "axMap 설치"
echo "  폴더: $ROOT"
echo

# --- 1. node ----------------------------------------------------------------
command -v node >/dev/null 2>&1 || fail "node 를 찾을 수 없습니다. Node 20 이상을 설치한 뒤 다시 실행하세요."
VER="$(node --version | sed 's/^v//')"
[ "${VER%%.*}" -ge 20 ] || fail "Node $VER 입니다. 20 이상이 필요합니다."
ok "node $VER"

# --- 2. 이름 ----------------------------------------------------------------
#
# [!] 여기서 기본 이름을 채워 주지 않는다. 이것이 이 스크립트에서 제일 중요한 줄이다.
#
#     모두에게 같은 이름을 주면 장부에는 한 명만 존재하게 되고, 겹침 판정은 자기
#     claim 을 겹침으로 보지 않으므로 팀 전원이 서로의 영역을 아무 경고 없이 덮어쓴다.
#     락이 조용히 여러 명에게 발급된 것이고, 그게 이 도구가 막으려는 사고 그 자체다.
#
#     다행히 팀에서는 git config user.name 이 이미 사람마다 다르다. 그걸 그대로 쓴다.
WHO="$(git -C "$ROOT" config user.name || true)"
if [ -z "$WHO" ]; then
  cat >&2 <<'MSG'
중단: git 사용자 이름이 없습니다. 장부에서 당신을 가리킬 이름이 없다는 뜻입니다.

  git config --global user.name "홍길동"
  git config --global user.email "you@example.com"

기본 이름으로 대신 채우지 않습니다 - 여러 사람이 같은 이름이 되면
서로의 claim 을 겹침으로 보지 못해 같은 파일을 조용히 함께 고칩니다.
MSG
  exit 1
fi
ok "이름: $WHO  (git config user.name)"

# --- 3. 장부 ----------------------------------------------------------------
echo
echo "장부를 준비합니다..."
node "$ROOT/ci/axmap/bin/axmap.mjs" init || fail "장부를 만들지 못했습니다. 위 메시지를 읽고 고친 뒤 다시 실행하세요."

# --- 4. 훅 ------------------------------------------------------------------
#
# [!] 훅이 없으면 이 프로토콜은 권고 사항에 불과하다. claim 하지 않은 파일도
#     그냥 커밋되고, 그러면 아무도 규칙을 지킬 이유가 없어진다.
echo
node "$ROOT/ci/axmap/bin/axmap.mjs" hook install || warn "훅을 심지 못했습니다. 나중에 'node ci/axmap/bin/axmap.mjs hook install' 을 직접 실행하세요."

# --- 5. 다른 AI CLI 에 MCP 등록 ----------------------------------------------
#
# [!] 실패해도 여기서 멈추지 않는다. 장부와 훅이 본체고 MCP 는 편의다.
#     못 붙어도 사람이 CLI 로 claim 할 수 있으니 설치 전체를 무를 이유가 없다.
#     대신 무엇이 붙고 무엇이 안 붙었는지는 화면에 그대로 나온다.
# 🔴 예전에는 여기서 mcp-register.mjs 를 불렀다. 그 파일은 axMap 저장소로 나갔고
#    사본에는 없다. 그리고 이 저장소에서는 필요 없다 — .mcp.json 이 사본의 서버를
#    가리키고 있어서 Claude Code 는 clone 만으로 붙는다.
#    Codex·Antigravity 처럼 저장소의 .mcp.json 을 안 읽는 도구를 쓴다면
#    axMap 을 전역에 한 번 설치한다 (axMap 저장소의 mcp/SETUP-FOR-AI.md).
say "MCP: .mcp.json 이 ci/axmap/mcp/server.mjs 를 가리킵니다. AI CLI 를 껐다 켜세요." 

# --- 6. 스스로 확인 ----------------------------------------------------------
#
# [!] 설치했다는 말보다 "지금 실제로 도는가" 를 보여주는 편이 낫다.
#     이 도구의 실패는 대부분 조용해서, 오류가 안 났다는 것이 정상이라는 뜻이 아니다.
echo
echo "확인합니다..."
node "$ROOT/ci/axmap/bin/axmap.mjs" doctor || fail "위의 !! 줄을 고친 뒤 다시 실행하세요."

# --- 7. 안내 ----------------------------------------------------------------
#
# [!] 슬래시 명령(/ax-start 등)은 Claude Code 전용이라 옮길 수 없다.
#     그래서 다른 CLI 를 쓰는 사람에게는 "대신 이렇게 치세요" 를 준다.
cat <<'MSG'

설치 완료.

다음:
  1. 이 폴더에서 쓰는 AI CLI 를 엽니다 (claude / codex / agy)
  2. MCP 서버 'axmap' 을 승인할지 물어보면 '예' 를 누릅니다
  3. 첫 마디로 이렇게 쳐 보면 붙었는지 바로 압니다:

     Claude Code:
       /ax-start 여행 상세 화면을 만들려고 한다

     그 밖의 CLI (슬래시 명령이 없습니다 - 그냥 이 문장을 칩니다):
       ax_brief 로 이 저장소를 파악하고, ax_status 로 지금 누가 뭘 잡고 있는지 본
       다음, 내가 건드릴 경로를 ax_claim 해줘. 할 일은 여행 상세 화면 만들기야.

쓰는 법:
  파일을 고치기 전에 claim -> 끝나면 release. 그게 전부입니다.
  Claude Code 면 /ax 로 보고 /ax-done 으로 반납합니다.
  다른 CLI 면 같은 일을 시키는 문장이 docs/ONBOARDING.md 3.5 절 표에 있습니다.

  언제든 다시 확인:  node ci/axmap/bin/axmap.mjs doctor
  MCP 가 안 뜨면:    AI CLI 를 껐다 켠다 (.mcp.json 은 켤 때 한 번만 읽힌다)
  자세히:            docs/ONBOARDING.md  ("Claude Code 가 아닌 AI CLI 를 쓴다면" 절)

MSG
