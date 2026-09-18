#!/usr/bin/env node
/**
 * Overpass API 수집 — 대상 구역의 도로·보행로·계단·정류장·POI
 *
 * Overpass API — OpenStreetMap 데이터에 "이 조건에 맞는 것만 달라" 고 물어보는 공개 서버.
 *
 * 🔴 공용 서버다. 순차 실행하고 사이에 쉰다. 병렬로 때리면 IP 가 막히고,
 *    막히면 팀 전체가 몇 시간 못 쓴다.
 *
 * 🔴 이 스크립트는 focus 구역(중구·동구)만 받는다. 전역이 아니다.
 *    작업 범위는 부산 전역이지만 그것을 Overpass 로 긁으면 안 된다 —
 *    241배 부하이고, 그러라고 있는 서버가 아니다. 전역은 이미 받아둔 PBF 에서
 *    뽑는다: collect/pbf_extract.py. 여기는 PBF 파이프라인을 검증할 때 쓰는
 *    작고 빠른 대조군이다.
 *
 *    --area target 으로 억지로 전역을 부를 수는 있지만 면적 상한에 걸린다.
 *
 * 이어서 받는다 — 이미 있는 주제는 건너뛴다. --force 로 다시 받는다.
 *
 *   node collect/overpass.mjs
 *   node collect/overpass.mjs --force --only walk
 */
import { writeFile, readFile, mkdir, stat } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { log } from '../lib/log.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const OUT  = join(ROOT, 'data/raw/overpass')

const ENDPOINTS = [
  'https://overpass-api.de/api/interpreter',
  'https://overpass.kumi.systems/api/interpreter',   // 1번이 막히면 2번
]

const args    = process.argv.slice(2)
const FORCE   = args.includes('--force')
const ONLY    = args.includes('--only') ? args[args.indexOf('--only') + 1] : null
const AREA    = args.includes('--area') ? args[args.indexOf('--area') + 1] : 'focus'
const PAUSE_MS = 8000     // 질의 사이 휴식. 줄이지 말 것
const MAX_KM2  = 200      // 이보다 넓으면 거부한다. 공용 서버에 대한 예의다

/** 주제별 질의. bbox 는 {{bbox}} 로 두면 아래에서 치환한다. */
const TOPICS = {
  // 행정경계 — 나중에 bbox 대신 실제 폴리곤으로 자르기 위해
  boundary: `
    [out:json][timeout:180];
    relation["boundary"="administrative"]["admin_level"~"^(8|9)$"]({{bbox}});
    out geom;`,

  // 보행로 — 걷기 판정의 본체. incline(경사)·width(폭)·surface(노면) 태그가 여기 붙는다
  walk: `
    [out:json][timeout:300];
    (
      way["highway"~"^(footway|path|steps|pedestrian|living_street|corridor)$"]({{bbox}});
      way["sidewalk"]["highway"]({{bbox}});
      way["foot"~"^(yes|designated)$"]["highway"]({{bbox}});
    );
    out geom tags;`,

  // 계단 — 부산 산복도로의 핵심. step_count 가 있으면 그게 곧 난이도다
  stairs: `
    [out:json][timeout:180];
    way["highway"="steps"]({{bbox}});
    out geom tags;`,

  // 차도 — 택시 경로와 도보 병행 판정용
  road: `
    [out:json][timeout:300];
    way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|service|road)(_link)?$"]({{bbox}});
    out geom tags;`,

  // 대중교통 — 정류장·역·출입구
  transit: `
    [out:json][timeout:180];
    (
      node["highway"="bus_stop"]({{bbox}});
      node["public_transport"~"^(platform|stop_position|station)$"]({{bbox}});
      node["railway"~"^(station|subway_entrance|tram_stop)$"]({{bbox}});
      way["public_transport"="platform"]({{bbox}});
      relation["type"="route"]["route"~"^(bus|subway|train)$"]({{bbox}});
    );
    out geom tags;`,

  // 접근성 장애물 — 휠체어·캐리어 판정
  barrier: `
    [out:json][timeout:180];
    (
      node["barrier"]({{bbox}});
      node["kerb"]({{bbox}});
      way["barrier"]({{bbox}});
      node["highway"="crossing"]({{bbox}});
      node["highway"="elevator"]({{bbox}});
    );
    out geom tags;`,

  // POI — 상가정보 CSV 와 대조할 기준. OSM 쪽은 관광객 시선이 섞여 있다
  poi: `
    [out:json][timeout:300];
    (
      node["amenity"]({{bbox}});
      node["shop"]({{bbox}});
      node["tourism"]({{bbox}});
      node["leisure"]({{bbox}});
      way["tourism"]({{bbox}});
      way["amenity"~"^(restaurant|cafe|marketplace|bar|pub)$"]({{bbox}});
    );
    out center tags;`,

  // 조망 — 전망대·뷰포인트. "걸을 만한가" 의 보상 항목
  viewpoint: `
    [out:json][timeout:180];
    (
      node["tourism"~"^(viewpoint|artwork|attraction)$"]({{bbox}});
      way["natural"~"^(coastline|beach|water)$"]({{bbox}});
      node["man_made"="tower"]({{bbox}});
    );
    out geom tags;`,
}

