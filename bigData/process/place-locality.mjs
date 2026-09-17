#!/usr/bin/env node
/**
 * 장소 로컬성 — 주변 업종 구성으로 「관광지 ↔ 동네」를 가른다 (S15P21E201-1164)
 *
 * 취향 축 LOCALITY_SCORE 가 배포 DB 에 한 줄도 없다. 이 축은 **「관광지 ↔ 동네」 양 끝을
 * 담는 유일한 축**이다 — 사용자가 「로컬한 곳」을 고르면 지금은 아무것도 안 나온다.
 *
 *   로컬성 = 반경 300m 안 가게들의 업종 가중치 평균을, 부산 안에서 순위로 바꾼 값
 *            100 = 동네 · 0 = 관광지
 *
 * 🔴 **한 파일이 두 출처를 다 돌린다.** 조용함은 관광공사부터 만들고 상가를 나중에 붙였는데,
 *    그 상가가 **운영 DB 의 88%** 라 그 사이 동안 축이 반쪽이었다. 여기서는 **빠질 수가 없게**
 *    한 번에 돈다. 산출물만 둘로 나눠 쓴다 (적재하는 쪽 열쇠 체계가 다르기 때문이다).
 *
 * ── 🔴 틀린 가설을 지우지 않고 남긴다 ────────────────────────────────────────
 *
 * 처음에는 **영업 지속기간**으로 가려고 했다. 행정안전부 인허가 데이터에 `인허가일자` 와
 * `폐업일자` 가 있어서 *「동네는 오래된 가게가 많고 관광지는 자주 바뀐다」* 고 본 것이다.
 * 그럴듯했는데 **실측이 반대로 나왔다** (2026-09-17, 반경 200m 영업연수 중앙값):
 *
 *     자갈치시장(관광지)   19.8년   ← 제일 오래됨
 *     남포동(관광지)       14.3년
 *     괴정동(주거)         12.4년
 *     사직동(주거)          1.6년   ← 제일 짧음
 *
 * **오래된 관광지가 있고 새로 생긴 주거지가 있다.** 가게 나이는 「관광지↔동네」가 아니라
 * **「그 상권이 언제 생겼나」**를 잰다. 다른 축이다.
 *
 * 🔴 인허가 수집이 헛일이라는 뜻은 아니다 — 좌표 변환 검증(상가정보와 짝 15,218개, 오차
 *    중앙값 1.7m)에 쓰였고 음식 랭킹 쪽에 남는다. **로컬 축의 재료가 아닐 뿐이다.**
 *
 * ── 그래서 무엇으로 가르나 — 업종 구성 ──────────────────────────────────────
 *
 * 상가정보의 **상권업종대분류**를 쓴다. 우리가 만든 분류가 아니라 이미 붙어 있는 것이다.
 *
 *   **숙박이 관광 신호다.** 호텔·모텔·게스트하우스는 **밖에서 온 사람**이 있어야 선다.
 *   동네 사람은 자기 동네에서 안 잔다.
 *
 *   **교육이 동네 신호다.** 학원·교습소는 **사는 사람**이 있어야 생긴다. 관광객은 학원에
 *   안 다닌다.
 *
 * 부산 192개 행정동 전수 실측이 그것을 받쳐 준다 (2026-09-17):
 *
 *     숙박 상위   청학1동 16.3 · 송정동 14.7 · 광안2동 13.4 · 민락동 12.3 · 암남동 8.8
 *                 — 전부 해변 아니면 항만·역 주변
 *     숙박 0%     만덕3동(교육 7.4) · 괴정2동(6.2) · 덕천3동(5.4) · 용호2동 · 부곡4동
 *                 — 전부 주거지이고 교육이 높다
 *
 * 가중치는 코드가 아니라 **config/locality-weights.json** 에 있다. 숫자가 임의라는 사실이
 * 파일로 드러나야 다음 사람이 측정값으로 읽지 않는다. shade.mjs · place-quietness.mjs 와 같다.
 *
 * ── 🔴 왜 날 비율이 아니라 순위인가 ─────────────────────────────────────────
 *
 * 숙박 비율 분포가 한쪽으로 심하게 쏠려 있다 — **최소 0.0% · 중앙 0.8% · 최대 16.3%**.
 * 날 비율을 0~100 으로 펴면 **거의 모든 장소가 「동네」 끝에 뭉치고** 사용자가 손잡이를
 * 어디에 놓아도 결과가 안 바뀐다.
 *
 * 그래서 **부산 안에서의 순위(백분위)** 로 바꾼다. process/shade.mjs 가 같은 이유로 같은
 * 선택을 했다 — *「백분위는 사람이 정한 상한이 아니라 데이터가 정한다」*.
 *
 * 🔴 **그래서 이 값은 상대값이다.** 「이 장소의 로컬 점수 70」은 *「부산의 다른 장소들과
 *    견주어 상위 30%」* 라는 뜻이지 절대적인 무엇이 아니다. 장소가 늘면 점수가 움직인다.
 *    화면이 「부산에서 로컬한 편」 처럼 읽히게 써야 한다.
 *
 * ── 🔴 이 값이 못 하는 것 ───────────────────────────────────────────────────
 *
 * · **가게 자체를 안 본다.** 관광지 한복판의 오래된 동네 밥집도 주변이 관광지면 낮게 나온다.
 *   이 값은 「이 **동네**가 로컬한가」이지 「이 **가게**가 로컬한가」가 아니다
 * · **관광객이 실제로 몇 명인지 모른다.** 숙박 밀도는 「관광지로 개발됐나」이지 사람 수가 아니다
 * · **업종 분류가 2026-06 수집분에 묶여 있다.** 새로 생긴 가게는 안 들어 있다
 *
 * 입력
 *   data/sbiz/부산_202606.csv                 상가정보 (업종·좌표)
 *   config/locality-weights.json
 *   data/raw/tourapi/tourapi-busan.ndjson     관광공사 장소
 *   data/staged/place-slope-sbiz.ndjson       운영 DB 에 있는 상가 장소 명단
 *
 * 실행
 *   node process/place-locality.mjs
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
const IN_SBIZ = join(ROOT, 'data/sbiz/부산_202606.csv')
const IN_WEIGHTS = join(ROOT, 'config/locality-weights.json')
const IN_TOURAPI = join(ROOT, 'data/raw/tourapi/tourapi-busan.ndjson')
const IN_ROSTER = join(ROOT, 'data/staged/place-slope-sbiz.ndjson')
const OUT = join(ROOT, 'data/staged')

const EXIT = { OK: 0, INVARIANT: 1, INPUT: 2 }
const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a)

const M_PER_DEG_LAT = 110574
const mPerDegLon = (lat) => 111320 * Math.cos((lat * Math.PI) / 180)
const distM = (aLat, aLon, bLat, bLon) =>
  Math.hypot((aLat - bLat) * M_PER_DEG_LAT, (aLon - bLon) * mPerDegLon(aLat))

/**
 * 🔴 따옴표를 존중하는 한 줄 파서. `split(',')` 로는 안 된다 — 상호명에 쉼표가 든 가게가
 *    있고, 그러면 그 줄부터 칸이 밀려 경도·위도 자리에 엉뚱한 값이 들어온다. 조용히 틀린다.
 */
