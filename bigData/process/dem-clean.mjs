/**
 * DEM 청소 — 고도 타일을 읽을 때 **가짜 지형을 먼저 걷어낸다**
 *
 * `slope.mjs` 와 `calibrate-slope.mjs` 가 둘 다 이 파일을 통해 타일을 읽는다.
 * 두 곳에 같은 코드를 베껴 두면 한쪽만 고쳐지고, 그러면 **보정이 잰 DEM 과
 * 경사가 쓴 DEM 이 달라진다** — 그게 이 작업이 고치려는 고장 그 자체다.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 🔴 왜 필요한가 (S15P21E201-795, 2026-09-10)
 *
 * 고도 원본은 AWS terrarium 타일이고 그 뿌리는 **SRTM(2000년 2월 우주왕복선
 * 레이더 측량)** 이다. 레이더는 물·젖은 모래·매립지에서 되돌아오는 신호가
 * 약해 **없는 봉우리를 만든다.** 부산 해안에 그것이 실제로 있다.
 *
 *   마린시티 앞 (35.15395, 129.14485) — terrarium 51.5 m,
 *   주변 250 m 중앙값 1.2 m. 즉 **평지 한복판에 50 m 짜리 혹**이 서 있다.
 *   (대조: ASTER 0 m · Copernicus GLO-30 13.9 m — 셋이 서로 안 맞는다)
 *
 * 이것이 경사에만 해를 끼치는 게 아니다. **보정 자체를 오염시켰다.**
 * `calibrate-slope.mjs` 는 "최고 고도 12 m 이하인 길" 을 평지 대조군으로 삼는데,
 * 혹이 그 길들을 12 m 위로 밀어 올려 **대조군에서 빼 버린다.** 오차가 스스로
 * 가장 나쁜 표본을 걸러낸 뒤에 "거짓양성 0.5%" 를 잰 것이다.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 🔴 두 가지를 한다
 *
 * **1. 값 범위 검사** — `[-500, +2000] m` 밖은 버리고 이웃 중앙값으로 메운다.
 *
 *    실측(타일 3,127장 전수): **175 장에 20,589 픽셀**이 범위 밖이고
 *    가장 낮은 값이 **-22,782 m** 다. 생김새는 **한 픽셀 폭 세로 실선** —
 *    한 열(예: 28125_12936.png 의 x=221)이 256행 내내 R 채널만 128 → 106 으로
 *    주저앉는다. 양옆 열은 멀쩡하다.
 *
 *    🔴 **우리 디코더 탓이 아니다.** 같은 타일을 PIL(파이썬 표준 이미지
 *    라이브러리)로 따로 풀어 대조했고 **픽셀 값이 똑같이 나왔다.** 원본
 *    타일에 들어 있는 손상이다. 그래서 고칠 곳은 `png.mjs` 가 아니라 여기다.
 *
 *    이게 왜 치명적인가: 아래 침식(erosion)은 **최솟값**을 취한다. -22,782 m
 *    픽셀 하나가 반경 안의 배경을 통째로 -22,782 m 로 끌어내리고, 그러면
 *    그 근방이 전부 "혹" 으로 오판된다. **범위 검사가 반드시 먼저다.**
 *
 * **2. 가짜 혹 누르기** — 저지대에서 좁은 폭으로 높이 튀는 덩어리를 배경으로 누른다.
 *
 *    방법은 **형태학적 열림(morphological opening)** 이다. 말로 풀면:
 *    지형 위에 반지름 r 짜리 원판을 **아래에서 떠받치며 굴린다.** 원판이
 *    들어가지 못하는 좁은 뾰족한 것은 배경으로 취급되고, 원판이 올라탈 수
 *    있을 만큼 넓은 것은 그대로 남는다. 그 결과가 `배경(bg)` 이고
 *    `튐(tophat) = 원래고도 - 배경` 이 "좁게 튀어나온 양" 이다.
 *
 *    이 방법을 고른 이유는 **가장자리에 절벽을 안 만들기 때문**이다.
 *    "25 m 넘는 픽셀만 눌러라" 로 하면 문턱 바로 바깥에 25 m 낭떠러지가
 *    생겨서 없던 급경사가 도리어 늘어난다. 그래서 픽셀이 아니라
 *    **튐이 이어져 있는 덩어리(connected component) 통째로** 누른다.
 *    덩어리 가장자리에서 튐은 0 으로 수렴하므로 이음매가 매끄럽다.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 🔴 세 숫자를 어떻게 정했나 — **권고를 그대로 안 썼다. 재보고 좁혔다.**
 *
 * 진단(S15P21E201-795)이 권고한 값은 *"주변 250 m 중앙값 15 m 이하인 저지대에서,
 * 원본 격자 5칸 이하 폭으로 25 m 넘게 튀는 덩어리"* 였다.
 * z15 타일은 **3.906 m/픽셀**이고 SRTM 한 칸 30 m ≈ **7.68 픽셀**이라
 * 5칸 = 150 m → 열림 반지름 r ≈ 19 px 에 해당한다.
 *
 * **r 을 바꿔가며 두 지형을 재봤다.** 하나는 가짜(마린시티 앞), 하나는 진짜(동백섬).
 * 문은 둘이다 — 튐 ≥ 25 m 그리고 배경 ≤ 15 m. **둘 다 통과해야** 눌린다.
 *
 * ```
 *              마린시티 앞 혹 51.5m (가짜)   │   동백섬 52.9m (진짜)
 *   r  지우는폭   배경    튐   판정          │   배경    튐   판정
 *   ─────────────────────────────────────────┼──────────────────────────
 *    8    66m   14.8  36.7  🔴 눌림          │   46.7   6.2  남음 (문 둘 다 막힘)
 *   10    82m    7.3  44.2  🔴 눌림          │   43.0   9.9  남음 (문 둘 다 막힘)
 *   12    98m    5.4  46.2  🔴 눌림          │   38.3  14.6  남음 (문 둘 다 막힘)  ← 고른 값
 *   14   113m    4.2  47.3  🔴 눌림          │   33.0  19.9  남음 (문 둘 다 막힘)
 *   16   129m    3.6  47.9  🔴 눌림          │   27.9  25.0  남음 (저지대문만 막음)
 *   19   152m    3.0  48.5  🔴 눌림          │   19.7  33.2  남음 (저지대문만 막음) ← 권고값
 *   24   191m    1.8  49.7  🔴 눌림          │    9.8  43.1  🔴 **눌림 — 섬이 무너진다**
 * ```
 *
 * 권고값 **19 는 되기는 된다. 그런데 문 하나로 버틴다.** 동백섬의 튐이 33.2 m 라
 * 튐 문(25 m)은 이미 뚫렸고, 저지대 문(배경 19.7 m vs 문턱 15 m)이 **1.3배 여유**로
 * 혼자 막고 있다. 그 문이 조금만 흔들려도 — 다른 매립지, 다른 조위, 국가 DEM 교체 —
 * **동백섬이 통째로 사라진다.** 실제로 24 에서 사라진다.
 *
 * 그래서 **`OPEN_RADIUS_PX = 12`** (≈ 47 m, 지우는 폭 2r+1 = 25 px ≈ **98 m** ≈
 * SRTM **3.3칸**) 을 쓴다. 여기서는 동백섬이 **두 문 모두에 막힌다** —
 * 튐 14.6 m(문턱 25 m 대비 **1.7배 여유**) · 배경 38.3 m(문턱 15 m 대비 **2.6배 여유**).
 * 가짜 혹 쪽은 손해가 없다: 배경 5.4 m · 튐 46.2 m 로 여전히 압도적으로 잡힌다.
 * (r=6 까지 내리면 마린시티 혹을 놓친다 — 그 혹의 폭이 74 m 라 51 m 창에 안 들어온다.
 *  쓸 수 있는 구간은 8~19 이고 12 는 그 한가운데다.)
 *
 * 나머지 둘은 권고대로 뒀다.
 *   `LOWLAND_MAX_M = 15` — 배경이 이보다 높으면 손대지 않는다.
 *   `RISE_MIN_M = 25`    — 덩어리의 최대 튐이 이보다 작으면 손대지 않는다.
 *
 * 🔴 **"주변 250 m 중앙값" 을 그대로 쓰지 않았다. 그걸 쓰면 동백섬이 통과한다.**
 *    동백섬은 사방이 바다라 250 m 중앙값이 **5.4 m** 다 — 저지대 문을 그냥 지나간다.
 *    여기서 배경으로 쓰는 것은 링 중앙값이 아니라 **열림이 만든 배경**이다.
 *    동백섬처럼 넓은 봉우리는 열림이 못 깎으므로 배경이 **38.3 m 로 딸려 올라가고**,
 *    그래서 문에 걸린다. 같은 "15 m" 라도 **무엇의 15 m 인지가 판정을 뒤집는다.**
 *
 * ─────────────────────────────────────────────────────────────────────────
 * ⚠️ 잃는 것: 저지대에 있는 **폭 100 m 미만, 높이 25 m 초과** 의 진짜 둔덕.
 *    부산 해안에서 그런 지형은 드물고(있으면 대개 섬이라 더 넓다), 있더라도
 *    경사 통계에서 한 구간짜리다. 반대로 가짜 혹은 매립지 도로망 한복판에
 *    앉아 수백 구간을 오염시킨다. **바꾸는 값이 다르다.**
 *
 * ⚠️ 국가 5 m DEM 으로 갈아타면 이 파일은 통째로 필요 없어질 수 있다.
 *    그때는 지우지 말고 **왜 껐는지**를 여기에 적는다.
 */
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { decodePNG, terrariumToElevation } from './png.mjs'

