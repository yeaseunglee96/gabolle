#!/usr/bin/env bash
# 가격+narrative 조사(S15P21E201-1414)를 50곳 청크 단위로 axmap 선점하며 순서대로 돈다.
# 남이 잡은 번호는 건너뛴다. 여럿이 각자 PC 에서, 또 한 PC 에서 여러 개를 동시에
# 돌려도 안전하다 — 단 그러려면 아래 AXMAP_SESSION 이 있어야 한다.
#
# 사용법 (bigData/ 에서):
#   bash research/collect-chunks.sh
#
# 무엇을 하나:
#   1~$TOTAL_CHUNKS 번 청크를 순서대로 확인 — 이미 끝난 건 건너뛴다
#   비어 있으면 axmap 으로 그 청크를 선점하고 price-queue.mjs 를 돌린다
#   결과가 생기면(found !== null) 커밋 + push 까지 자동으로 한다
#   할당량류 오류가 한 청크에서 절반 넘게 나오면 스스로 멈춘다
#
# 🔴 커밋까지는 자동이지만, 그 결과를 담은 브랜치의 MR 을 열고 머지하는 것은
#    사람(또는 이어받는 에이전트)이 한다. 브랜치 하나(TASK 브랜치, 아래)를 계속
#    쓴다 — 청크마다 새 브랜치를 만들지 않는다.
#
# 선점을 두 겹으로 쓴다 (중요 — 하나만 쓰면 여럿이 동시에 못 돈다):
#   ① 청크 표식 "bigData/research/data/combined-results/chunk-N" — 실제 파일이 아니다.
#      그 청크를 도는 동안 오래 잡아 둔다. 목적은 사람끼리 "누가 몇 번을 도나" 보이는 것뿐.
#   ② 커밋 직전에 그 청크에서 방금 생긴 실제 파일들만 짧게 선점한다.
#      ①로 폴더 전체를 잡으면 다른 청크를 돌던 사람이 자기 것을 커밋 못 한다.
#
# 🔴 2026-09-22 아침 — 이 판(v2)에서 고친 것:
#   (a) "새로 생긴 파일"만 보면 놓친다. 재시도가 이미 커밋된(found:null) 파일을
#       그 자리에서 덮어쓰면 git 은 "수정"으로 보는데 "새 파일"만 보면 그걸 놓친다.
#       → git status --porcelain 으로 새 파일 + 수정된 파일을 다 보고,
#         found !== null 인 것만 걸러 커밋한다.
#   (b) git commit 종료 코드를 반드시 확인한다 — 실패를 "완료"로 잘못 찍은 적이 있다.
#   (c) 선점 경로에 저장소 루트 기준 접두어(bigData/)를 붙인다.
#
# 🔴 2026-09-22 오후 — 이 판(v3)에서 고친 것. **선점이 실제로는 한 번도 안 막았다.**
#   axmap 이 "임자" 를 가르는 기준은 이름 하나가 아니라 **(이름, 세션) 짝**이고,
#   자기 자신과는 일부러 안 겹치게 본다(추가 선점을 허용하려고). 그런데 이 루프는
#   자기 세션 번호를 안 줘서 Claude Code 가 창마다 심는 번호를 그대로 물려받았고,
#   그래서 **한 창에서 띄운 루프 둘이 axmap 에게는 같은 한 주체**였다. 아래 ①의
#   거부 검사는 발동할 조건 자체가 없었다 — 그건 *남*이 잡았을 때만 뜨는 말이다.
#   장부 실측: 09-21~22 의 청크 선점 40건이 전부 세션 하나에서 나왔다.
#
#   (d) 루프마다 **자기 세션 번호**를 만들어 준다 (AXMAP_SESSION, 아래).
#       이것이 없으면 나머지 둘을 고쳐도 소용이 없다.
#   (e) 선점 시간을 30분 → 60분. 청크 하나가 50곳 ÷ 동시 6 × 최대 220초 ≈ 33분이라
#       30분으로는 **돌고 있는 중에 만료**된다. 실제로 09-22 02:05 에 잡은 청크10 이
#       반납 기록 없이 만료됐고 그 자리에 다른 루프가 들어왔다.
#   (f) 거부 판정을 **종료 코드**로 본다. 글자("거부")를 찾던 것은 문구가 바뀌면
#       조용히 통과한다 — 선점에서 가장 나쁜 고장이다.
#
# 🔴 `axmap release` 를 경로 없이 부르지 마라 (`/ax-done` 이 그렇게 부른다).
#   그건 그 세션이 쥔 것을 **전부** 놓아서, 돌고 있는 청크까지 빈자리로 만든다.
#   실측: 09-22 06:59:45 에 청크11 이 시작 50초 만에 풀렸고 2분 뒤 다시 잡혔다.
#   (d) 를 넣은 뒤로는 이 루프의 세션이 따로라 바깥의 전체 반납이 닿지 않는다.
#
# 🔴 할당량(개인 계정 5시간 롤링 한도)이 이미 막혀 있으면 이 스크립트를 돌리지 마라 —
#    Gemini 든 Claude(agy 경유)든 같은 벽을 공유한다. 확인법과 자세한 사정은
#    axmap 저장소 docs/ 의 최신 인수인계 문서에 있다.

set -u
REPO="$(git rev-parse --show-toplevel)"
BD="$REPO/bigData"
cd "$BD" || exit 1

