<#
  axMap 설치 — clone 한 뒤 한 번만 실행한다.

    powershell -ExecutionPolicy Bypass -File .\setup.ps1

  🔴 이 파일은 **UTF-8 BOM 으로 저장한다. BOM 을 지우지 마라.**

  Windows PowerShell 5.1 은 BOM 이 없는 스크립트를 UTF-8 이 아니라 시스템 ANSI
  코드페이지(한국어 윈도우면 CP949)로 읽는다. 이 파일에는 한글이 가득하고, CP949
  디코더는 한글의 UTF-8 바이트를 두 바이트씩 잘못 묶으면서 뒤따르는 ASCII 바이트까지
  삼킨다. 삼켜지는 것이 하필 겹따옴표나 줄바꿈이면 문자열이 엉뚱한 곳에서 닫히고,
  **파일 전체가 문법 오류**가 된다.

  실제로 여기서 한 번 났다. 무서운 것은 실패하는 방식이다 — 고친 줄과 아무 상관없는
  줄 번호에 "Unexpected token" 이 뜬다. 원인을 찾을 단서가 메시지에 없다.
  BOM 이 있으면 파서가 UTF-8 로 읽고 이 문제가 통째로 사라진다.

  고친 뒤에는 문법 검사를 돌린다 (npm test 는 이 파일을 실행하지 않는다):
    [System.Management.Automation.Language.Parser]::ParseFile('setup.ps1', [ref]$t, [ref]$e)

  하는 일은 다섯뿐이다.
    1. 이름이 정해져 있는지 확인한다 (없으면 여기서 멈춘다)
    2. 장부를 만든다              (axmap init)
    3. 커밋 훅을 심는다           (axmap hook install)
    4. (전역 CLI 사용자를 위한 안내를 찍는다)
    5. 실제로 도는지 확인한다      (axmap doctor)

  MCP(AI 도구가 외부 프로그램을 "도구" 로 부를 수 있게 해주는 규격) 등록은 이 저장소가
  아니라 **각자의 PC 에 설치한 axMap** 이 한다. `npm i -g axmap-cli` 뒤 `axmap setup`
  을 한 번 돌리면 Claude Code · Codex · Antigravity 가 전부 각자의 홈 설정에 붙는다.
  4번은 그 안내만 찍는다.

  2026-08-31 이전에는 저장소에 `.mcp.json` 이 있어서 Claude Code 만 clone 으로 붙었다.
  그 파일을 뺐다 (S15P21E201-509) — 같은 이름이 저장소와 홈 두 곳에 잡혀 저장소 쪽이
  홈을 이겼고, npm 판을 깔아도 여기서는 안 쓰였다.
#>
param([switch]$Force)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$AX   = Join-Path $root (Join-Path 'ci' (Join-Path 'axmap' (Join-Path 'bin' 'axmap.mjs')))


function Say($m)  { Write-Host $m }
function Ok($m)   { Write-Host "  OK  $m" -ForegroundColor Green }
function Warn($m) { Write-Host "  !!  $m" -ForegroundColor Yellow }
function Fail($m) { Write-Host "중단: $m" -ForegroundColor Red; exit 1 }

Say ""
Say "axMap 설치"
Say "  폴더: $root"
Say ""

# --- 1. node ----------------------------------------------------------------
$node = Get-Command node -ErrorAction SilentlyContinue
if (-not $node) { Fail "node 를 찾을 수 없습니다. Node 20 이상을 설치한 뒤 다시 실행하세요." }
$ver = (& node --version) -replace '^v',''
if ([int](($ver -split '\.')[0]) -lt 20) { Fail "Node $ver 입니다. 20 이상이 필요합니다." }
Ok "node $ver"

# --- 2. 이름 ----------------------------------------------------------------
#
# [!] 여기서 기본 이름을 채워 주지 않는다. 이것이 이 스크립트에서 제일 중요한 줄이다.
#
#     모두에게 같은 이름을 주면 장부에는 한 명만 존재하게 되고, 겹침 판정은 자기
#     claim 을 겹침으로 보지 않으므로 팀 전원이 서로의 영역을 아무 경고 없이 덮어쓴다.
#     락이 조용히 여러 명에게 발급된 것이고, 그게 이 도구가 막으려는 사고 그 자체다.
#
#     다행히 팀에서는 git config user.name 이 이미 사람마다 다르다. 그걸 그대로 쓴다.
$who = (& git -C $root config user.name) 2>$null
if (-not $who) {
  Fail @"
git 사용자 이름이 없습니다. 장부에서 당신을 가리킬 이름이 없다는 뜻입니다.

  git config --global user.name "홍길동"
  git config --global user.email "you@example.com"

기본 이름으로 대신 채우지 않습니다 - 여러 사람이 같은 이름이 되면
서로의 claim 을 겹침으로 보지 못해 같은 파일을 조용히 함께 고칩니다.
"@
}
Ok "이름: $who  (git config user.name)"