const sleep = ms => new Promise(r => setTimeout(r, ms))

async function exists(p) { try { const s = await stat(p); return s.size > 0 } catch { return false } }

async function ask(query, topic) {
  let lastErr
  for (let attempt = 1; attempt <= 4; attempt++) {
    const endpoint = ENDPOINTS[(attempt - 1) % ENDPOINTS.length]
    try {
      log(`  ${topic}: 시도 ${attempt} → ${new URL(endpoint).host}`)
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          // Overpass 예의 — 누가 왜 부르는지 밝힌다
          'User-Agent': 'S15P21E201-bigData/0.1 (SSAFY team project; busan mobility ontology)',
        },
        body: 'data=' + encodeURIComponent(query),
      })
      if (res.status === 429 || res.status === 504) {
        const wait = 30000 * attempt
        log(`  ${topic}: ${res.status} — ${wait / 1000}초 쉬고 재시도`)
        await sleep(wait); continue
      }
      if (!res.ok) throw new Error(`HTTP ${res.status}: ${(await res.text()).slice(0, 200)}`)
      const json = await res.json()
      if (!json.elements) throw new Error('elements 가 없다: ' + JSON.stringify(json).slice(0, 200))
      return json
    } catch (e) {
      lastErr = e
      log(`  ${topic}: 실패 — ${e.message}`)
      if (attempt < 4) await sleep(15000 * attempt)
    }
  }
  throw lastErr
}

async function main() {
  await mkdir(OUT, { recursive: true })
  const area = JSON.parse(await readFile(join(ROOT, 'config/area.json'), 'utf8'))
  const sel = area[AREA]
  if (!sel) { console.error(`config/area.json 에 '${AREA}' 가 없습니다`); process.exit(1) }
  const b = sel.bbox
  const bbox = `${b.south},${b.west},${b.north},${b.east}`   // Overpass 는 S,W,N,E 순서다

  // 🔴 면적 상한. 전역을 공용 서버에 묻는 사고를 코드가 막는다
  const km2 = (b.north - b.south) * 111 * (b.east - b.west) * 111
            * Math.cos((b.north + b.south) / 2 * Math.PI / 180)
  if (km2 > MAX_KM2) {
    console.error(`🔴 ${sel.name} 은 약 ${km2.toFixed(0)} km2 로 상한 ${MAX_KM2} km2 를 넘습니다.`)
    console.error('   Overpass 는 공용 서버입니다. 넓은 범위는 PBF 에서 뽑으세요:')
    console.error('     py collect/pbf_extract.py')
    process.exit(2)
  }

  log(`대상: ${sel.name} (${AREA})  약 ${km2.toFixed(0)} km2  bbox=${bbox}`)

  const topics = ONLY ? [ONLY] : Object.keys(TOPICS)
  const summary = []

  for (const topic of topics) {
    if (!TOPICS[topic]) { log(`알 수 없는 주제: ${topic}`); process.exitCode = 1; continue }
    const out = join(OUT, `${topic}.json`)

    if (!FORCE && await exists(out)) {
      const n = JSON.parse(await readFile(out, 'utf8')).elements.length
      log(`${topic}: 이미 있음 (${n}건) — 건너뜀`)
      summary.push({ topic, count: n, skipped: true })
      continue
    }

    log(`${topic}: 받는 중…`)
    try {
      const json = await ask(TOPICS[topic].replace(/\{\{bbox\}\}/g, bbox), topic)
      await writeFile(out, JSON.stringify(json))
      log(`${topic}: ✅ ${json.elements.length}건`)
      summary.push({ topic, count: json.elements.length })
    } catch (e) {
      // 🔴 실패를 숨기지 않는다. 종료 코드로 알린다
      log(`${topic}: ❌ 포기 — ${e.message}`)
      summary.push({ topic, error: e.message })
      process.exitCode = 1
    }
    await sleep(PAUSE_MS)
  }

  await writeFile(join(OUT, '_summary.json'),
    JSON.stringify({ at: new Date().toISOString(), area: area.target.name, bbox, summary }, null, 2))

  log('─'.repeat(50))
  for (const s of summary) log(s.error ? `❌ ${s.topic}: ${s.error}` : `✅ ${s.topic}: ${s.count}건`)
  const failed = summary.filter(s => s.error).length
  log(failed ? `🔴 ${failed}개 주제 실패` : '전부 성공')
}

main().catch(e => { console.error('치명:', e); process.exit(1) })
