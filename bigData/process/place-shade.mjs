#!/usr/bin/env node
/**
 * 장소 그늘 — 가로수 수종·그루수로 장소별 그늘을 낸다 (S15P21E201-1182)
 *
 * 여름 부산에서 그늘은 「조금 덜 쾌적」이 아니라 **걷느냐 마느냐**를 가른다.
 * 그리고 **계산으로는 못 만든다** — 나무가 어디 심겼는지는 위성 고도에도 OSM 태그에도
 * 거의 없다. 받아야만 알 수 있다.
 *
 *   그늘밀도 = Σ(수종 가중치 × 그루 수) ÷ 식재거리(m)        [가중그루/m]
 *   그늘점수 = 반경 500m 안 구간들의 거리가중 평균 밀도를, 부산 안에서 순위로
 *              100 = 그늘 많음 · 0 = 그늘 적음
 *
 * 🔴 **`process/shade.mjs` 와 다른 것이다.** 그쪽은 **도로 구간**(OSM way)마다 그늘을 내고,
 *    이 파일은 **장소**마다 낸다. 화면이 묻는 것은 길이 아니라 장소다.
 *    그리고 그쪽은 `segment-slope.ndjson` 이 필요한데 이 PC 의 그 파일에는 **좌표가 없다**
 *    (67,676줄 전부). 여기서는 가로수 기록의 좌표를 그대로 쓰므로 그 단계가 필요 없다.
 *
 * ── 🔴 식재거리 단위가 섞여 있다. 이 파일의 절반이 그것이다 ──────────────────
 *
 * 원본 `plant_distance` 에 **km 와 m 가 섞여 있다.** 그대로 나누면 이런 값이 나온다.
 *
 *     고관로   14그루 / 0.008  →  1,150그루/m     ← 울타리도 이렇게 빽빽하지 않다
 *     톳고개로 186그루 / 0.4   →    348그루/m
 *     광안대로   0그루 / 210   →        0그루/m   ← 이쪽은 m 가 맞다
 *
 * 값의 크기 분포가 두 덩어리로 갈린다 (2026-09-17, 619건):
 *
 *     <1: 171건 · 1~10: 84건 · 10~100: 10건 · 100~1000: 200건 · 1000~10000: 146건
 *
 * 🔴 **물리적 한계로 가른다.** 가로수 간격은 보통 4~10m 라 **1m 마다 한 그루를 넘을 수
 *    없다.** m 로 읽어 그 한계를 넘으면 km 로 다시 읽는다.
 *
 *     m 로 읽은 것 357건 · km 로 다시 읽은 것 262건 · 그래도 불가능 4건 → 버린다
 *     고친 뒤 그루밀도 중앙 0.118그루/m = **8.4m 마다 한 그루**   ← 규칙이 맞다는 증거
 *
 * 🔴 **안 고쳐지는 4건은 조용히 통과시키지 않고 버린다.** 그 값이 남으면 주변 장소가
 *    통째로 「부산 최고 그늘」이 된다. 애매하면 막는 쪽으로 기운다.
 *
 * ── 🔴 왜 반경이 500m 인가 (조용함 200 · 로컬 300 보다 넓다) ─────────────────
 *
 * 가로수 기록 하나는 **도로 구간 전체**(예: 송정1호교 → 기장체육관)를 **점 하나**로
 * 대표한다. 그보다 좁게 잡으면 **없는 정밀도를 있는 척**하는 것이다.
 *
 * 그리고 835건이 부산 전체라 성기다. 반경별 실측 커버리지 (2026-09-17):
 *
 *     반경 200m   관광공사 34% · 상가 42%
 *     반경 300m            48% ·      63%
 *     반경 500m            66% ·      86%      ← 고른 값
 *     반경 1000m           84% ·      97%      (너무 넓다 — 다른 동네 가로수를 끌어온다)
 *
 * ── 🔴 기록이 없는 것과 그늘이 없는 것은 다르다 ─────────────────────────────
 *
 * 반경 안에 가로수 기록이 없으면 **줄을 만들지 않는다.** 「조사가 안 된 길」과 「나무가
 * 없는 길」을 구분할 방법이 없기 때문이다. `config/tree-shade-weights.json` 의
 * *「결측과 0의 구별」* 절이 같은 말을 한다 — *「그늘 0 과 모름은 다른 것이다」*.
 *
 * 🔴 다만 **그루 수가 0 으로 명시된 구간은 0 으로 센다.** 그건 「없다고 확인된 것」이다.
 *
 * ── 🔴 이 값이 못 하는 것 ───────────────────────────────────────────────────
 *
 * · **가로수만 본다.** 공원 숲·건물 그림자·차양은 안 센다. 건물 그림자는 시각에 따라
 *   답이 달라서 아예 다른 계산이다 (`process/shadow.mjs`)
 * · **계절을 모른다.** 낙엽수는 겨울에 그늘이 없는데 같은 값이 나온다.
 *   가중치 파일에 `낙엽` 칸이 있으니 나중에 계절을 가르려면 그것을 쓴다
 * · **점 하나가 구간 전체를 대표한다.** 긴 도로의 한쪽 끝에 있는 장소도 같은 값을 받는다
 * · **순위라서 상대값이다.** 「그늘 70」은 「부산에서 상위 30%」이지 절대값이 아니다
 *
 * 입력
 *   data/raw/trees/street-trees.ndjson   collect/street-trees.mjs 가 만든다
 *   config/tree-shade-weights.json       이 저장소에 있다 (38종)
 *   data/raw/tourapi/tourapi-busan.ndjson
 *   data/staged/place-slope-sbiz.ndjson  운영 DB 의 상가 장소 명단
 *   data/sbiz/부산_202606.csv             상가 좌표
 *
 * 실행
 *   node process/place-shade.mjs
 *   node process/place-shade.mjs --radius 300
 *
 * 종료 코드: 0 냈다 / 1 불변식 깨짐 / 2 입력 없음
 */
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { createReadStream, existsSync } from 'node:fs'
import { createInterface } from 'node:readline'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const IN_TREES = join(ROOT, 'data/raw/trees/street-trees.ndjson')
const IN_WEIGHTS = join(ROOT, 'config/tree-shade-weights.json')
const IN_TOURAPI = join(ROOT, 'data/raw/tourapi/tourapi-busan.ndjson')
const IN_ROSTER = join(ROOT, 'data/staged/place-slope-sbiz.ndjson')
const IN_SBIZ = join(ROOT, 'data/sbiz/부산_202606.csv')
const OUT = join(ROOT, 'data/staged')

