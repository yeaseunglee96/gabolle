<#
  axMap 설치 — clone 한 뒤 한 번만 실행한다.

    powershell -ExecutionPolicy Bypass -File .\setup.ps1

  하는 일은 셋뿐이다.
    1. 이름이 정해져 있는지 확인한다 (없으면 여기서 멈춘다)
    2. 장부를 만든다              (axmap init)
    3. 커밋 훅을 심는다           (axmap hook install)

  MCP 는 따로 설정할 것이 없다. `.mcp.json` 이 저장소에 들어 있으므로 Claude Code 가
  이 폴더를 열 때 스스로 발견하고 승인을 묻는다. "예" 한 번이면 끝난다.
#>
param([switch]$Force)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

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
$major = [int]($ver -split '\.')[0]
if ($major -lt 20) { Fail "Node $ver 입니다. 20 이상이 필요합니다." }
Ok "node $ver"

# --- 2. 이름 ----------------------------------------------------------------
#
# [!] 여기서 기본 이름을 채워 주지 않는다. 이것이 이 스크립트에서 제일 중요한 줄이다.
#
#     모두에게 같은 이름을 주면 장부에는 **한 명만** 존재하게 되고, 겹침 판정은 자기
#     claim 을 겹침으로 보지 않으므로 팀 전원이 서로의 영역을 아무 경고 없이 덮어쓴다.
#     락이 조용히 여러 명에게 발급된 것이고, 그게 이 도구가 막으려는 사고 그 자체다.
#
#     다행히 팀에서는 `git config user.name` 이 이미 사람마다 다르다. 그걸 그대로 쓴다 —
#     새로 물어볼 것이 없다.
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
& node (Join-Path $root "bin\axmap.mjs") init
if ($LASTEXITCODE -ne 0) {
  Fail "장부를 만들지 못했습니다. 위 메시지를 읽고 고친 뒤 다시 실행하세요."
}

# --- 4. 훅 ------------------------------------------------------------------
#
# [!] 훅이 없으면 이 프로토콜은 권고 사항에 불과하다. claim 하지 않은 파일도
#     그냥 커밋되고, 그러면 아무도 규칙을 지킬 이유가 없어진다.
Say ""
& node (Join-Path $root "bin\axmap.mjs") hook install
if ($LASTEXITCODE -ne 0) { Warn "훅을 심지 못했습니다. 나중에 'node bin\axmap.mjs hook install' 을 직접 실행하세요." }

# --- 5. 안내 ----------------------------------------------------------------
Say ""
Say "설치 완료."
Say ""
Say "다음:"
Say "  1. 이 폴더에서 Claude Code 를 엽니다 (claude)"
Say "  2. MCP 서버 'axmap' 을 승인할지 물어보면 '예' 를 누릅니다"
Say "     (.mcp.json 이 저장소에 들어 있어 따로 설정할 것이 없습니다)"
Say "  3. 에이전트에게 axmap_brief 를 먼저 부르게 합니다"
Say ""
Say "쓰는 법:"
Say "  파일을 고치기 전에 claim -> 끝나면 release. 그게 전부입니다."
Say "  node bin\axmap.mjs status     지금 누가 무엇을 잡고 있나"
Say ""
