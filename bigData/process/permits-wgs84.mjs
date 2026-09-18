#!/usr/bin/env node
/**
 * 인허가 좌표를 EPSG:5174 → WGS84 로 바꾸고, **맞는지 데이터로 증명한다**
 *
 * 왜 필요한가:
 *   collect/localdata-permits.mjs 가 받아 온 좌표는 미터 평면좌표다. 위경도가 아니라
 *   "원점에서 동쪽으로 몇 미터" 같은 값이라 지도에 그냥 못 찍고, 다른 데이터와도
 *   못 잇는다. 상가정보와 관광공사는 둘 다 위경도라 **여기만 다르다.**
 *
 *   수집기는 일부러 변환하지 않았다 — ../CLAUDE.md 4절이 collect/ 는 네트워크만
 *   쓰고 계산은 안 한다고 정했다. 원문을 원문대로 남겨야 변환식이 틀렸을 때
 *   되돌릴 수 있다. **그 변환을 여기서 한다.**
 *
 * 🔴 어느 좌표계인지는 이미 실측으로 갈렸다 — 5174 다
 *   docs/PERMITS.md 가 근거를 적어 두었다: 이름매칭 31곳의 오차 중앙값이
 *   5174 는 20.4m, 5181 은 317.2m 였고, 육지 경계 포함률은 99.97% 대 95.17% 였다.
 *   여기서는 그 결론을 **쓰기만** 하고 다시 따지지 않는다.
 *
 * 🔴 변환식이 맞는지를 눈이 아니라 데이터로 증명한다
 *   상가정보(data/raw/poi/sbiz-poi-busan-202606.csv)에는 **같은 가게가 이미
 *   WGS84 위경도로** 들어 있다. 그래서 상호명과 도로명주소가 같은 짝을 찾아
 *   **변환 결과와 몇 미터 떨어져 있는지**를 센다.
 *
 *   부호 하나만 틀려도 수 킬로미터로 벌어지므로 숨을 데가 없다. 중앙값이 기준을
 *   넘으면 종료 코드 1 이다 — 틀린 좌표를 조용히 내보내지 않는다.
 *
 * 입력:  data/raw/permits/*.csv                     (collect/localdata-permits.mjs)
 *        data/raw/poi/sbiz-poi-busan-202606.csv     (검증용. 없으면 검증만 건너뛴다)
 * 출력:  data/staged/permits-wgs84.ndjson
 *        data/staged/_permits-wgs84-run/
 *
 * 실행:
 *   node process/permits-wgs84.mjs
 *
 * 종료 코드:
 *   0  바꿨고 검증도 통과했다
 *   2  입력이 없다 — 먼저 node collect/localdata-permits.mjs
 *   1  불변식이 깨졌다 (변환 오차가 기준 초과 등)
 *
 * 🔴 네트워크를 쓰지 않는다. 바깥 라이브러리도 쓰지 않는다 — 이 폴더는 의존성이 없다.
 */