const EXIT = { OK: 0, INVARIANT: 1, INPUT: 2 }
const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a)

const argIdx = process.argv.indexOf('--radius')
const RADIUS_M = argIdx > 0 ? Number(process.argv[argIdx + 1]) : 500

/** 🔴 가로수 간격의 물리적 하한. 1m 마다 한 그루를 넘으면 가로수가 아니다. */
const MAX_TREES_PER_M = 1

const M_PER_DEG_LAT = 110574
const mPerDegLon = (lat) => 111320 * Math.cos((lat * Math.PI) / 180)
const distM = (aLat, aLon, bLat, bLon) =>
  Math.hypot((aLat - bLat) * M_PER_DEG_LAT, (aLon - bLon) * mPerDegLon(aLat))

/** 🔴 따옴표를 존중하는 파서. split(',') 는 상호명 속 쉼표에서 칸이 밀린다. */
function parseCsvLine(line) {
  const out = []
  let cur = '', q = false
  for (let i = 0; i < line.length; i++) {
    const c = line[i]
    if (q) { if (c === '"') { if (line[i + 1] === '"') { cur += '"'; i++ } else q = false } else cur += c }
    else if (c === '"') q = true
    else if (c === ',') { out.push(cur); cur = '' }
    else cur += c
  }
  out.push(cur)
  return out
}

