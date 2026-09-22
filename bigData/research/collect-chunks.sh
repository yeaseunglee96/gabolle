#!/usr/bin/env bash
# 가격+narrative 조사(S15P21E201-1414)를 50곳 청크 단위로 axmap 선점하며 순서대로 돈다.
# 남이 잡은 번호는 건너뛴다. 여럿이 각자 PC 에서 동시에 돌려도 안전하다.
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
    --task "$TASK" --intent "가격조사 청크$n (인덱스 $(( (n-1)*50 ))~$(( n*50-1 )))" --ttl 30m 2>&1)
  if echo "$claim_out" | grep -qE "거부|Rejected"; then
    log "청크 $n — 남이 잡고 있어 건너뜀"
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
    if echo "$file_claim" | grep -qE "거부|Rejected"; then
      log "🔴 청크 $n — 결과 파일 선점 실패, 커밋 건너뜀 (다음 판에 다시 시도)"
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