// ── 값 범위 (1) ────────────────────────────────────────────────────────────
/** 지구에서 이 범위를 벗어난 지면 고도는 없다. 부산이면 더더욱 없다. */
export const DEM_MIN_M = -500
export const DEM_MAX_M = 2000

// ── 가짜 혹 (2) ────────────────────────────────────────────────────────────
/** 열림 반지름(픽셀). 2r+1 픽셀보다 좁은 것이 "좁다" 로 취급된다. 위 표 참고. */
export const OPEN_RADIUS_PX = 12
/** 배경(열림 결과)이 이보다 높으면 저지대가 아니다 — 손대지 않는다. */
export const LOWLAND_MAX_M = 15
/** 덩어리의 최대 튐이 이보다 낮으면 혹이 아니다 — 손대지 않는다. */
export const RISE_MIN_M = 25
/** 덩어리의 바깥 끝. 튐이 이보다 낮으면 덩어리 밖 = 배경과 같다고 본다. */
const SKIRT_EPS_M = 0.5

/**
 * 이음매 여유(halo). 타일 하나를 청소하려면 **옆 타일까지 봐야 한다** —
 * 열림 반지름만큼, 그리고 덩어리가 타일 경계를 걸쳐 있을 수 있으므로 그 몫도.
 * 안 그러면 같은 혹이 왼쪽 타일에서는 눌리고 오른쪽 타일에서는 안 눌려
 * **경계에 없던 절벽이 생긴다.**
 */