async function main() {
  log(`장소 그늘 — 반경 ${RADIUS_M}m · 수종 가중 그루밀도를 순위로`)

  for (const [f, hint] of [
    [IN_TREES, 'node collect/street-trees.mjs'],
    [IN_WEIGHTS, '이 파일이 저장소에 있어야 합니다'],
    [IN_TOURAPI, 'node collect/tourapi.mjs'],
    [IN_ROSTER, 'node process/place-slope-sbiz.mjs'],
    [IN_SBIZ, '소상공인시장진흥공단 상가정보'],
  ]) {
    if (!existsSync(f)) {
      log(`🔴 입력이 없습니다: ${f}`)
      log(`   먼저 돌리십시오 — ${hint}`)
      process.exitCode = EXIT.INPUT
      return
    }
  }

  const W = JSON.parse(await readFile(IN_WEIGHTS, 'utf8')).weights
  if (!W) { log('🔴 가중치 파일에 weights 가 없습니다.'); process.exitCode = EXIT.INPUT; return }
  const SPECIES = Object.keys(W)

  // ── 가로수 구간 ────────────────────────────────────────────────────
  const secs = []
  const stat = { rows: 0, noCoord: 0, noDist: 0, asM: 0, asKm: 0, dropped: 0, zero: 0 }
  {
    const rl = createInterface({ input: createReadStream(IN_TREES), crlfDelay: Infinity })
    for await (const line of rl) {
      if (!line.trim()) continue
      let o; try { o = JSON.parse(line) } catch { continue }
      let raw = o.raw; try { raw = JSON.parse(raw) } catch { /* 이미 객체일 수 있다 */ }
      const item = raw?.response?.body?.items?.item ?? raw?.body?.items?.item
      for (const x of Array.isArray(item) ? item : item ? [item] : []) {
        stat.rows++
        const lat = Number(x.lat), lon = Number(x.lng)
        if (!Number.isFinite(lat) || !Number.isFinite(lon) || !lat || !lon) { stat.noCoord++; continue }

        let trees = 0, weighted = 0
        for (const k of SPECIES) {
          const n = Number(x[k])
          if (Number.isFinite(n) && n > 0) { trees += n; weighted += n * W[k].w }
        }

        const pd = Number(x.plant_distance)
        if (!Number.isFinite(pd) || pd <= 0) { stat.noDist++; continue }

        // 🔴 단위 정규화 — 머리말 참고
        let lenM = pd, unit = 'm'
        if (trees / lenM > MAX_TREES_PER_M) { lenM = pd * 1000; unit = 'km→m'; stat.asKm++ }
        else stat.asM++
        if (trees / lenM > MAX_TREES_PER_M) {
          // 🔴 어느 쪽으로 읽어도 불가능하다. 조용히 통과시키지 않는다
          stat.dropped++
          if (unit === 'km→m') stat.asKm--; else stat.asM--
          continue
        }
        if (trees === 0) stat.zero++
        secs.push({ lat, lon, lenM, density: weighted / lenM, trees, name: x.loc_nm ?? '' })
      }
    }
  }
  log(`  가로수 구간 ${secs.length}개 / 원본 ${stat.rows}건`)
  log(`     좌표없음 ${stat.noCoord} · 식재거리없음 ${stat.noDist} · m 로 읽음 ${stat.asM} · km 로 고침 ${stat.asKm} · 🔴 버림 ${stat.dropped} · 그루수 0 인 구간 ${stat.zero}`)
  if (!secs.length) { log('🔴 쓸 수 있는 구간이 하나도 없습니다.'); process.exitCode = EXIT.INPUT; return }

  // ── 격자 ───────────────────────────────────────────────────────────
  const CELL_DEG = 0.01 // ≈ 1.1km — 반경이 넓으니 칸도 크게
  const grid = new Map()
  for (const s of secs) {
    const k = `${Math.floor(s.lat / CELL_DEG)},${Math.floor(s.lon / CELL_DEG)}`
    const b = grid.get(k); if (b) b.push(s); else grid.set(k, [s])
  }
  const span = Math.ceil(RADIUS_M / (CELL_DEG * M_PER_DEG_LAT)) + 1

  /** 반경 안 구간들의 **식재거리 가중** 평균 밀도. 긴 구간이 더 무겁다. */
  function shadeAt(lat, lon) {
    let wsum = 0, len = 0, n = 0
    const ci = Math.floor(lat / CELL_DEG), cj = Math.floor(lon / CELL_DEG)
    for (let i = ci - span; i <= ci + span; i++) {
      for (let j = cj - span; j <= cj + span; j++) {
        for (const s of grid.get(`${i},${j}`) ?? []) {
          if (distM(lat, lon, s.lat, s.lon) > RADIUS_M) continue
          wsum += s.density * s.lenM; len += s.lenM; n++
        }
      }
    }
    if (!n) return null // 🔴 기록이 없는 것과 그늘이 없는 것은 다르다
    return { density: wsum / len, sections: n, plantedM: Math.round(len) }
  }

  // ── 장소 ───────────────────────────────────────────────────────────
  const tour = []
  {
    const rl = createInterface({ input: createReadStream(IN_TOURAPI), crlfDelay: Infinity })
    for await (const line of rl) {
      if (!line) continue
      let o; try { o = JSON.parse(line) } catch { continue }
      if (o.op !== 'areaBasedList2' || !o.raw) continue
      let body; try { body = JSON.parse(o.raw)?.response?.body } catch { continue }
      const item = body?.items?.item
      for (const it of Array.isArray(item) ? item : item ? [item] : []) {
        const lat = Number(it.mapy), lon = Number(it.mapx)
        if (!it.contentid || !Number.isFinite(lat) || !Number.isFinite(lon)) continue
        tour.push({ key: 'TOURAPI', id: String(it.contentid), title: it.title ?? '', lat, lon })
      }
    }
  }
  const sbiz = []
  {
    const roster = new Map()
    const rl0 = createInterface({ input: createReadStream(IN_ROSTER), crlfDelay: Infinity })
    for await (const line of rl0) {
      if (!line.trim()) continue
      try { const o = JSON.parse(line); if (o.sourceId) roster.set(String(o.sourceId), o.title ?? '') } catch { /* 건너뜀 */ }
    }
    const rl = createInterface({ input: createReadStream(IN_SBIZ, 'utf8'), crlfDelay: Infinity })
    let h = null, iId, iLon, iLat, iName
    for await (const line of rl) {
      if (!line.trim()) continue
      const c = parseCsvLine(line)
      if (!h) {
        h = c.map((x) => x.replace(/^"|"$/g, ''))
        iId = h.indexOf('상가업소번호'); iLon = h.indexOf('경도'); iLat = h.indexOf('위도'); iName = h.indexOf('상호명')
        if (iId < 0 || iLon < 0 || iLat < 0) { log('🔴 csv 머리줄에서 칸을 못 찾았습니다.'); process.exitCode = EXIT.INPUT; return }
        continue
      }
      if (!roster.has(c[iId])) continue
      const lat = Number(c[iLat]), lon = Number(c[iLon])
      if (!Number.isFinite(lat) || !Number.isFinite(lon)) continue
      sbiz.push({ key: 'SBIZ', id: c[iId], title: roster.get(c[iId]) || c[iName] || '', lat, lon })
    }
  }
  log(`  장소 — 관광공사 ${tour.length.toLocaleString()}곳 · 상가 ${sbiz.length.toLocaleString()}곳`)

  // ── 계산 ───────────────────────────────────────────────────────────
  const all = [...tour, ...sbiz]
  let noData = 0
  for (const p of all) {
    const r = shadeAt(p.lat, p.lon)
    if (!r) { noData++; continue }
    p.density = r.density; p.sections = r.sections; p.plantedM = r.plantedM
  }
  const scored = all.filter((p) => p.density != null)
  if (!scored.length) { log('🔴 한 곳도 값을 못 냈습니다.'); process.exitCode = EXIT.INVARIANT; return }

  // 🔴 순위로 바꾼다 — shade.mjs 가 정한 원칙이다 (절대 기준선을 손으로 안 적는다)
  const sorted = scored.map((p) => p.density).sort((a, b) => a - b)
  const pct = (v) => { let lo = 0, hi = sorted.length; while (lo < hi) { const m = (lo + hi) >> 1; if (sorted[m] < v) lo = m + 1; else hi = m } return lo / sorted.length }
  const scoreOf = (d) => +(pct(d) * 100).toFixed(1)
  for (const p of scored) p.score = scoreOf(p.density)

  await mkdir(OUT, { recursive: true })
  const toLine = (p) => JSON.stringify(
    p.key === 'TOURAPI'
      ? { contentid: p.id, title: p.title, featureType: 'SHADE_SCORE', evidenceStatus: 'ESTIMATED',
          shadeScore: p.score, treeDensity: +p.density.toFixed(4), sections: p.sections, plantedM: p.plantedM, radiusM: RADIUS_M }
      : { sourceType: 'SBIZ', sourceId: p.id, title: p.title, featureType: 'SHADE_SCORE', evidenceStatus: 'ESTIMATED',
          shadeScore: p.score, treeDensity: +p.density.toFixed(4), sections: p.sections, plantedM: p.plantedM, radiusM: RADIUS_M })
  const tL = scored.filter((p) => p.key === 'TOURAPI').map(toLine)
  const sL = scored.filter((p) => p.key === 'SBIZ').map(toLine)
  await writeFile(join(OUT, 'place-shade.ndjson'), tL.join('\n') + (tL.length ? '\n' : ''))
  await writeFile(join(OUT, 'place-shade-sbiz.ndjson'), sL.join('\n') + (sL.length ? '\n' : ''))

  /**
   * 🔴 대조군 — **자료가 스스로 고르게 한다.** 이름을 손으로 적지 않는다.
   *
   * 그늘 밀도가 가장 진한 구간 옆 장소는 높게, 그루 수가 **0 으로 확인된** 구간만
   * 주변에 있는 장소는 낮게 나와야 한다. 반경·가중·부호가 틀리면 이게 깨진다.
   */
  const dense = [...secs].sort((a, b) => b.density - a.density).slice(0, 10)
  const near = (sec) => scored.filter((p) => distM(sec.lat, sec.lon, p.lat, p.lon) <= RADIUS_M)
  const denseSide = dense.flatMap(near)
  const zeroSecs = secs.filter((s) => s.trees === 0)
  const zeroSide = zeroSecs.flatMap(near).filter((p) => !denseSide.includes(p))
  const avg = (a) => (a.length ? a.reduce((t, p) => t + p.score, 0) / a.length : null)
  const denseAvg = avg(denseSide), zeroAvg = avg(zeroSide)
  const gap = denseAvg != null && zeroAvg != null ? denseAvg - zeroAvg : null

  log('')
  log(`저장 완료 — 관광공사 ${tL.length.toLocaleString()}곳 · 상가 ${sL.length.toLocaleString()}곳 / 가로수 기록 없음 ${noData.toLocaleString()}곳`)
  log(`  덮은 비율 — 관광공사 ${(tL.length / tour.length * 100).toFixed(0)}% · 상가 ${(sL.length / sbiz.length * 100).toFixed(0)}%`)
  log(`  대조군 — 가장 진한 구간 옆 ${denseSide.length}곳 평균 ${denseAvg?.toFixed(1) ?? '값없음'}`)
  log(`  대조군 — 그루수 0 으로 확인된 구간 옆 ${zeroSide.length}곳 평균 ${zeroAvg?.toFixed(1) ?? '값없음'}`)
  if (gap != null) log(`  차이 ${gap.toFixed(1)} (20 이상이어야 한다)`)

  stamp(join(ROOT, 'data/staged/_place-shade-run'), {
    step: 'process/place-shade',
    inputs: [IN_TREES, IN_WEIGHTS, IN_TOURAPI, IN_ROSTER, IN_SBIZ],
    params: { radiusM: RADIUS_M, maxTreesPerM: MAX_TREES_PER_M, stat: 'percentile rank of planted-length-weighted mean weighted-tree density' },
    result: { sections: secs.length, sectionStats: stat, tourapi: tL.length, sbiz: sL.length, noData,
      control: { denseSide: denseSide.length, denseAvg, zeroSide: zeroSide.length, zeroAvg, gap } },
  })

  // ── 불변식 ─────────────────────────────────────────────────────────
  if (gap == null) {
    log('')
    log('🔴 대조군을 못 세웠습니다. 진한 구간 옆이나 0그루 구간 옆에 장소가 없습니다.')
    process.exitCode = EXIT.INVARIANT
    return
  }
  if (gap < 20) {
    log('')
    log('🔴 대조군이 어긋났습니다. 가로수가 진한 곳이 0그루인 곳보다 그늘지게 안 나옵니다.')
    log(`   진한 구간 옆 ${denseAvg.toFixed(1)} − 0그루 구간 옆 ${zeroAvg.toFixed(1)} = ${gap.toFixed(1)}`)
    process.exitCode = EXIT.INVARIANT
    return
  }
  log('  대조군 통과')
}

main().catch((e) => { console.error('치명:', e?.stack || e); process.exit(EXIT.INVARIANT) })