import { mkdir, readdir, readFile, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'
import { log } from '../lib/log.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const IN_DIR = join(ROOT, 'data/raw/permits')
const SBIZ = join(ROOT, 'data/raw/poi/sbiz-poi-busan-202606.csv')
const OUT_DIR = join(ROOT, 'data/staged')
const OUT_FILE = join(OUT_DIR, 'permits-wgs84.ndjson')

/**
 * 🔴 검증 기준. 상가정보의 같은 가게와 떨어진 거리의 중앙값이 이보다 크면 멈춘다.
 *
 *    2026-09-08 실측은 **1.7 m** 다 (짝 15,239곳). 두 데이터는 좌표를 재는 방식이
 *    달라서(인허가는 건물 대표점, 상가정보는 업소 위치) 0 이 될 수는 없다.
 *
 *    10m 로 잡은 이유는 여유가 아니라 **덫**이다. EPSG 공식 자리 옮김 변수로
 *    되돌리면 20.4m 가 나오는데, 그러면 이 검사가 잡는다. 기준을 넉넉히 잡으면
 *    나중에 누가 "공식 값인데 왜 안 써" 하고 되돌려도 초록으로 지나간다.
 */
const MAX_MEDIAN_METERS = 10
/** 짝을 이만큼도 못 찾으면 검증이 성립하지 않는다. */
const MIN_PAIRS = 500

// ── EPSG:5174 (Korean 1985 / Modified Central Belt) ─────────────────────────
//    횡축 메르카토르 · 베셀 1841 타원체
const BESSEL_A = 6377397.155
const BESSEL_F = 1 / 299.1528128
const LAT0 = 38 * Math.PI / 180
const LON0 = 127.0028902777778 * Math.PI / 180   // 🔴 127°00'10.4" — '수정' 중부원점
const K0 = 1.0
const FE = 200000
const FN = 500000

/**
 * 베셀 → WGS84 자리 옮김 (7변수, Position Vector 규약)
 *
 * 🔴 EPSG 가 5174 에 붙여 둔 공식 값을 쓰지 않는다. 재보니 그게 제일 나빴다.
 *
 *   2026-09-08 실측 — 상가정보의 같은 가게 15,239곳과 대조한 거리 중앙값
 *
 *     EPSG 공식 7변수  -145.907, 505.034, 685.756, -1.162, 2.347, 1.592, 6.342
 *                      → 20.4 m   🔴 남북으로 -19.8 m 쏠림
 *     널리 쓰는 3변수  -146.43, 507.89, 681.46
 *                      →  3.4 m
 *     🟢 아래 7변수    →  **1.7 m**   (동서 0.1 · 남북 0.3 — 쏠림이 사라진다)
 *
 * 🔴 docs/PERMITS.md 의 한 줄을 정정한다
 *   그 문서는 손으로 31곳을 재서 20.4m 를 얻고 *"남은 20m 는 구글 핀을 손으로
 *   찍은 오차"* 라고 적었다. **그게 아니었다.** 사람의 손 오차가 아니라
 *   **변환 변수가 남긴 계통 오차**였다. 31곳으로는 우연처럼 보이는데
 *   15,239곳으로 보니 평균과 중앙값이 똑같이 -19.8m 로 나와 정체가 드러났다.
 *   **표본이 적으면 계통 오차가 우연처럼 보인다.**
 */
const DX = -115.80, DY = 474.99, DZ = 674.11
const RX = 1.16, RY = -2.31, RZ = -1.63            // 초(arcsecond)
const DS = 6.43                                     // 백만분율(ppm)

const WGS_A = 6378137.0
const WGS_F = 1 / 298.257223563

const ARCSEC = Math.PI / 180 / 3600

/** 횡축 메르카토르 역변환 — 평면좌표(x,y) → 타원체 위경도(라디안) */
function tmInverse(x, y) {
  const a = BESSEL_A, f = BESSEL_F
  const e2 = f * (2 - f)
  const ep2 = e2 / (1 - e2)
  const e1 = (1 - Math.sqrt(1 - e2)) / (1 + Math.sqrt(1 - e2))

  // 원점까지의 자오선호 길이
  const m = (lat) => {
    const A0 = 1 - e2 / 4 - 3 * e2 ** 2 / 64 - 5 * e2 ** 3 / 256
    const A2 = 3 / 8 * (e2 + e2 ** 2 / 4 + 15 * e2 ** 3 / 128)
    const A4 = 15 / 256 * (e2 ** 2 + 3 * e2 ** 3 / 4)
    const A6 = 35 * e2 ** 3 / 3072
    return a * (A0 * lat - A2 * Math.sin(2 * lat) + A4 * Math.sin(4 * lat) - A6 * Math.sin(6 * lat))
  }

  const M = m(LAT0) + (y - FN) / K0
  const mu = M / (a * (1 - e2 / 4 - 3 * e2 ** 2 / 64 - 5 * e2 ** 3 / 256))
  const phi1 = mu
    + (3 * e1 / 2 - 27 * e1 ** 3 / 32) * Math.sin(2 * mu)
    + (21 * e1 ** 2 / 16 - 55 * e1 ** 4 / 32) * Math.sin(4 * mu)
    + (151 * e1 ** 3 / 96) * Math.sin(6 * mu)
    + (1097 * e1 ** 4 / 512) * Math.sin(8 * mu)

  const sin1 = Math.sin(phi1), cos1 = Math.cos(phi1), tan1 = Math.tan(phi1)
  const C1 = ep2 * cos1 ** 2
  const T1 = tan1 ** 2
  const N1 = a / Math.sqrt(1 - e2 * sin1 ** 2)
  const R1 = a * (1 - e2) / (1 - e2 * sin1 ** 2) ** 1.5
  const D = (x - FE) / (N1 * K0)

  const lat = phi1 - (N1 * tan1 / R1) * (
    D ** 2 / 2
    - (5 + 3 * T1 + 10 * C1 - 4 * C1 ** 2 - 9 * ep2) * D ** 4 / 24
    + (61 + 90 * T1 + 298 * C1 + 45 * T1 ** 2 - 252 * ep2 - 3 * C1 ** 2) * D ** 6 / 720
  )
  const lon = LON0 + (
    D
    - (1 + 2 * T1 + C1) * D ** 3 / 6
    + (5 - 2 * C1 + 28 * T1 - 3 * C1 ** 2 + 8 * ep2 + 24 * T1 ** 2) * D ** 5 / 120
  ) / cos1

  return [lat, lon]
}

/** 위경도(라디안) → 지심직교좌표 */
function toGeocentric(lat, lon, h, a, f) {
  const e2 = f * (2 - f)
  const N = a / Math.sqrt(1 - e2 * Math.sin(lat) ** 2)
  return [
    (N + h) * Math.cos(lat) * Math.cos(lon),
    (N + h) * Math.cos(lat) * Math.sin(lon),
    (N * (1 - e2) + h) * Math.sin(lat),
  ]
}

/** 지심직교좌표 → 위경도(라디안). 반복 없이 Bowring 식으로 푼다. */
function toGeodetic(X, Y, Z, a, f) {
  const e2 = f * (2 - f)
  const b = a * (1 - f)
  const ep2 = (a ** 2 - b ** 2) / b ** 2
  const p = Math.hypot(X, Y)
  const th = Math.atan2(a * Z, b * p)
  const lat = Math.atan2(Z + ep2 * b * Math.sin(th) ** 3, p - e2 * a * Math.cos(th) ** 3)
  const lon = Math.atan2(Y, X)
  const N = a / Math.sqrt(1 - e2 * Math.sin(lat) ** 2)
  const h = p / Math.cos(lat) - N
  return [lat, lon, h]
}

/** 베셀 지심좌표 → WGS84 지심좌표 (Position Vector 7변수) */
function helmert(X, Y, Z) {
  const rx = RX * ARCSEC, ry = RY * ARCSEC, rz = RZ * ARCSEC
  const s = 1 + DS / 1e6
  return [
    DX + s * (X - rz * Y + ry * Z),
    DY + s * (rz * X + Y - rx * Z),
    DZ + s * (-ry * X + rx * Y + Z),
  ]
}

/** EPSG:5174 평면좌표 → WGS84 [경도, 위도] */
function to5174Wgs84(x, y) {
  const [latB, lonB] = tmInverse(x, y)
  const [X, Y, Z] = toGeocentric(latB, lonB, 0, BESSEL_A, BESSEL_F)
  const [Xw, Yw, Zw] = helmert(X, Y, Z)
  const [lat, lon] = toGeodetic(Xw, Yw, Zw, WGS_A, WGS_F)
  return [lon * 180 / Math.PI, lat * 180 / Math.PI]
}

// ── 거들이 ──────────────────────────────────────────────────────────────────


/** 따옴표를 다루는 최소 CSV 한 줄 쪼개기. */
function splitCsv(line) {
  const out = []
  let cur = '', q = false
  line = line.replace(/\r$/, '')   // 🔴 원본이 CRLF 다. 안 떼면 마지막 칸 이름이 "위도\r" 이 된다
  for (let i = 0; i < line.length; i++) {
    const c = line[i]
    if (q) {
      if (c === '"') { if (line[i + 1] === '"') { cur += '"'; i++ } else q = false }
      else cur += c
    } else if (c === '"') q = true
    else if (c === ',') { out.push(cur); cur = '' }
    else cur += c
  }
  out.push(cur)
  return out
}

/** 상호명을 견줄 수 있는 꼴로 만든다. 공백·괄호·기호를 없앤다. */
const normName = (s) => String(s ?? '').replace(/\(.*?\)/g, '').replace(/[\s\-_.,'"·`!?&+]/g, '').toLowerCase()

/** 도로명주소에서 건물번호까지만 남긴다. 상세주소(층·호)가 데이터마다 달라서다. */
const normAddr = (s) => {
  const t = String(s ?? '').replace(/\(.*?\)/g, ' ').replace(/\s+/g, ' ').trim()
  const m = t.match(/^(.*?\d+(?:-\d+)?)(?:\s|$)/)
  return (m ? m[1] : t).replace(/\s/g, '')
}

/** 두 위경도 사이 거리(m). 부산 규모에서는 평면 근사로 충분하다. */
function distM(lon1, lat1, lon2, lat2) {
  const R = 6371000
  const dLat = (lat2 - lat1) * Math.PI / 180
  const dLon = (lon2 - lon1) * Math.PI / 180
  const mLat = (lat1 + lat2) / 2 * Math.PI / 180
  return R * Math.hypot(dLat, dLon * Math.cos(mLat))
}

const median = (a) => {
  if (!a.length) return null
  const s = [...a].sort((x, y) => x - y)
  const i = s.length >> 1
  return s.length % 2 ? s[i] : (s[i - 1] + s[i]) / 2
}

async function main() {
  if (!existsSync(IN_DIR)) {
    log(`🔴 입력이 없습니다: ${IN_DIR}`)
    log('   먼저 받으십시오:  node collect/localdata-permits.mjs')
    process.exit(2)
  }
  const files = (await readdir(IN_DIR)).filter((f) => f.endsWith('.csv'))
  if (!files.length) {
    log('🔴 인허가 CSV 가 하나도 없습니다.')
    process.exit(2)
  }

  log('인허가 좌표 변환 (EPSG:5174 → WGS84)')
  log(`  입력 ${files.length}개 파일`)

  const out = []
  let rows = 0, converted = 0, noCoord = 0
  /** 검증용 색인: 정규화이름|정규화주소 → [경도, 위도] */
  const byKey = new Map()

  for (const f of files) {
    const text = await readFile(join(IN_DIR, f), 'utf8')
    const lines = text.split('\n')
    const head = splitCsv(lines[0]).map((h) => h.replace(/^"|"$/g, ''))
    const col = Object.fromEntries(head.map((h, i) => [h, i]))
    const need = ['사업장명', '좌표정보(X)', '좌표정보(Y)', '도로명주소', '지번주소', '인허가일자', '폐업일자', '영업상태명', '업태구분명', '관리번호']
    for (const n of need) {
      if (col[n] == null) { log(`🔴 ${f} 에 칸 "${n}" 이 없습니다. 원본 형식이 바뀌었습니다.`); process.exit(1) }
    }

    for (let i = 1; i < lines.length; i++) {
      if (!lines[i].trim()) continue
      const c = splitCsv(lines[i])
      rows++
      const x = Number(c[col['좌표정보(X)']]), y = Number(c[col['좌표정보(Y)']])
      let lon = null, lat = null
      if (Number.isFinite(x) && Number.isFinite(y) && x > 0 && y > 0) {
        ;[lon, lat] = to5174Wgs84(x, y)
        converted++
      } else noCoord++

      const name = c[col['사업장명']]
      const road = c[col['도로명주소']]
      if (lon != null) {
        const k = `${normName(name)}|${normAddr(road)}`
        if (normName(name) && normAddr(road)) byKey.set(k, [lon, lat])
      }

      out.push(JSON.stringify({
        id: c[col['관리번호']],
        name,
        category: c[col['업태구분명']] || null,
        state: c[col['영업상태명']] || null,
        openedOn: c[col['인허가일자']] || null,
        closedOn: c[col['폐업일자']] || null,
        roadAddr: road || null,
        jibunAddr: c[col['지번주소']] || null,
        lon, lat,
        // 🔴 원문 평면좌표를 함께 남긴다. 변환식을 고쳐도 다시 받을 필요가 없다.
        src5174: Number.isFinite(x) && Number.isFinite(y) ? [x, y] : null,
      }))
    }
  }

  await mkdir(OUT_DIR, { recursive: true })
  await writeFile(OUT_FILE, out.join('\n') + '\n')
  log(`  행 ${rows} · 변환 ${converted} · 좌표없음 ${noCoord}`)

  // ── 검증: 상가정보의 같은 가게와 몇 m 떨어졌나 ────────────────────────────
  let pairs = [], sbizRows = 0
  if (existsSync(SBIZ)) {
    const text = await readFile(SBIZ, 'utf8')
    const lines = text.split('\n')
    const head = splitCsv(lines[0]).map((h) => h.replace(/^"|"$/g, ''))
    const col = Object.fromEntries(head.map((h, i) => [h, i]))
    const ci = { name: col['상호명'], road: col['도로명주소'], lon: col['경도'], lat: col['위도'] }
    if (Object.values(ci).some((v) => v == null)) {
      log('🔴 상가정보 CSV 에 기대한 칸이 없습니다. 검증을 건너뜁니다.')
    } else {
      for (let i = 1; i < lines.length; i++) {
        if (!lines[i].trim()) continue
        const c = splitCsv(lines[i])
        sbizRows++
        const k = `${normName(c[ci.name])}|${normAddr(c[ci.road])}`
        const hit = byKey.get(k)
        if (!hit) continue
        const lon = Number(c[ci.lon]), lat = Number(c[ci.lat])
        if (!Number.isFinite(lon) || !Number.isFinite(lat)) continue
        pairs.push(distM(hit[0], hit[1], lon, lat))
      }
    }
  } else {
    log('  (상가정보 CSV 가 없어 검증을 건너뜁니다)')
  }

  const med = median(pairs)
  const p90 = pairs.length ? [...pairs].sort((a, b) => a - b)[Math.floor(pairs.length * 0.9)] : null
  const under50 = pairs.filter((d) => d < 50).length

  if (pairs.length) {
    log(`  검증 짝 ${pairs.length}개 (상가정보 ${sbizRows}행과 대조)`)
    log(`    거리 중앙값 ${med.toFixed(1)} m`)
    log(`    90번째 백분위 ${p90.toFixed(1)} m`)
    log(`    50m 안 ${under50} (${(100 * under50 / pairs.length).toFixed(1)}%)`)
  }

  stamp(join(OUT_DIR, '_permits-wgs84-run'), {
    step: 'process/permits-wgs84',
    inputs: [existsSync(SBIZ) ? SBIZ : null].filter(Boolean),
    params: { crs: 'EPSG:5174', maxMedianMeters: MAX_MEDIAN_METERS, minPairs: MIN_PAIRS },
    result: {
      rows, converted, noCoord,
      verifyPairs: pairs.length,
      medianMeters: med == null ? null : Number(med.toFixed(2)),
      p90Meters: p90 == null ? null : Number(p90.toFixed(2)),
      under50m: under50,
      out: 'data/staged/permits-wgs84.ndjson',
    },
  })

  // ── 불변식 ────────────────────────────────────────────────────────────
  if (converted === 0) {
    log('🔴 한 건도 변환하지 못했습니다.')
    process.exit(1)
  }
  if (existsSync(SBIZ)) {
    if (pairs.length < MIN_PAIRS) {
      log(`🔴 검증 짝이 ${pairs.length}개뿐입니다 (기준 ${MIN_PAIRS}). 변환이 맞는지 증명하지 못했습니다.`)
      process.exit(1)
    }
    if (med > MAX_MEDIAN_METERS) {
      log(`🔴 거리 중앙값 ${med.toFixed(1)}m 가 기준 ${MAX_MEDIAN_METERS}m 를 넘습니다.`)
      log('   변환식이 틀렸을 가능성이 큽니다. 틀린 좌표를 내보내지 않습니다.')
      process.exit(1)
    }
  }
  log('변환 완료')
}

main().catch((e) => { console.error('치명:', e?.stack || e); process.exit(1) })