const PAD = OPEN_RADIUS_PX * 3 + 8   // 44 px ≈ 172 m

/** 슬라이딩 창의 최솟값/최댓값 — 단조 덱(monotonic deque). 창 크기와 무관하게 O(n). */
function runExtreme(src, dst, w, h, r, wantMin) {
  const k = 2 * r + 1
  const idx = new Int32Array(Math.max(w, h) + 1)
  const better = wantMin ? (a, b) => a <= b : (a, b) => a >= b
  // 가로
  const tmp = new Float32Array(w * h)
  for (let y = 0; y < h; y++) {
    const row = y * w
    let head = 0, tail = 0
    for (let x = 0; x < w + r; x++) {
      if (x < w) {
        const v = src[row + x]
        while (tail > head && better(v, src[row + idx[tail - 1]])) tail--
        idx[tail++] = x
      }
      const out = x - r
      if (out >= 0) {
        while (idx[head] < out - r) head++
        tmp[row + out] = src[row + idx[head]]
      }
    }
  }
  // 세로
  for (let x = 0; x < w; x++) {
    let head = 0, tail = 0
    for (let y = 0; y < h + r; y++) {
      if (y < h) {
        const v = tmp[y * w + x]
        while (tail > head && better(v, tmp[idx[tail - 1] * w + x])) tail--
        idx[tail++] = y
      }
      const out = y - r
      if (out >= 0) {
        while (idx[head] < out - r) head++
        dst[out * w + x] = tmp[idx[head] * w + x]
      }
    }
  }
  return dst
}