# --- 3. 장부 ----------------------------------------------------------------
Say ""
Say "장부를 준비합니다..."
& node $AX init
if ($LASTEXITCODE -ne 0) { Fail "장부를 만들지 못했습니다. 위 메시지를 읽고 고친 뒤 다시 실행하세요." }

# --- 4. 훅 ------------------------------------------------------------------
#
# [!] 훅(git 이 커밋 직전에 자동으로 돌리는 검사)이 없으면 이 프로토콜은 권고 사항에
#     불과하다. claim 하지 않은 파일도 그냥 커밋되고, 그러면 아무도 규칙을 지킬
#     이유가 없어진다.
Say ""
& node $AX hook install
if ($LASTEXITCODE -ne 0) { Warn "훅을 심지 못했습니다. 나중에 'node ci\axmap\bin\axmap.mjs hook install' 을 직접 실행하세요." }

# --- 5. MCP 안내 --------------------------------------------------------------
#
# [!] 여기서 등록을 대신하지 않는다. 등록기(mcp-register.mjs)는 axMap 저장소에 있고
#     팀 사본에는 없다. 사본에 다시 넣으면 벤더 지문(ci\axmap\manifest.sha256)이
#     어긋나 ci:vendor 잡이 빨개진다. 그래서 이 자리는 안내만 한다.
Say "MCP: 이 저장소에는 등록 설정이 없습니다. 각자 한 번 돌리세요 -"
Say "       npm i -g axmap-cli   그리고   axmap setup"
Say "     claude / codex / agy 가 각자의 홈 설정에 붙습니다. 그 뒤 AI CLI 를 껐다 켜세요."

# --- 6. 스스로 확인 ----------------------------------------------------------
#
# [!] "설치했습니다" 보다 "지금 실제로 도는가" 를 보여주는 편이 낫다.
#     이 도구의 실패는 대부분 조용해서, 오류가 안 났다는 것이 정상이라는 뜻이 아니다.
Say ""
Say "확인합니다..."
& node $AX doctor
if ($LASTEXITCODE -ne 0) { Fail "위의 !! 줄에 고치는 방법이 함께 적혀 있습니다. 고친 뒤 다시 실행하세요." }

# --- 7. 안내 ----------------------------------------------------------------
#
# [!] 슬래시 명령(/ax-start 등)은 Claude Code 전용이라 옮길 수 없다.
#     그래서 다른 CLI 를 쓰는 사람에게는 "대신 이렇게 치세요" 를 준다.
Say ""
Say "설치 완료."
Say ""
Say "다음:"
Say "  1. 이 폴더에서 쓰는 AI CLI 를 엽니다 (claude / codex / agy)"
Say "  2. MCP 서버 'axmap' 을 승인할지 물어보면 '예' 를 누릅니다"
Say "  3. 첫 마디로 이렇게 쳐 보면 붙었는지 바로 압니다:"
Say ""
Say "     Claude Code:"
Say "       /ax-start 여행 상세 화면을 만들려고 한다"
Say ""
Say "     그 밖의 CLI (슬래시 명령이 없습니다 - 그냥 이 문장을 칩니다):"
Say "       ax_brief 로 이 저장소를 파악하고, ax_status 로 지금 누가 뭘 잡고 있는지 본"
Say "       다음, 내가 건드릴 경로를 ax_claim 해줘. 할 일은 여행 상세 화면 만들기야."
Say ""
Say "쓰는 법:"
Say "  파일을 고치기 전에 claim -> 끝나면 release. 그게 전부입니다."
Say "  Claude Code 면 /ax 로 보고 /ax-done 으로 반납합니다."
Say "  다른 CLI 면 같은 일을 시키는 문장이 docs\ONBOARDING.md 3.5 절 표에 있습니다."
Say ""
Say "  언제든 다시 확인:  node ci\axmap\bin\axmap.mjs doctor"
Say "  MCP 가 안 뜨면:    axmap setup 을 한 번 돌리고 AI CLI 를 껐다 켠다"
Say '  자세히:            docs\ONBOARDING.md  - Claude Code 가 아닌 AI CLI 를 쓴다면 3.5 절'
Say ""