TASK="${AXMAP_TASK:-S15P21E201-1414}"
TOTAL_CHUNKS="${TOTAL_CHUNKS:-40}"
LOG="$BD/research/data/collect.log"
AXMAP="npx -y axmap-cli@latest"

# 🔴 이 루프만의 세션 번호. **없으면 선점이 아무것도 안 막는다** (머리말 (d)).
# 안 주면 Claude Code 가 창마다 심어 둔 번호를 물려받아, 같은 창에서 띄운 루프
# 둘이 axmap 에게 같은 한 주체가 된다. 프로세스 번호($$)와 시각을 합쳐 매번 다르게.
export AXMAP_SESSION="collect-chunks-$$-$(date +%s)"

log() { echo "$(date '+%H:%M:%S') $*" | tee -a "$LOG"; }

for n in $(seq 1 "$TOTAL_CHUNKS"); do
  status=$(node research/price-queue.mjs --chunk "$n" --status 2>&1)
  echo "$status" >> "$LOG"
  remaining=$(echo "$status" | grep -oE "남은 곳 [0-9]+" | grep -oE "[0-9]+")

  if [ "${remaining:-0}" = "0" ]; then
    log "청크 $n — 이미 끝남, 건너뜀"
    continue
  fi

  # ① 청크 표식 선점 (저장소 루트 기준 경로 — 실제로 없어도 된다)
  claim_out=$($AXMAP claim "bigData/research/data/combined-results/chunk-$n" \
    --task "$TASK" --intent "가격조사 청크$n (인덱스 $(( (n-1)*50 ))~$(( n*50-1 )))" --ttl 60m 2>&1)
  claim_code=$?
  # 종료 코드 2 = 남이 잡고 있음. 그 밖의 0 아닌 값도 "못 잡았다" 이므로 똑같이 건너뛴다.
  if [ "$claim_code" -ne 0 ]; then
    log "청크 $n — 선점 실패(종료코드 $claim_code), 건너뜀"
    echo "$claim_out" >> "$LOG"
    continue
  fi
  log "청크 $n 선점 성공 — 시작 ($remaining 곳 남음)"

  node research/price-queue.mjs --chunk "$n" --concurrency 6 >> "$LOG" 2>&1

  # 새 파일 + 수정된 파일을 다 본 뒤, found !== null 인 것만 걸러 커밋한다.
  changed_root=$(cd "$REPO" && git status --porcelain bigData/research/data/combined-results/ | cut -c4-)

  real_root=""
  if [ -n "$changed_root" ]; then
    real_root=$(cd "$REPO" && node -e '
      const fs = require("fs");
      const out = [];
      for (const p of process.argv.slice(1)) {
        try {
          const r = JSON.parse(fs.readFileSync(p, "utf8"));
          if (r.found !== null) out.push(p);
        } catch (e) {}
      }
      console.log(out.join("\n"));
    ' $changed_root)
  fi

  new_count=0
  [ -n "$real_root" ] && new_count=$(echo "$real_root" | grep -c .)

  # 할당량류 오류 신호는 "값 없는(found:null)" 쪽에서 본다
  null_root=$(comm -23 <(echo "$changed_root" | sort -u) <(echo "$real_root" | sort -u) 2>/dev/null | grep -v '^$')
  exhausted=0
  if [ -n "$null_root" ]; then
    exhausted=$(cd "$REPO" && echo "$null_root" | xargs -I{} grep -liE "RESOURCE_EXHAUSTED|quota|rate.?limit|429|too many requests" {} 2>/dev/null | wc -l)
  fi

  # ② 커밋 직전 — 이번에 값이 실제로 생긴 파일들만 짧게 선점 (이미 저장소 루트 기준 경로)
  if [ "$new_count" -gt 0 ]; then
    file_claim=$(cd "$REPO" && $AXMAP claim $real_root --task "$TASK" --intent "청크$n 결과 커밋" --ttl 10m 2>&1)
    file_claim_code=$?
    if [ "$file_claim_code" -ne 0 ]; then
      log "🔴 청크 $n — 결과 파일 선점 실패(종료코드 $file_claim_code), 커밋 건너뜀 (다음 판에 다시 시도)"
      echo "$file_claim" >> "$LOG"
    else
      (cd "$REPO" && git add $real_root)
      commit_out=$(git commit -q -m "[$TASK] chore: [Data] 가격 조사 청크 $n 결과 ($new_count 곳)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>" 2>&1)
      commit_code=$?
      echo "$commit_out" >> "$LOG"

      if [ "$commit_code" -ne 0 ]; then
        log "🔴 청크 $n — git commit 실패(종료코드 $commit_code). 멈춘다 — 확인 필요."
        echo "COMMIT_FAILED chunk=$n" >> "$LOG"
        break
      fi

      push_out=$(git push 2>&1)
      echo "$push_out" >> "$LOG"
      log "청크 $n — $new_count 곳 커밋·push 완료"
      (cd "$REPO" && $AXMAP release $real_root) >> "$LOG" 2>&1
    fi
  fi

  (cd "$REPO" && $AXMAP release "bigData/research/data/combined-results/chunk-$n") >> "$LOG" 2>&1

  if [ "$exhausted" -ge 25 ]; then
    log "🔴 할당량이 막힌 것으로 보인다 (청크 $n 에서 $exhausted 곳 오류). 멈춘다."
    echo "QUOTA_EXHAUSTED $(date '+%Y-%m-%d %H:%M:%S')" >> "$LOG"
    break
  fi
done

log "루프 종료"