/**
 * 손상 픽셀을 이웃 중앙값으로 메운다. 두 가지를 잡는다.
 *
 * **(1) 값 범위 밖** — `[-500, +2000] m`. 지시받은 그대로다.
 *
 * **(2) 한 픽셀 폭 뾰족한 것(spike)** — 🔴 **범위 검사만으로는 모자란다. 실측이다.**
 *
 *   손상은 R 채널이 주저앉아 생기는데, R=127 이면 고도가 -256~-1 m 이고
 *   R=126 이면 -512~-257 m 다. **그 값들은 [-500,2000] 안에 있다.**
 *   전수로 세어 보니 -50 m 아래 22,421 px 가운데 **1,832 px(8.2%)가 범위 검사를
 *   그냥 통과한다.**
 *
 *   그게 왜 치명적인가: 침식은 **최솟값**을 취한다. 살아남은 -348 m 픽셀 하나가
 *   반경 47 m 안의 배경을 -348 m 로 끌어내리고, 그러면 그 주변 **멀쩡한 땅이 전부
 *   "튐 370 m 짜리 혹"** 으로 보인다. 실제로 범위 검사만 넣고 전역을 돌렸을 때
 *   **배경이 -347.9 m · -478.1 m 인 가짜 "혹" 이 무더기로 잡혔다.**
 *   오차를 지우려던 필터가 오차를 만들어 낸 것이다.
 *
 *   그래서 **값이 아니라 생김새로 잡는다.** 손상은 **한 픽셀 폭 실선**이라
 *   좌우(또는 상하) 이웃끼리는 서로 맞고 가운데만 혼자 튄다. 실제 지형은
 *   그렇게 생기지 않는다 — 절벽은 계단 모양이라 이웃 둘이 서로 다르다.
 *   실측: -50 m 아래 픽셀의 **52.9%가 좌우 이웃이 둘 다 멀쩡**했다.
 *
 *   🔴 **깊은 값을 통째로 자르지 않는 이유**가 여기 있다. terrarium 타일은
 *   **바다 깊이도 음수로 담고 있고 그건 진짜 자료다.** 부산 앞바다는 실제로
 *   -20~-50 m 다(전체 픽셀의 0.03%). 값으로 자르면 그 진짜를 지우고, 생김새로
 *   자르면 매끄러운 해저는 남고 실선만 사라진다.
 */
/** 뾰족한 것으로 판정할 최소 낙차(m). 이웃 둘이 이만큼 안에서 서로 맞아야 한다. */
const SPIKE_DEV_M = 30
const SPIKE_AGREE_M = 10

/**
 * 🔴 **바다 바닥(음수)을 0 m 로 눌러 놓는다.** 이게 없으면 필터가 스스로 고장 난다.
 *
 * terrarium 타일은 **수심도 음수로 담는다.** 우리가 재는 것은 **길의 경사**이고
 * 길은 전부 물 위에 있으므로 수심은 쓸 데가 없다. 그런데 침식이 **최솟값**을
 * 취하기 때문에, 해안에서 반경 47 m 안에 바다가 있으면 배경이 그 수심까지
 * 끌려 내려간다. 그러면 **바닷가의 멀쩡한 땅이 전부 "튐 큰 혹"** 으로 보인다.
 *
 * 실제로 그렇게 됐다. 바닥을 안 두고 전역을 돌렸을 때 배경이 **-332.9 m ·
 * -452.5 m** 인 "혹" 이 무더기로 잡혔다. 손상 픽셀을 아무리 잘 걸러도 **진짜
 * 수심만으로도 같은 일이 일어난다** — 이건 손상 문제가 아니라 **경계 조건 문제**다.
 *
 * 0 m 로 누르면 물가에서 배경이 0 이 되고, 이는 **땅이 바다와 만나는 실제 높이**다.
 * 육지 고도는 어차피 0 이상이라 길의 경사는 하나도 안 바뀐다.
 */
