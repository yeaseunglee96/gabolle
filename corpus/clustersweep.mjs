/**
 * 저장소 하나에서 군집 하이퍼파라미터를 쓸어 재고, 폴더와 겨루게 한다.
 *
 * 🔴 순수 함수다. git 도 fs 도 시계도 없다 — 커밋 목록과 그래프를 **받아서**
 *    쓴다. `measure.mjs` 가 이미 읽어둔 것을 다시 읽지 않기 위해서이기도 하고,
 *    파라미터 선택 로직을 테스트에서 30분 기다리지 않고 검증하기 위해서다.
 *
 * ── 무엇을 재는가 ────────────────────────────────────────────────────────
 *
 * 커밋 이력을 앞뒤로 자른다. **앞쪽(과거)만으로** 블록을 나누고, 뒤쪽(미래)에서
 * 실제로 같이 바뀐 쌍을 그 블록이 품고 있었는지 센다. 만든 데 쓴 엣지로
 * 채점하면 동어반복이라 아무 말도 못 한다.
 *
 * ── 🔴 자를 세 번 갈아끼운 끝에 정한 것 ──────────────────────────────────
 *
 * 실험(저장소 29개)에서 자를 두 번 잘못 골랐다. 같은 데이터가 자에 따라
 * 정반대 답을 냈다.
 *
 *   날것 적중률   폴더 압승. 폴더 하나가 전부 삼키면 100% 다 — **굵을수록 이김**
 *   lift          군집 94% 승. vcmi 에서 블록 338개·적중 7.8% 가 1등이 됐다.
 *                 분모(섞었을 때 적중)가 0 에 가까워 터진 것 — **잘수록 이김**
 *   제약 후 적중  군집 62% 승. 이것이 맞다
 *
 * 그래서 **블록 수와 얼룩 크기를 먼저 제약**하고, 통과한 것 안에서만 적중률을
 * 본다. 그리고 폴더에도 **똑같은 제약**을 걸어 겨루게 한다 — 폴더 쪽을 약하게
 * 만들어 놓고 이겼다고 하는 것은 우리 자신을 속이는 것이다.
 *
 * 이 판단의 근거가 되는 수치는 docs/CLUSTER.md 에 있다.
 */
import { buildGraph, cluster } from './cluster.mjs'
import { ari, containment, directoryPartition, shape } from './clusterscore.mjs'

/**
 * 격자.
 *
 * 🔴 γ 하한이 0.25 였을 때 **최적값이 격자의 맨 아래 칸에 몰렸다** — 29개 중
 *    11개가 γ=0.25 를 골랐다. 담 밖에 답이 있다는 신호이므로 아래로 넓혔다.
 *    지금도 하한(0.05)에 몰리면 또 넓혀야 한다. `atFloor` 로 그 사실을 함께 낸다.
 */
export const GRID = {
  algorithm: ['leiden', 'louvain'],
  objective: ['modularity', 'cpm'],
  resolution: [0.05, 0.1, 0.15, 0.25, 0.5, 0.75, 1, 1.5, 2],
  coWeight: [0, 1],
}

/** 사람이 볼 수 있는 지도인가. 군집에도 폴더에도 똑같이 건다. */
export const LIMITS = { minBlocks: 3, maxBlocks: 40, maxShare: 0.4 }

const passes = (blocks, maxShare, lim = LIMITS) =>
  blocks >= lim.minBlocks && blocks <= lim.maxBlocks && maxShare <= lim.maxShare

/**
 * 커밋 목록에서 공변경 쌍을 센다.
 *
 * `measure.mjs` 의 `readCommits` 가 주는 모양(`{files: [...]}`)을 그대로 먹는다.
 * `cochange.mjs` 를 안 쓰는 이유는 그쪽이 git 을 다시 읽기 때문이다 — 저장소
 * 하나에 git log 를 두 번 돌리면 수집 속도가 그만큼 깎인다.
 *
 * @param maxFiles 이보다 많은 파일을 건드린 커밋은 버린다. 대량 포맷팅·머지
 *                 커밋 하나가 쌍을 수만 개 만들어 분포를 통째로 덮는다
 */
export function coPairs(commits, nodeIds, { maxFiles = 40, minSupport = 1 } = {}) {
  /**
   * 🔴 경로를 이어붙여 키를 만들지 않는다. **번호로 만든다.**
   *
   * 처음에는 `` `${a} ${b}` `` 로 했는데 두 가지가 걸렸다. 파일 경로에는 공백이
   * 들어갈 수 있어 구분자가 경로 안에 나타나면 서로 다른 쌍이 같은 키가 된다
   * (치환은 곧 소유권 충돌이다 — CLAUDE.md). 그래서 NUL 로 바꿨더니 이번엔
   * "소스에 NUL 바이트가 없다" 테스트가 잡았다. 번호는 둘 다 없다.
   */
  const idx = new Map()
  for (const f of nodeIds) idx.set(f, idx.size)
  const names = [...idx.keys()]
  const N = idx.size

  const pair = new Map()
  for (const c of commits) {
    const hit = []
    for (const f of c.files ?? []) { const i = idx.get(f); if (i !== undefined) hit.push(i) }
    if (hit.length < 2 || hit.length > maxFiles) continue
    for (let i = 0; i < hit.length; i++) {
      for (let j = i + 1; j < hit.length; j++) {
        const a = hit[i] < hit[j] ? hit[i] : hit[j]
        const b = hit[i] < hit[j] ? hit[j] : hit[i]
        const key = a * N + b
        pair.set(key, (pair.get(key) ?? 0) + 1)
      }
    }
  }

  const out = []
  for (const [key, support] of pair) {
    if (support < minSupport) continue
    // 🔴 origin 을 반드시 붙인다. 없으면 buildGraph 가 거부한다 — 전에는
    //    조용히 정적 엣지로 삼켜서 coWeight 축이 통째로 죽었다.
    out.push({ source: names[Math.floor(key / N)], target: names[key % N], support, lift: 1, origin: 'cochange' })
  }
  return out
}