function parseCsvLine(line) {
  const out = []
  let cur = '', inQuote = false
  for (let i = 0; i < line.length; i++) {
    const c = line[i]
    if (inQuote) {
      if (c === '"') { if (line[i + 1] === '"') { cur += '"'; i++ } else inQuote = false }
      else cur += c
    } else if (c === '"') inQuote = true
    else if (c === ',') { out.push(cur); cur = '' }
    else cur += c
  }
  out.push(cur)
  return out
}

/**
 * 🔴 대조군 — 관광지 쪽 좌표는 **tourapi 수집본**에서, 동네 쪽은 **상가정보로 계산한
 *    행정동 중심**에서 얻는다. 둘 다 자료에서 나온다. **기억으로 적지 않는다.**
 *
 *    이 규칙을 오늘 한 번 어겼다 — 주거지 좌표를 손으로 적었다가 표본이 8개밖에 안 잡혀
 *    잘못된 결론을 낼 뻔했다. 그래서 여기서는 이름만 적고 좌표는 코드가 찾는다.
 *
 *    🔴 이름도 **수집본에 실제로 있는 것**을 쓴다. 처음에 「해운대해수욕장」·「광안리해수욕장」
 *    으로 적었다가 셋 다 값없음이 나왔다 — 수집본에는 그 제목이 없다. 아래는 실제로 있는
 *    제목을 확인하고 적은 것이다. **불변식이 그것을 잡아 줬다.**
 */