const SEA_FLOOR_M = 0

function repairRange(a, w, h) {
  const bad = []
  const isBad = new Uint8Array(a.length)
  for (let i = 0; i < a.length; i++) {
    if (!(a[i] >= DEM_MIN_M && a[i] <= DEM_MAX_M)) { bad.push(i); isBad[i] = 1 }
  }
  // (2) 한 픽셀 폭 실선 — 가로 방향(세로선)과 세로 방향(가로선) 둘 다 본다
  for (let y = 0; y < h; y++) for (let x = 1; x < w - 1; x++) {
    const i = y * w + x
    if (isBad[i]) continue
    const l = a[i - 1], r = a[i + 1]
    if (Math.abs(l - r) < SPIKE_AGREE_M && Math.abs(a[i] - (l + r) / 2) > SPIKE_DEV_M) { bad.push(i); isBad[i] = 1 }
  }
  for (let y = 1; y < h - 1; y++) for (let x = 0; x < w; x++) {
    const i = y * w + x
    if (isBad[i]) continue
    const u = a[i - w], d = a[i + w]
    if (Math.abs(u - d) < SPIKE_AGREE_M && Math.abs(a[i] - (u + d) / 2) > SPIKE_DEV_M) { bad.push(i); isBad[i] = 1 }
  }
  if (!bad.length) return 0
  // 메울 때 참고하는 이웃에서 **손상 픽셀을 빼야 한다** — 실선은 여러 줄이 붙어
  // 있을 수 있어서, 손상으로 손상을 메우면 그대로 남는다.
  const good = []
  for (let i = 0; i < a.length; i += 7) if (!isBad[i]) good.push(a[i])
  good.sort((p, q) => p - q)
  const fallback = good.length ? good[good.length >> 1] : 0
  const fixed = new Float32Array(bad.length)
  for (let n = 0; n < bad.length; n++) {
    const i = bad[n], y0 = (i / w) | 0, x0 = i % w
    let val = null
    for (const rad of [2, 4, 8, 16]) {
      const acc = []
      for (let y = Math.max(0, y0 - rad); y <= Math.min(h - 1, y0 + rad); y++)
        for (let x = Math.max(0, x0 - rad); x <= Math.min(w - 1, x0 + rad); x++) {
          const j = y * w + x
          if (!isBad[j]) acc.push(a[j])
        }
      if (acc.length >= 8) { acc.sort((p, q) => p - q); val = acc[acc.length >> 1]; break }
    }
    fixed[n] = val ?? fallback
  }
  for (let n = 0; n < bad.length; n++) a[bad[n]] = fixed[n]
  return bad.length
}

/**
 * 가짜 혹을 누른다. `a` 를 제자리에서 고친다.
 *
 * 🔴 **덩어리를 "튐 > 0" 으로 그냥 이으면 안 된다. 실측으로 걸렸다.**
 *    매립지처럼 평평한 곳은 잔잔한 튐이 사방에 깔려 있어서, 그것만으로 이으면
 *    **마린시티 가짜 혹과 동백섬이 한 덩어리로 붙는다** (28,219 px = 43 ha).
 *    그러면 가짜 혹 하나가 **진짜 섬을 끌고 같이 내려간다** — 동백섬이 53 m 에서
 *    38 m 로 눌렸다. 없애려던 오차보다 더 나쁜 오차를 만든 것이다.
 *
 *    그래서 **꼭대기에서 내려가기만 한다(descend-only flood).**
 *    씨앗(튐 ≥ RISE_MIN)에서 출발해 **튐이 낮아지는 쪽으로만** 번진다.
 *    안부(saddle)까지 내려간 뒤 옆 봉우리로 **다시 올라가지 못한다.**
 *    큰 값부터 꺼내는 힙을 쓰면 물이 차는 높이가 저절로 단조 감소한다.
 */