/**
 * 저장소 하나를 쓸어 잰다.
 *
 * @param ids          파일 경로 (graph.nodes 의 id)
 * @param staticEdges  import·참조 엣지 (graph.edges)
 * @param commits      최신이 먼저인 커밋 목록 (measure.readCommits)
 * @returns 레코드에 그대로 실을 수 있는 객체. 잴 수 없으면 `{skip}`
 */
export function sweepRepo(ids, staticEdges, commits, {
  holdout = 0.2,
  seeds = 3,
  grid = GRID,
  limits = LIMITS,
  minCommits = 80,
  minFuturePairs = 30,
} = {}) {
  if (ids.length < 40) return { skip: `파일 ${ids.length}개` }
  if (commits.length < minCommits) return { skip: `커밋 ${commits.length}개` }

  const cut = Math.max(15, Math.floor(commits.length * holdout))
  // readCommits 는 **최신이 먼저**다. 앞쪽 slice 가 미래, 뒤쪽이 과거 — 직관과 반대다.
  const future = commits.slice(0, cut)
  const past = commits.slice(cut)

  const nodeIds = new Set(ids)
  const pastCo = coPairs(past, nodeIds, { minSupport: 2 })
  const futureCo = coPairs(future, nodeIds, { minSupport: 1 })
  if (futureCo.length < minFuturePairs) return { skip: `미래 공변경 ${futureCo.length}쌍` }

  const hit = (m) => containment(ids, futureCo, m).weighted

  // ── 폴더 기준선. 깊이를 셋 다 재서 폴더 쪽을 최대한 세운다 ────────────────
  const dirs = [1, 2, 3].map((depth) => {
    const m = directoryPartition(ids, { depth })
    const sh = shape(m)
    return { depth, blocks: sh.usableBlocks, maxShare: sh.maxShare, heldOut: hit(m), m }
  })
  const dirOk = dirs.filter((d) => d.heldOut != null && passes(d.blocks, d.maxShare, limits))
  const dirBest = dirOk.sort((a, b) => b.heldOut - a.heldOut)[0] ?? null

  // ── 격자 ─────────────────────────────────────────────────────────────────
  const cands = []
  for (const coWeight of grid.coWeight) {
    const g = buildGraph(ids, [...staticEdges, ...pastCo], { coWeight, minSupport: 2 })
    for (const algorithm of grid.algorithm) {
      for (const objective of grid.objective) {
        for (const resolution of grid.resolution) {
          const runs = []
          for (let s = 1; s <= seeds; s++) runs.push(cluster(algorithm, g, { resolution, objective, seed: s }).membership)
          const m = runs[0]
          const sh = shape(m)
          let acc = 0, n = 0
          for (let i = 0; i < runs.length; i++) for (let j = i + 1; j < runs.length; j++) {
            const v = ari(runs[i], runs[j]); if (v != null) { acc += v; n++ }
          }
          cands.push({
            algorithm, objective, resolution, coWeight,
            blocks: sh.usableBlocks,
            maxShare: +sh.maxShare.toFixed(3),
            singletonShare: +sh.singletonShare.toFixed(3),
            stability: n ? +(acc / n).toFixed(3) : null,
            ariDir: dirBest ? +ari(m, dirBest.m).toFixed(3) : null,
            heldOut: +hit(m).toFixed(4),
          })
        }
      }
    }
  }

  const ok = cands.filter((c) => Number.isFinite(c.heldOut) && passes(c.blocks, c.maxShare, limits))
  const best = ok.sort((a, b) => b.heldOut - a.heldOut)[0] ?? null

  return {
    commits: { past: past.length, future: future.length },
    coPairs: { past: pastCo.length, future: futureCo.length },
    dirs: dirs.map(({ m, ...d }) => ({ ...d, heldOut: d.heldOut == null ? null : +d.heldOut.toFixed(4) })),
    dirBest: dirBest ? { depth: dirBest.depth, blocks: dirBest.blocks, heldOut: +dirBest.heldOut.toFixed(4) } : null,
    best,
    // 🔴 제약을 아무도 통과 못 한 것과 "안 재봤다" 는 다른 말이다. 구별해서 남긴다
    passed: ok.length,
    tried: cands.length,
    /**
     * 군집이 폴더를 이겼는가. 이 저장소가 이 제품을 쓸 이유가 있는가와 같은 말이다.
     * 폴더가 제약을 못 통과하면(예: 최상위 폴더 하나뿐) null — 이겼다고 치지 않는다.
     */
    beatsDir: best && dirBest ? +(best.heldOut - dirBest.heldOut).toFixed(4) : null,
    /**
     * 최적값이 격자 끝에 붙었나. 붙었으면 담 밖에 답이 있다는 뜻이라
     * 이 저장소의 γ 는 "격자가 허락한 최선" 이지 최적이 아니다.
     */
    atFloor: best ? best.resolution === grid.resolution[0] : null,
    atCeil: best ? best.resolution === grid.resolution[grid.resolution.length - 1] : null,
    candidates: cands,
  }
}