const CONTROL_TOURIST_NAMES = ['부산 송도해수욕장', '광안리해변 테마거리', '국제시장']
const CONTROL_LOCAL_DONGS = ['만덕3동', '괴정2동', '덕천3동']

/** 관광지 무리가 동네 무리보다 이만큼은 낮아야 한다 (100점 만점). */
const CONTROL_MIN_GAP = 25

async function main() {
  log('장소 로컬성 — 업종 구성으로 「관광지 ↔ 동네」')

  for (const [f, hint] of [
    [IN_SBIZ, '소상공인시장진흥공단 상가정보. config/sources.json 의 sbiz-poi 참고'],
    [IN_WEIGHTS, '이 파일이 저장소에 있어야 합니다'],
    [IN_TOURAPI, 'node collect/tourapi.mjs'],
    [IN_ROSTER, 'node process/place-slope-sbiz.mjs — 상가 장소 명단이 여기서 나온다'],
  ]) {
    if (!existsSync(f)) {
      log(`🔴 입력이 없습니다: ${f}`)
      log(`   먼저 돌리십시오 — ${hint}`)
      process.exitCode = EXIT.INPUT
      return
    }
  }

  const cfg = JSON.parse(await readFile(IN_WEIGHTS, 'utf8'))
  const W = cfg.weights
  const RADIUS_M = cfg.radiusM
  const MIN_SHOPS = cfg.minShops
  if (!W || !Number.isFinite(RADIUS_M) || !Number.isFinite(MIN_SHOPS)) {
    log('🔴 가중치 파일에 weights · radiusM · minShops 가 있어야 합니다.')
    process.exitCode = EXIT.INPUT
    return
  }
  log(`  반경 ${RADIUS_M}m · 최소 가게 ${MIN_SHOPS}곳`)

  // ── 가게 ───────────────────────────────────────────────────────────
  const shops = []
  const dong = new Map() // 행정동 → 중심좌표 (대조군이 쓴다)
  const rosterCoord = new Map() // 상가업소번호 → 좌표
  let unknownCat = 0
  const unknownKinds = new Map()
  {
    const roster = new Map()
    const rl0 = createInterface({ input: createReadStream(IN_ROSTER), crlfDelay: Infinity })
    for await (const line of rl0) {
      if (!line.trim()) continue
      try { const o = JSON.parse(line); if (o.sourceId) roster.set(String(o.sourceId), o.title ?? '') } catch { /* 건너뜀 */ }
    }

    const rl = createInterface({ input: createReadStream(IN_SBIZ, 'utf8'), crlfDelay: Infinity })
    let h = null, iId, iCat, iDong, iLon, iLat, iName
    for await (const line of rl) {
      if (!line.trim()) continue
      const c = parseCsvLine(line)
      if (!h) {
        h = c.map((x) => x.replace(/^"|"$/g, ''))
        iId = h.indexOf('상가업소번호'); iCat = h.indexOf('상권업종대분류명')
        iDong = h.indexOf('행정동명'); iLon = h.indexOf('경도'); iLat = h.indexOf('위도')
        iName = h.indexOf('상호명')
        if (iId < 0 || iCat < 0 || iLon < 0 || iLat < 0) {
          log(`🔴 csv 머리줄에서 칸을 못 찾았습니다 — 업소번호=${iId} 업종=${iCat} 경도=${iLon} 위도=${iLat}`)
          process.exitCode = EXIT.INPUT
          return
        }
        continue
      }
      const lat = Number(c[iLat]), lon = Number(c[iLon])
      if (!Number.isFinite(lat) || !Number.isFinite(lon)) continue
      const cat = c[iCat]
      let w = W[cat]
      if (w === undefined) {
        w = 0 // 🔴 모르는 업종은 중립. 관광으로도 동네로도 밀지 않는다
        unknownCat++
        unknownKinds.set(cat || '(없음)', (unknownKinds.get(cat || '(없음)') ?? 0) + 1)
      }
      shops.push({ lat, lon, w })

      const d = c[iDong]
      if (d) {
        const v = dong.get(d) ?? { n: 0, lat: 0, lon: 0 }
        v.n++; v.lat += lat; v.lon += lon
        dong.set(d, v)
      }
      if (roster.has(c[iId])) rosterCoord.set(c[iId], { title: roster.get(c[iId]) || c[iName] || '', lat, lon })
    }
    log(`  가게 ${shops.length.toLocaleString()}곳 · 행정동 ${dong.size}개 · 모르는 업종 ${unknownCat}건`)
    if (unknownCat) {
      const top = [...unknownKinds].sort((a, b) => b[1] - a[1]).slice(0, 5)
      log(`     🔴 표에 없는 대분류 — ${top.map(([k, v]) => `${k}=${v}`).join(' · ')} (중립으로 셌습니다)`)
    }
  }
  if (!shops.length) {
    log('🔴 가게가 하나도 없습니다.')
    process.exitCode = EXIT.INPUT
    return
  }

  // ── 격자 ───────────────────────────────────────────────────────────
  const CELL_DEG = 0.005
  const grid = new Map()
  for (const s of shops) {
    const k = `${Math.floor(s.lat / CELL_DEG)},${Math.floor(s.lon / CELL_DEG)}`
    const b = grid.get(k)
    if (b) b.push(s); else grid.set(k, [s])
  }
  const span = Math.ceil((RADIUS_M + 200) / (CELL_DEG * M_PER_DEG_LAT))

  /** 주변 업종 가중치의 평균. +1 에 가까울수록 관광지, -1 에 가까울수록 동네. */
  function rawAt(lat, lon) {
    let sum = 0, n = 0
    const ci = Math.floor(lat / CELL_DEG), cj = Math.floor(lon / CELL_DEG)
    for (let i = ci - span; i <= ci + span; i++) {
      for (let j = cj - span; j <= cj + span; j++) {
        for (const s of grid.get(`${i},${j}`) ?? []) {
          if (distM(lat, lon, s.lat, s.lon) > RADIUS_M) continue
          sum += s.w; n++
        }
      }
    }
    if (n < MIN_SHOPS) return null // 🔴 값을 지어내지 않는다
    return { raw: sum / n, shops: n }
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
  const sbiz = [...rosterCoord].map(([id, v]) => ({ key: 'SBIZ', id, title: v.title, lat: v.lat, lon: v.lon }))
  log(`  장소 — 관광공사 ${tour.length.toLocaleString()}곳 · 상가 ${sbiz.length.toLocaleString()}곳`)

  // ── 날값 ───────────────────────────────────────────────────────────
  const all = [...tour, ...sbiz]
  let tooFew = 0
  for (const p of all) {
    const r = rawAt(p.lat, p.lon)
    if (!r) { tooFew++; continue }
    p.raw = r.raw; p.nShops = r.shops
  }
  const scored = all.filter((p) => p.raw != null)
  if (!scored.length) {
    log('🔴 한 곳도 값을 못 냈습니다.')
    process.exitCode = EXIT.INVARIANT
    return
  }

  /**
   * 🔴 순위로 바꾼다. 날값은 쏠려 있어 그대로 쓰면 거의 전부가 한쪽 끝에 뭉친다.
   *    localityScore 100 = 동네 · 0 = 관광지 이므로, raw 가 클수록(관광) 점수가 낮다.
   */
  const sortedRaw = scored.map((p) => p.raw).sort((a, b) => a - b)
  const pct = (v) => {
    let lo = 0, hi = sortedRaw.length
    while (lo < hi) { const m = (lo + hi) >> 1; if (sortedRaw[m] < v) lo = m + 1; else hi = m }
    return lo / sortedRaw.length
  }
  const scoreOf = (raw) => +((1 - pct(raw)) * 100).toFixed(1)
  for (const p of scored) p.score = scoreOf(p.raw)

  // ── 저장 ───────────────────────────────────────────────────────────
  await mkdir(OUT, { recursive: true })
  const line = (p) => JSON.stringify(
    p.key === 'TOURAPI'
      ? { contentid: p.id, title: p.title, featureType: 'LOCALITY_SCORE', evidenceStatus: 'ESTIMATED',
          localityScore: p.score, shops: p.nShops, radiusM: RADIUS_M }
      : { sourceType: 'SBIZ', sourceId: p.id, title: p.title, featureType: 'LOCALITY_SCORE', evidenceStatus: 'ESTIMATED',
          localityScore: p.score, shops: p.nShops, radiusM: RADIUS_M })
  const tLines = scored.filter((p) => p.key === 'TOURAPI').map(line)
  const sLines = scored.filter((p) => p.key === 'SBIZ').map(line)
  await writeFile(join(OUT, 'place-locality.ndjson'), tLines.join('\n') + (tLines.length ? '\n' : ''))
  await writeFile(join(OUT, 'place-locality-sbiz.ndjson'), sLines.join('\n') + (sLines.length ? '\n' : ''))

  // ── 대조군 ─────────────────────────────────────────────────────────
  const tourPt = CONTROL_TOURIST_NAMES.map((nm) => {
    const hit = tour.find((p) => p.title.replace(/\s/g, '').includes(nm.replace(/\s/g, '')))
    return hit ? [nm, scoreOf(rawAt(hit.lat, hit.lon)?.raw ?? NaN)] : [nm, null]
  })
  const localPt = CONTROL_LOCAL_DONGS.map((nm) => {
    const v = dong.get(nm)
    if (!v) return [nm, null]
    const r = rawAt(v.lat / v.n, v.lon / v.n)
    return [nm, r ? scoreOf(r.raw) : null]
  })
  const avg = (rows) => {
    const vs = rows.map(([, v]) => v).filter((v) => v != null && Number.isFinite(v))
    return vs.length ? vs.reduce((a, b) => a + b, 0) / vs.length : null
  }
  const tourAvg = avg(tourPt), localAvg = avg(localPt)
  const gap = tourAvg != null && localAvg != null ? localAvg - tourAvg : null

  log('')
  log(`저장 완료 — 관광공사 ${tLines.length.toLocaleString()}곳 · 상가 ${sLines.length.toLocaleString()}곳 / 표본 모자람 ${tooFew}곳`)
  log('  대조군 — 관광지 (점수가 낮아야 한다)')
  for (const [n, v] of tourPt) log(`    ${String(v ?? '값없음').padStart(6)}  ${n}`)
  log('  대조군 — 주거 동 (점수가 높아야 한다)')
  for (const [n, v] of localPt) log(`    ${String(v ?? '값없음').padStart(6)}  ${n}`)
  if (gap != null) log(`  차이: 동네 ${localAvg.toFixed(1)} − 관광지 ${tourAvg.toFixed(1)} = ${gap.toFixed(1)} (${CONTROL_MIN_GAP} 이상이어야 한다)`)

  stamp(join(ROOT, 'data/staged/_place-locality-run'), {
    step: 'process/place-locality',
    inputs: [IN_SBIZ, IN_WEIGHTS, IN_TOURAPI, IN_ROSTER],
    params: { radiusM: RADIUS_M, minShops: MIN_SHOPS, stat: 'percentile rank of mean category weight (inverted)' },
    result: {
      shops: shops.length, tourapi: tLines.length, sbiz: sLines.length, tooFew, unknownCat,
      control: { tourist: tourPt, local: localPt, tourAvg, localAvg, gap },
    },
  })

  // ── 불변식 ─────────────────────────────────────────────────────────
  const missing = [...tourPt, ...localPt].filter(([, v]) => v == null || !Number.isFinite(v))
  if (missing.length) {
    log('')
    log('🔴 대조군에 값이 없습니다. 이름이나 동 이름이 자료와 안 맞습니다.')
    for (const [n] of missing) log(`   값없음 — ${n}`)
    process.exitCode = EXIT.INVARIANT
    return
  }
  if (gap < CONTROL_MIN_GAP) {
    log('')
    log('🔴 대조군이 어긋났습니다. 주거지가 관광지보다 로컬하게 안 나옵니다.')
    log(`   동네 ${localAvg.toFixed(1)} − 관광지 ${tourAvg.toFixed(1)} = ${gap.toFixed(1)}`)
    log('   가중치(config/locality-weights.json)나 반경이 틀렸습니다.')
    process.exitCode = EXIT.INVARIANT
    return
  }
  log('  대조군 통과')
}

main().catch((e) => { console.error('치명:', e?.stack || e); process.exit(EXIT.INVARIANT) })