function pressBumps(a, w, h, p) {
  const { openRadiusPx, lowlandMaxM, riseMinM } = p
  const n = w * h
  const ero = new Float32Array(n)
  const bg = new Float32Array(n)
  runExtreme(a, ero, w, h, openRadiusPx, true)    // 침식 = 창 최솟값
  runExtreme(ero, bg, w, h, openRadiusPx, false)  // 팽창 = 창 최댓값 → 열림 완성

  const top = new Float32Array(n)
  for (let i = 0; i < n; i++) top[i] = a[i] - bg[i]

  // 씨앗 = 충분히 높이 튀고 배경이 낮은 곳. 큰 것부터 처리한다.
  const seeds = []
  for (let i = 0; i < n; i++) if (top[i] >= riseMinM && bg[i] <= lowlandMaxM) seeds.push(i)
  seeds.sort((x, y) => top[y] - top[x])

  const taken = new Uint8Array(n)     // 이미 어떤 혹에 속한 픽셀
  const bumps = []
  let pressed = 0

  // 최대힙 (튐이 큰 것부터)
  const hv = new Float64Array(n), hi = new Int32Array(n)
  for (const s of seeds) {
    if (taken[s]) continue
    let hn = 0
    const push = (v, i) => {
      let c = hn++; hv[c] = v; hi[c] = i
      while (c > 0) { const par = (c - 1) >> 1; if (hv[par] >= hv[c]) break
        ;[hv[par], hv[c]] = [hv[c], hv[par]];[hi[par], hi[c]] = [hi[c], hi[par]]; c = par }
    }
    const pop = () => {
      const rv = hv[0], ri = hi[0]
      hv[0] = hv[--hn]; hi[0] = hi[hn]
      let c = 0
      for (;;) { const l = 2 * c + 1, r = l + 1; let m = c
        if (l < hn && hv[l] > hv[m]) m = l
        if (r < hn && hv[r] > hv[m]) m = r
        if (m === c) break
        ;[hv[m], hv[c]] = [hv[c], hv[m]];[hi[m], hi[c]] = [hi[c], hi[m]]; c = m }
      return [rv, ri]
    }
    const queued = []          // 이번 혹에서 힙에 넣은 것 (되돌리기용)
    const mine = []
    let level = Infinity
    push(top[s], s); queued.push(s)
    const inHeap = new Set([s])
    while (hn) {
      const [t, i] = pop()
      if (t > level) continue          // 🔴 올라가는 것은 다른 봉우리다 — 안 먹는다
      if (taken[i]) continue
      level = t
      taken[i] = 1; mine.push(i)
      const y = (i / w) | 0, x = i % w
      const tryPush = j => {
        if (taken[j] || inHeap.has(j) || top[j] <= SKIRT_EPS_M) return
        inHeap.add(j); queued.push(j); push(top[j], j)
      }
      if (x > 0) tryPush(i - 1)
      if (x < w - 1) tryPush(i + 1)
      if (y > 0) tryPush(i - w)
      if (y < h - 1) tryPush(i + w)
    }
    if (!mine.length) continue
    let peakI = mine[0]
    for (const i of mine) if (top[i] > top[peakI]) peakI = i
    const peak = a[peakI]
    for (const i of mine) { a[i] = bg[i]; pressed++ }
    bumps.push({ x: peakI % w, y: (peakI / w) | 0, peak, bg: bg[peakI], rise: top[peakI], px: mine.length })
  }
  return { pressed, bumps }
}

/**
 * 🔴 **육지에 뚫린 깊은 구멍을 메운다.** 범위 검사와 실선 검사를 지나온 뒤에도
 *    남는 손상이 있고, 그것이 **바다 바닥 누르기와 만나 최악으로 작동한다.**
 *
 *    실측 — 타일 28135_12940 (35.3496, 129.1037) 은 **고도 200~246 m 의 진짜 산**인데
 *    격자에 -2886 · -2192 · **-351 · -320** 같은 값이 흩어져 박혀 있다.
 *    앞의 둘은 `[-500,2000]` 밖이라 걸리지만 **뒤의 둘은 범위 안이라 살아남고**,
 *    살아남은 뒤 바다 바닥 규칙에 걸려 **0 m 로 눌린다.** 그러면 200 m 산 한복판에
 *    해수면 구멍이 뚫리고, 침식이 그 0 을 집어 배경을 0 으로 만들어
 *    **산 전체가 "튐 216 m 짜리 혹"** 이 된다. 실제로 그 한 자리에서만
 *    가짜 "혹" 이 14개 잡혔다.
 *
 *    그래서 **육지에서만, 아래로만** 고친다. 판정은 국소 중앙값 대비다:
 *      · 3×3 최댓값이 자기보다 `DEEP_HOLE_M` 넘게 높으면 후보
 *      · 후보를 뺀 9×9 중앙값이 `LAND_MIN_M` 보다 높아야 한다 (= 여기는 육지다)
 *      · 그 중앙값보다 `DEEP_HOLE_M` 넘게 낮으면 손상 — 중앙값으로 메운다
 *
 *    🔴 **바다는 손대지 않는다.** 물가에서는 9×9 중앙값이 낮게 나오므로
 *    "육지" 조건에서 걸러진다. 진짜 수심 자료(-20~-50 m)는 그대로 남는다 —
 *    어차피 바다 바닥 규칙이 0 으로 누르지만, **누르는 것과 지어내는 것은 다르다.**
 */
const DEEP_HOLE_M = 30
const LAND_MIN_M = 20

function repairLandHoles(a, w, h) {
  const cand = []
  const isCand = new Uint8Array(a.length)
  for (let y = 1; y < h - 1; y++) for (let x = 1; x < w - 1; x++) {
    const i = y * w + x, v = a[i]
    let mx = -Infinity
    for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
      const u = a[i + dy * w + dx]; if (u > mx) mx = u
    }
    if (mx - v > DEEP_HOLE_M) { cand.push(i); isCand[i] = 1 }
  }
  let fixedN = 0
  const fixes = []
  for (const i of cand) {
    const y0 = (i / w) | 0, x0 = i % w
    const acc = []
    for (let y = Math.max(0, y0 - 4); y <= Math.min(h - 1, y0 + 4); y++)
      for (let x = Math.max(0, x0 - 4); x <= Math.min(w - 1, x0 + 4); x++) {
        const j = y * w + x
        if (!isCand[j]) acc.push(a[j])
      }
    if (acc.length < 12) continue
    acc.sort((p, q) => p - q)
    const med = acc[acc.length >> 1]
    if (med < LAND_MIN_M) continue           // 여기는 육지가 아니다 — 손대지 않는다
    if (a[i] > med - DEEP_HOLE_M) continue   // 그만한 구멍이 아니다
    fixes.push([i, med]); fixedN++
  }
  for (const [i, v] of fixes) a[i] = v
  return fixedN
}

/**
 * 타일 하나를 청소해서 돌려준다. 옆 타일을 끌어와 여유(halo)를 두르고 계산한 뒤
 * 가운데 tileSize×tileSize 만 잘라낸다.
 *
 * @param {(tx:number,ty:number)=>Float32Array|null} rawTile 원본 타일 공급자(미터)
 * @returns {{elev:Float32Array, repaired:number, pressed:number, bumps:Array}|null}
 */
export function cleanTile(rawTile, tx, ty, opts = {}) {
  const T = opts.tileSize ?? 256
  const params = {
    openRadiusPx: opts.openRadiusPx ?? OPEN_RADIUS_PX,
    lowlandMaxM: opts.lowlandMaxM ?? LOWLAND_MAX_M,
    riseMinM: opts.riseMinM ?? RISE_MIN_M,
  }
  const pad = opts.pad ?? PAD
  const center = rawTile(tx, ty)
  if (!center) return null

  const W = T + 2 * pad
  const buf = new Float32Array(W * W)
  // 이웃 타일 3×3 을 미리 잡아 둔다. 없는 이웃은 가운데 타일로 되접어 쓴다(가장자리 복제).
  const nb = new Map()
  const get = (gx, gy) => {
    let TX = Math.floor(gx / T), TY = Math.floor(gy / T)
    const k = `${TX}_${TY}`
    let t = nb.has(k) ? nb.get(k) : (nb.set(k, rawTile(TX, TY)), nb.get(k))
    if (!t) {   // 없는 이웃 → 가운데 타일의 가장자리를 복제한다
      t = center
      gx = Math.min(tx * T + T - 1, Math.max(tx * T, gx))
      gy = Math.min(ty * T + T - 1, Math.max(ty * T, gy))
      TX = tx; TY = ty
    }
    return t[(gy - TY * T) * T + (gx - TX * T)]
  }
  for (let y = 0; y < W; y++) {
    const gy = ty * T - pad + y
    for (let x = 0; x < W; x++) buf[y * W + x] = get(tx * T - pad + x, gy)
  }

  // 🔴 범위 검사가 반드시 먼저다. 침식은 최솟값을 취하므로 -22,782 m 픽셀 하나가
  //    반경 안의 배경을 통째로 끌어내려 근방을 전부 "혹" 으로 오판하게 만든다.
  let repaired = 0
  if (opts.rangeCheck !== false) {
    repaired = repairRange(buf, W, W)          // (1) 값 범위 + 한 픽셀 폭 실선
    repaired += repairLandHoles(buf, W, W)     // (2) 육지에 뚫린 깊은 구멍
    // (3) 바다 바닥 누르기 — 위 SEA_FLOOR_M 주석 참고
    for (let i = 0; i < buf.length; i++) if (buf[i] < SEA_FLOOR_M) buf[i] = SEA_FLOOR_M
  }
  const { pressed, bumps } = opts.debump === false
    ? { pressed: 0, bumps: [] }
    : pressBumps(buf, W, W, params)

  const elev = new Float32Array(T * T)
  for (let y = 0; y < T; y++)
    for (let x = 0; x < T; x++) elev[y * T + x] = buf[(y + pad) * W + (x + pad)]

  // 가운데 타일 안에 꼭대기가 있는 혹만 보고한다 (이웃 타일에서 중복 세지 않게)
  const mine = bumps.filter(b => b.x >= pad && b.x < pad + T && b.y >= pad && b.y < pad + T)
    .map(b => ({ ...b, gx: tx * T + b.x - pad, gy: ty * T + b.y - pad }))
  return { elev, repaired, pressed, bumps: mine }
}

/**
 * `slope.mjs` · `calibrate-slope.mjs` 가 쓰는 타일 공급자.
 * 데시미터 Int16 으로 돌려준다 (원래 두 스크립트가 쓰던 형식 그대로).
 */
export function createDemReader({ demDir, zoom = 15, tileSize = 256, debump = true, ...opts } = {}) {
  const rawCache = new Map(), cleanCache = new Map()
  const stats = { tilesLoaded: 0, tilesMissing: 0, repairedPx: 0, pressedPx: 0, bumps: [] }

  const rawTile = (tx, ty) => {
    const k = `${tx}_${ty}`
    if (rawCache.has(k)) return rawCache.get(k)
    const p = join(demDir, String(zoom), `${k}.png`)
    let v = null
    if (existsSync(p)) v = terrariumToElevation(decodePNG(readFileSync(p)))
    if (rawCache.size > 900) for (const key of [...rawCache.keys()].slice(0, 400)) rawCache.delete(key)
    rawCache.set(k, v)
    return v
  }

  return {
    stats,
    /** @returns {Int16Array|null} 데시미터 */
    tile(tx, ty) {
      const k = `${tx}_${ty}`
      if (cleanCache.has(k)) return cleanCache.get(k)
      const r = cleanTile(rawTile, tx, ty, { tileSize, debump, ...opts })
      let out = null
      if (!r) stats.tilesMissing++
      else {
        stats.tilesLoaded++
        stats.repairedPx += r.repaired
        stats.pressedPx += r.pressed
        for (const b of r.bumps) stats.bumps.push(b)
        out = new Int16Array(r.elev.length)
        for (let i = 0; i < out.length; i++) out[i] = Math.round(r.elev[i] * 10)
      }
      if (cleanCache.size > 900) for (const key of [...cleanCache.keys()].slice(0, 400)) cleanCache.delete(key)
      cleanCache.set(k, out)
      return out
    },
  }
}
