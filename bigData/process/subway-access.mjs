#!/usr/bin/env node
/**
 * 부산 도시철도 **출구 단위** 접근성 — OSM 출구 노드 × 부산교통공사 CSV 조인
 *
 * 🔴 역(Station)과 출구(StationEntrance)는 다른 개념이다. 이것이 이 파일이 있는 이유다.
 *
 *    "역까지 300m" 를 **역 중심점**으로 재면 실제로 걷는 거리와 어긋난다. 부산은
 *    서면·해운대처럼 출구 하나와 다른 출구가 수백 m 떨어진 역이 있고, 산복도로 쪽은
 *    그 수백 m 가 곧 계단과 경사다. 그래서 **좌표를 갖는 단위는 역이 아니라 출구**여야 한다.
 *
 *    시설 정보도 두 단위로 갈린다:
 *      · 엘리베이터  → **출입구번호**가 붙어 있다 = 출구 단위 (exit* 접두사)
 *      · 에스컬레이터·외부경사로 → 역 단위 집계뿐이다 = 역 단위 (station* 접두사)
 *    필드 이름의 접두사가 그 차이다. 섞으면 "이 출구에 에스컬레이터가 있다" 는
 *    **그럴듯하지만 틀린 문장**이 만들어진다.
 *
 * 입력 (네트워크 없음 — process/ 규칙):
 *   data/raw/pbf/transit.ndjson      OSM railway=subway_entrance 노드 (좌표 + ref)
 *   data/raw/subway/*.csv            collect/subway.mjs 가 받은 부산교통공사 원본
 *
 * 출력:
 *   data/staged/subway-entrance.ndjson       출구 한 개 = 한 줄
 *   data/staged/subway-access/_stations.json 역 단위 정보 (출구가 아니라 역에 붙는 것)
 *   data/staged/subway-access/_summary.json  조인 성공률과 **실패한 것들의 예시**
 *   data/staged/subway-access/_run.json      실행 지문
 *   🔴 data/staged/_run.json 은 건드리지 않는다 (다른 단계의 것이다)
 *
 * 종료 코드:
 *   0  정상
 *   1  불변식이 깨졌다 (조용히 틀린 숫자를 내보내느니 멈춘다)
 *   2  입력이 없다 (collect 를 먼저 돌려야 한다)
 */
import { existsSync, readFileSync } from 'node:fs'
import { mkdir, writeFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

import { stamp } from '../mlops/manifest.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const RAW = join(ROOT, 'data/raw/subway')
const TRANSIT = join(ROOT, 'data/raw/pbf/transit.ndjson')
const STAGED = join(ROOT, 'data/staged')
const RUNDIR = join(STAGED, 'subway-access')
const OUT = join(STAGED, 'subway-entrance.ndjson')

/**
 * 🔴 조인의 안전장치 — 이름이 같다고 같은 역이 아니다.
 *
 * 동해선 좌천역(기장군)과 1호선 좌천역(동구)은 이름이 같고 20km 떨어져 있다.
 * 이름만으로 붙이면 기장 출구에 동구 엘리베이터가 달린다. 그것이 이 파트에서
 * 가장 나쁜 실패다 — 검사도 통과하고 화면에도 뜨는데 틀렸다.
 * 그래서 역사정보 CSV 의 역 중심 좌표에서 이 거리를 넘으면 **조인을 거부한다.**
 *
 * 왜 1200m 인가: 부산에서 가장 넓게 퍼진 역(서면·해운대·사상)의 끝 출구가 역
 * 중심에서 400~600m 다. 두 배로 잡아 정상적인 출구를 잘라내지 않으면서,
 * 이름 충돌(수 km~수십 km)은 전부 걸러낸다.
 */
const MAX_JOIN_M = 1200

/**
 * 역명이 **아예 안 적힌** 출구만 가장 가까운 역에 붙인다. 상한을 훨씬 짧게 잡는다.
 *
 * 🔴 이름이 적혀 있는데 명부에 없는 출구에는 이것을 쓰지 않는다.
 *    처음에 그렇게 만들었더니 동해선 **안락역 3번출구**가 400m 옆 4호선 **충렬사역**에
 *    붙었다. 다른 회사가 운영하는 다른 역인데 가깝다는 이유만으로 붙은 것이다.
 *    이름이 있다는 것은 "어느 역인지 이미 안다" 는 뜻이고, 그 이름이 명부에 없으면
 *    답은 "우리 CSV 의 범위 밖" 이지 "옆 역" 이 아니다.
 */
const NEAREST_FALLBACK_M = 500

/* ────────────────────────────── 정규화 규칙 ──────────────────────────────
 *
 * 🔴 세 출처가 역 이름을 서로 다르게 쓴다. 실측한 차이만 적는다 (2026-08-28):
 *
 *   OSM description   "호포역 1번출구" · "경성대·부경대 3번출구" · "온청장역 …"(오타)
 *   엘리베이터 CSV     "노포" · "1연산"(호선 접두) · "미남광장" · "반여" · "국제금융센터"
 *   편의시설 CSV       "서면(1)" · "연산(3)" · "경성대.부경대" · "국제금융센터.부산은행"
 *   역사정보 CSV       "다대포항역" · "서면역(1호선)" · "시립미술관역"(벡스코 옛 이름)
 *
 * 정규화 순서 — 순서가 뜻을 바꾸므로 그대로 지킨다:
 *   1) NFC 정규화 + 앞뒤 공백 제거
 *   2) 괄호와 그 안을 통째로 버린다        "서면(1호선)" → "서면"
 *   3) 구분기호를 전부 버린다               "경성대·부경대" · "경성대.부경대" → "경성대부경대"
 *      대상: 가운뎃점(·, ․) · 마침표 · 붙임표(-, –, —) · 물결 · 쉼표 · 공백
 *   4) 끝의 "역" 한 글자를 버린다 (남는 글자가 2자 이상일 때만)
 *      "다대포항역" → "다대포항", "부산역" → "부산".  "남산정역" → "남산정" (남산 아님)
 *   5) 맨 앞의 호선 숫자를 버린다 (뒤가 한글 2자 이상일 때만)  "1연산" → "연산"
 *      🔴 엘리베이터 CSV 에만 있는 표기다. 다른 출처에는 숫자로 시작하는 역명이 없다
 *   6) 별칭표로 마지막 차이를 흡수한다 (아래 ALIAS — **오직 실측으로 확인한 것만**)
 *
 * 출입구번호:
 *   "2,4" → [2,4] · "6,8,10" → [6,8,10] · "1번" → [1] · "01" → [1] · "" → []
 *   쉼표/빗금/가운뎃점으로 나눈 뒤 각 조각에서 **첫 숫자 뭉치**만 취하고 정수로 만든다.
 *   정수로 만드는 순간 "01" 과 "1" 이 같아지고 "1번" 의 꼬리가 떨어진다.
 *   (실측: OSM 쪽 ref 는 전부 순수 숫자 1~17 이라 이 정규화가 필요 없었지만,
 *    CSV 쪽은 "2,4" 같은 복수 표기가 있어 필요하다. 한쪽만 맞추면 언젠가 어긋난다)
 */
const SEP = /[·․‧∙・.\-–—~,\s]/g

/** 별칭 — 변형 → 대표. 🔴 추측으로 넣지 않는다. 넣은 것은 전부 두 출처를 눈으로 대조했다. */
const ALIAS = new Map([
  ['온청장', '온천장'],             // OSM 오타. 1호선 온천장역 (description:en 은 "Oncheonjang")
  ['미남광장', '미남'],             // 엘리베이터 CSV 의 4호선 표기
  ['반여', '반여농산물시장'],       // 엘리베이터 CSV 의 4호선 축약
  ['국제금융센터', '국제금융센터부산은행'], // 엘리베이터 CSV 는 부기역명을 뺀다
  ['시립미술관', '벡스코'],         // 역사정보 CSV(2021년 기준)의 옛 역명
  // 4호선 옛 "동부산대학역" = 현 "윗반송역". OSM 이 옛 이름을 그대로 쓰고 있다.
  // 근거(추측 아님): 이 이름의 OSM 출구 4개가 윗반송역 중심에서 22~29m 이고,
  //                 OSM 에 "윗반송" 이름의 출구는 하나도 없다.
  ['동부산대학', '윗반송'],
])

function canon(name, line) {
  if (name == null) return ''
  let s = String(name).normalize('NFC').trim()
  s = s.replace(/\([^)]*\)/g, '')          // 2) 괄호
  s = s.replace(SEP, '')                   // 3) 구분기호
  if (s.length >= 3 && s.endsWith('역')) s = s.slice(0, -1)   // 4) 끝의 "역"
  if (line != null) {                      // 5) 맨 앞 호선 숫자 (호선을 아는 출처에서만)
    const m = s.match(/^(\d)(.{2,})$/)
    if (m && Number(m[1]) === Number(line)) s = m[2]
  }
  return ALIAS.get(s) ?? s                 // 6) 별칭
}

/** 출입구번호 문자열 → 정수 배열. 위 주석의 규칙 그대로. */
function exitNums(v) {
  if (v == null) return []
  return String(v)
    .split(/[,/·、]/)
    .map((p) => (p.match(/\d+/) || [])[0])
    .filter(Boolean)
    .map(Number)
    .filter((n) => Number.isFinite(n) && n > 0)
}

/* ────────────────────────────── 읽기 도구 ────────────────────────────── */

/**
 * 인코딩을 골라서 읽는다. 🔴 UTF-8 로 읽어서 깨지면 CP949 로 다시 읽는다 —
 * 깨진 글자를 그대로 다음 단계로 넘기지 않는다. 판단 근거는 BOM 과 엄격 디코딩이다.
 */
function readText(file) {
  const buf = readFileSync(file)
  if (buf.length >= 2 && buf[0] === 0xff && buf[1] === 0xfe) return { enc: 'utf-16le', text: new TextDecoder('utf-16le').decode(buf).replace(/^﻿/, '') }
  if (buf.length >= 2 && buf[0] === 0xfe && buf[1] === 0xff) return { enc: 'utf-16be', text: new TextDecoder('utf-16be').decode(buf).replace(/^﻿/, '') }
  try {
    const t = new TextDecoder('utf-8', { fatal: true }).decode(buf).replace(/^﻿/, '')
    return { enc: 'utf-8', text: t }
  } catch {
    return { enc: 'cp949', text: new TextDecoder('euc-kr').decode(buf) }
  }
}

/** 최소 CSV 파서. 큰따옴표 안의 쉼표·줄바꿈을 지킨다 (엘리베이터 상세위치에 실제로 있다). */
function parseCsv(text, delim = ',') {
  const rows = []
  let field = '', row = [], quoted = false
  for (let i = 0; i < text.length; i++) {
    const c = text[i]
    if (quoted) {
      if (c === '"') { if (text[i + 1] === '"') { field += '"'; i++ } else quoted = false }
      else field += c
    } else if (c === '"') quoted = true
    else if (c === delim) { row.push(field); field = '' }
    else if (c === '\n') { row.push(field); field = ''; if (row.some((x) => x !== '')) rows.push(row); row = [] }
    else if (c !== '\r') field += c
  }
  if (field !== '' || row.length) { row.push(field); if (row.some((x) => x !== '')) rows.push(row) }
  return rows
}

/** 헤더 행을 이름→인덱스로. 헤더 이름이 바뀌면 조용히 빈 값이 되지 않도록 없으면 던진다. */
function cols(header, wanted) {
  const idx = {}
  for (const [key, label] of Object.entries(wanted)) {
    const i = header.findIndex((h) => h.replace(SEP, '') === label.replace(SEP, ''))
    if (i < 0) throw new Error(`CSV 헤더에 "${label}" 이 없다. 실제 헤더: ${header.join(' / ')}`)
    idx[key] = i
  }
  return idx
}

const R = Math.PI / 180
function haversineM(aLat, aLon, bLat, bLon) {
  const dLat = (bLat - aLat) * R, dLon = (bLon - aLon) * R
  const s = Math.sin(dLat / 2) ** 2 + Math.cos(aLat * R) * Math.cos(bLat * R) * Math.sin(dLon / 2) ** 2
  return 2 * 6371000 * Math.asin(Math.min(1, Math.sqrt(s)))
}

const num = (v) => { const n = Number(String(v ?? '').replace(/[^\d.-]/g, '')); return Number.isFinite(n) ? n : 0 }

/* ────────────────────────────── 본체 ────────────────────────────── */

function need(file, hint) {
  if (!existsSync(file)) {
    console.error(`🔴 입력이 없다: ${file.replace(ROOT, '.')}`)
    console.error(`   ${hint}`)
    process.exit(2)
  }
}

async function main() {
  need(TRANSIT, 'npm run collect:extract 를 먼저 돌린다 (전국 PBF → 부산 NDJSON)')
  for (const k of ['elevator', 'facility', 'station']) {
    need(join(RAW, `${k}.csv`), 'node collect/subway.mjs 를 먼저 돌린다')
  }
  const hasRidership = existsSync(join(RAW, 'ridership.csv'))

  /* 1) OSM 출구 노드 ------------------------------------------------------ */
  const entrances = readFileSync(TRANSIT, 'utf8').split('\n').filter(Boolean)
    .map((l) => JSON.parse(l))
    .filter((o) => (o.tags || {}).railway === 'subway_entrance')
  if (entrances.length === 0) {
    console.error('🔴 transit.ndjson 에 railway=subway_entrance 노드가 하나도 없다.')
    process.exit(2)
  }

  /* 2) 역사정보 CSV — 역 명부 + 중심 좌표 (조인 안전장치) ------------------ */
  const stRead = readText(join(RAW, 'station.csv'))
  // 확장자는 .csv 인데 실제로는 탭 구분이다. 첫 줄에 탭이 있으면 탭으로 나눈다.
  const stRows = parseCsv(stRead.text, stRead.text.split('\n')[0].includes('\t') ? '\t' : ',')
  const sc = cols(stRows[0], {
    no: '역번호', name: '역사명', lineName: '노선명', lat: '역위도', lon: '역경도',
    transfer: '환승역구분', addr: '역사도로명주소',
  })
  const roster = []                       // {no, nameRaw, canon, line, lat, lon, transfer, addr}
  for (const r of stRows.slice(1)) {
    const lineName = r[sc.lineName] || ''
    const line = Number((lineName.match(/(\d)호선/) || [])[1]) || null
    const lat = Number(r[sc.lat]), lon = Number(r[sc.lon])
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) continue
    roster.push({
      no: r[sc.no], nameRaw: r[sc.name], canon: canon(r[sc.name], line), line, lat, lon,
      transfer: (r[sc.transfer] || '').includes('환승'), addr: r[sc.addr] || null,
    })
  }
  const byCanon = new Map()
  for (const s of roster) { if (!byCanon.has(s.canon)) byCanon.set(s.canon, []); byCanon.get(s.canon).push(s) }

  /* 3) 엘리베이터 CSV — 출구 단위 + 역 내부 ------------------------------- */
  const evRead = readText(join(RAW, 'elevator.csv'))
  const evRows = parseCsv(evRead.text)
  const ec = cols(evRows[0], {
    line: '호선', station: '역명', exit: '출입구번호', where: '상세위치',
    fromKind: '시작층 구분', fromFloor: '시작층(운행역층)', toKind: '종료층 구분', toFloor: '종료층(운행역층)',
  })
  const evAtExit = new Map()              // `${canon}#${exitNo}` → [{line, where, floors}]
  const evInside = new Map()              // canon → 출입구번호가 비어 있는 엘리베이터 수 (역 내부)
  let evTotal = 0, evWithExit = 0
  for (const r of evRows.slice(1)) {
    const line = Number(r[ec.line]) || null
    const cn = canon(r[ec.station], line)
    if (!cn) continue
    evTotal++
    const rec = {
      line,
      where: (r[ec.where] || '').replace(/\s+/g, ' ').trim(),
      // 기관 안내: "엘리베이터 근처에 출입구가 없는 경우 출입구번호를 공란으로 표기"
      floors: `${r[ec.fromKind]}${r[ec.fromFloor]} → ${r[ec.toKind]}${r[ec.toFloor]}`,
    }
    const nums = exitNums(r[ec.exit])
    if (nums.length === 0) { evInside.set(cn, (evInside.get(cn) || 0) + 1); continue }
    evWithExit++
    for (const n of nums) {
      const k = `${cn}#${n}`
      if (!evAtExit.has(k)) evAtExit.set(k, [])
      evAtExit.get(k).push(rec)
    }
  }

  /* 4) 역사 편의시설 CSV — 역 단위 --------------------------------------- */
  const fcRead = readText(join(RAW, 'facility.csv'))
  const fcRows = parseCsv(fcRead.text)
  const fc = cols(fcRows[0], {
    line: '구분', station: '역명', lift: '휠체어리프트', evIn: '엘리베이터(내부)', evOut: '엘리베이터(외부)',
    esc: '에스컬레이터', tactile: '시각장애인 유도로', ramp: '외부경사로(지상역 출구)',
  })
  const fac = new Map()                   // canon → 역 단위 합계 (환승역은 호선별 행을 합친다)
  for (const r of fcRows.slice(1)) {
    const line = Number((String(r[fc.line]).match(/(\d)/) || [])[1]) || null
    const cn = canon(r[fc.station], line)
    if (!cn) continue
    const cur = fac.get(cn) || { lines: [], escalator: 0, externalRamp: 0, elevatorInside: 0, elevatorOutside: 0, wheelchairLift: 0, tactileGuideway: 0 }
    if (line && !cur.lines.includes(line)) cur.lines.push(line)
    cur.escalator += num(r[fc.esc])
    cur.externalRamp += num(r[fc.ramp])
    cur.elevatorInside += num(r[fc.evIn])
    cur.elevatorOutside += num(r[fc.evOut])
    cur.wheelchairLift += num(r[fc.lift])
    cur.tactileGuideway += num(r[fc.tactile])
    fac.set(cn, cur)
  }

  /* 5) 승하차 인원 — 역 단위 혼잡 (있으면) -------------------------------- */
  const ride = new Map()                  // 역번호 → {boardWeekdayAvg, alightWeekdayAvg, peakHour, days}
  let rideNote = '없음 (ridership.csv 를 못 받았다)'
  if (hasRidership) {
    const rd = readText(join(RAW, 'ridership.csv'))
    const rows = parseCsv(rd.text)
    const rc = cols(rows[0], { no: '역번호', date: '년월일', dow: '요일', kind: '구분', total: '합계' })
    const hourCols = rows[0].map((h, i) => ({ h, i })).filter((x) => /^\d+시-\d+시$/.test(x.h.trim()))
    const acc = new Map()
    for (const r of rows.slice(1)) {
      const no = r[rc.no]
      if (!no) continue
      const weekday = !['토', '일'].includes((r[rc.dow] || '').trim())
      const a = acc.get(no) || { board: 0, alight: 0, days: new Set(), hours: new Array(hourCols.length).fill(0) }
      if (weekday) {
        a.days.add(r[rc.date])
        if ((r[rc.kind] || '').includes('승차')) {
          a.board += num(r[rc.total])
          hourCols.forEach((c, k) => { a.hours[k] += num(r[c.i]) })
        } else a.alight += num(r[rc.total])
      }
      acc.set(no, a)
    }
    for (const [no, a] of acc) {
      const d = a.days.size || 1
      let best = 0
      a.hours.forEach((v, k) => { if (v > a.hours[best]) best = k })
      ride.set(no, {
        boardWeekdayAvg: Math.round(a.board / d),
        alightWeekdayAvg: Math.round(a.alight / d),
        peakHour: hourCols[best]?.h.trim() ?? null,
        weekdays: d,
      })
    }
    rideNote = `평일 ${[...ride.values()][0]?.weekdays ?? '?'}일 평균, 역 ${ride.size}개`
  }

  /* 6) 조인 -------------------------------------------------------------- */
  const out = []
  const miss = { noExitNo: [], noStationName: [], nameNotInRoster: [], distanceRejected: [], nearestFallback: [] }
  let refDescConflict = 0

  for (const e of entrances) {
    const t = e.tags
    const desc = t['description:ko'] || t.description || t['official_name:ko'] || null
    // "호포역 1번출구" / "국제금융센터·부산은행 1번출구" / "두실역 6번 출구" / "4번출구"
    // 앞의 (.*?) 는 게으르게 매칭해서 "10번출구" 를 "1"+"0번" 으로 쪼개지 않는다.
    const m = desc ? desc.match(/^(.*?)\s*(\d+)\s*번\s*출입?구/) : null
    const nameRaw = m && m[1] ? m[1] : null
    const descExit = m ? Number(m[2]) : null

    // 출구번호는 구조화된 태그 ref 를 우선한다. 없으면 설명에서 뽑은 것을 쓴다.
    const refExit = exitNums(t.ref)[0] ?? null
    const exitRef = refExit ?? descExit
    if (refExit != null && descExit != null && refExit !== descExit) refDescConflict++
    if (exitRef == null) miss.noExitNo.push({ id: e.id, tags: t })

    const cn = nameRaw ? canon(nameRaw) : null
    if (!cn) miss.noStationName.push({ id: e.id, desc, ref: t.ref ?? null })

    // 역 후보: 이름으로 찾고, 거리로 거른다
    let cands = cn ? (byCanon.get(cn) || []) : []
    let match = 'name'
    if (cn && cands.length === 0) miss.nameNotInRoster.push({ id: e.id, desc, canon: cn })

    if (!cn) {
      // 🔴 역명이 **아예 없을 때만** 가장 가까운 역에 붙인다 (위 NEAREST_FALLBACK_M 주석 참고).
      //    이름이 있는데 명부에 없는 것은 "범위 밖" 이므로 붙이지 않는다.
      let best = null
      for (const s of roster) {
        const d = haversineM(e.lat, e.lon, s.lat, s.lon)
        if (!best || d < best.d) best = { s, d }
      }
      if (best && best.d <= NEAREST_FALLBACK_M) {
        cands = byCanon.get(best.s.canon) || [best.s]
        match = 'nearest'
        miss.nearestFallback.push({ id: e.id, desc, station: best.s.nameRaw, meters: Math.round(best.d) })
      } else { cands = []; match = 'none' }
    } else if (cands.length === 0) {
      match = 'none'
    } else {
      // 이름은 맞았다. 🔴 그래도 거리를 본다 — 동해선 좌천 ↔ 1호선 좌천 같은 동명이역을 잘라낸다.
      const within = cands.map((s) => ({ s, d: haversineM(e.lat, e.lon, s.lat, s.lon) }))
        .filter((x) => x.d <= MAX_JOIN_M)
      if (within.length === 0) {
        const nearest = cands.map((s) => Math.round(haversineM(e.lat, e.lon, s.lat, s.lon))).sort((a, b) => a - b)[0]
        miss.distanceRejected.push({ id: e.id, desc, canon: cn, nearestMeters: nearest, limit: MAX_JOIN_M })
        cands = []; match = 'none'
      } else cands = within.sort((a, b) => a.d - b.d).map((x) => x.s)
    }

    const st = cands[0] || null
    const lines = cands.map((s) => s.line).filter(Boolean).sort()
    const f = st ? fac.get(st.canon) : null
    const rideRows = cands.map((s) => ride.get(s.no)).filter(Boolean)

    // 출구 단위 엘리베이터: 이 역의 어느 호선에든 이 출입구번호에 엘리베이터가 있으면 있는 것이다
    const evRecs = st && exitRef != null ? (evAtExit.get(`${st.canon}#${exitRef}`) || []) : []

    out.push({
      // ─ 정체 ─
      class: 'StationEntrance',                 // 🔴 Station 이 아니다. 좌표를 갖는 단위는 이쪽이다
      id: `osm:node/${e.id}`,
      lat: e.lat,
      lon: e.lon,
      coordSource: 'osm:railway=subway_entrance',

      // ─ 출구 단위 (exit*) ─
      exitRef,                                  // 출구번호. null = OSM 에 없다
      exitLabel: desc,                          // 원문 그대로 (검증용)
      exitLabelEn: t['description:en'] || t['official_name:en'] || null,
      // 🔴 역을 못 붙였거나 출구번호를 모르면 false 가 아니라 null 이다.
      //    "엘리베이터가 없다" 와 "엘리베이터가 있는지 모른다" 는 다른 말이고,
      //    휠체어 이용자에게는 그 차이가 전부다.
      exitElevator: st && exitRef != null ? evRecs.length > 0 : null,
      exitElevatorCount: st && exitRef != null ? evRecs.length : null,
      exitElevatorFloors: evRecs.map((r) => r.floors),
      exitElevatorWhere: evRecs.map((r) => r.where),
      exitWheelchairOsm: t.wheelchair ?? null,  // OSM 태그. 보유율이 낮아 보조 신호로만 쓴다
      exitIsElevatorNode: t.highway === 'elevator' || null,

      // ─ 역 단위 (station*) — 🔴 출구가 아니라 역의 성질이다 ─
      stationName: st ? st.nameRaw : null,
      stationNameCanon: st ? st.canon : (cn || null),
      stationNo: st ? st.no : null,
      stationLines: lines.length ? lines : null,
      stationLat: st ? st.lat : null,
      stationLon: st ? st.lon : null,
      stationMetersFromExit: st ? Math.round(haversineM(e.lat, e.lon, st.lat, st.lon)) : null,
      stationAddress: st ? st.addr : null,
      stationIsTransfer: st ? !!st.transfer : null,
      stationEscalatorCount: f ? f.escalator : null,        // 환승역은 호선별 행의 합 = 역 전체 대수
      stationExternalRampCount: f ? f.externalRamp : null,   // "외부경사로(지상역 출구)"
      stationElevatorInsideCount: f ? f.elevatorInside : null,
      stationElevatorOutsideCount: f ? f.elevatorOutside : null,
      stationWheelchairLiftCount: f ? f.wheelchairLift : null,
      stationTactileGuidewayCount: f ? f.tactileGuideway : null,
      stationElevatorUnnumbered: st ? (evInside.get(st.canon) || 0) : null, // 출입구번호가 안 적힌 엘리베이터
      stationBoardingWeekdayAvg: rideRows.length ? rideRows.reduce((s, r) => s + r.boardWeekdayAvg, 0) : null,
      stationAlightWeekdayAvg: rideRows.length ? rideRows.reduce((s, r) => s + r.alightWeekdayAvg, 0) : null,
      stationPeakHour: rideRows[0]?.peakHour ?? null,

      // ─ 아직 아무 데도 없는 것 — 슬롯만 열어 둔다 ─
      // 🔴 첫차·막차: 시각표는 오픈API 뿐이고 활용신청(로그인)이 필요해서 못 받았다
      stationFirstTrain: null,
      stationLastTrain: null,
      // 🔴 환승 통로 도보 거리: 부산교통공사는 시설안내도를 이미지로만 낸다.
      //    환승역이 7곳뿐이라 나중에 손으로 잴 수 있다. **추정해서 채우지 않는다.**
      transferWalkMeters: null,

      // ─ 어떻게 붙였나 (믿을지 말지를 쓰는 쪽이 판단할 수 있게) ─
      joinMethod: match,                        // name | nearest | none
      joined: !!st,
    })
  }

  /* 7) 불변식 ------------------------------------------------------------ */
  const n = out.length
  const withExitRef = out.filter((r) => r.exitRef != null).length
  // 🔴 "역명을 뽑았나" 는 **설명 문구에서 뽑았나**로 센다. 최근접 보정으로 나중에 채워진
  //    이름까지 세면 파싱이 망가져도 100% 로 보인다 — 그것이 그럴듯한 거짓 지표다.
  const withStationName = n - miss.noStationName.length
  const joined = out.filter((r) => r.joined).length
  const withElevator = out.filter((r) => r.exitElevator === true).length
  // 부산교통공사 CSV 가 다루는 노선(1~4호선)에 속한 출구만이 조인의 분모다.
  // 동해선(코레일)·부산김해경전철 출구는 애초에 이 CSV 에 없다 = 실패가 아니라 범위 밖.
  const outOfScope = miss.nameNotInRoster.length
  const joinable = n - outOfScope
  // 반대 방향: 엘리베이터 CSV 의 (역, 출입구번호) 조합 중 대응하는 OSM 출구를 찾은 것
  const usedPairs = new Set(out.filter((r) => r.exitElevator === true).map((r) => `${r.stationNameCanon}#${r.exitRef}`))
  const evPairsMatched = [...evAtExit.keys()].filter((k) => usedPairs.has(k)).length
  const evPairsUnmatched = [...evAtExit.keys()].filter((k) => !usedPairs.has(k))

  const broken = []
  if (n !== entrances.length) broken.push(`출구 ${entrances.length}개가 들어와서 ${n}개가 나갔다 — 조용히 버린 것이 있다`)
  if (out.some((r) => !Number.isFinite(r.lat) || !Number.isFinite(r.lon))) broken.push('좌표가 없는 레코드가 있다')
  if (out.some((r) => r.transferWalkMeters !== null)) broken.push('transferWalkMeters 에 값이 들어갔다 — 이 값은 아직 어디에도 없다')
  if (out.some((r) => r.exitElevator === true && !r.joined)) broken.push('역을 못 붙였는데 엘리베이터가 있다고 적힌 레코드가 있다')
  // 구조 감시: OSM 표기나 CSV 헤더가 바뀌어 조인이 조용히 0 이 되는 것을 막는다.
  // 2026-08-28 실측 기준선(출구번호 99.6% · 역명 97.3%)에서 넉넉히 낮춰 잡았다.
  if (withExitRef / n < 0.95) broken.push(`출구번호를 못 뽑은 비율이 너무 높다 (${withExitRef}/${n})`)
  if (withStationName / n < 0.95) broken.push(`역명을 못 뽑은 비율이 너무 높다 (${withStationName}/${n})`)
  if (joinable > 0 && joined / joinable < 0.95) broken.push(`부산교통공사 노선 출구인데도 못 붙인 비율이 높다 (${joined}/${joinable})`)

  /* 8) 쓰기 -------------------------------------------------------------- */
  await mkdir(RUNDIR, { recursive: true })
  await writeFile(OUT, out.map((r) => JSON.stringify(r)).join('\n') + '\n')

  // 역 단위 정보는 출구 파일에 매번 반복되므로 따로도 남긴다 (Station 과 StationEntrance 의 분리)
  const stations = roster.map((s) => ({
    class: 'Station',
    stationNo: s.no, name: s.nameRaw, canon: s.canon, line: s.line,
    lat: s.lat, lon: s.lon, isTransfer: s.transfer, address: s.addr,
    ...(fac.get(s.canon) || {}),
    elevatorUnnumbered: evInside.get(s.canon) || 0,
    entranceCount: out.filter((r) => r.stationNo === s.no).length,
    entrancesWithElevator: out.filter((r) => r.stationNo === s.no && r.exitElevator === true).length,
    ...(ride.get(s.no) || {}),
    firstTrain: null, lastTrain: null,
  }))
  await writeFile(join(RUNDIR, '_stations.json'), JSON.stringify(stations, null, 1))

  const ex = (a, k = 5) => a.slice(0, k)
  const summary = {
    at: new Date().toISOString(),
    step: 'process/subway-access',
    encodings: { station: stRead.enc, elevator: evRead.enc, facility: fcRead.enc },
    input: {
      osmEntrances: entrances.length,
      elevatorRows: evTotal,
      elevatorRowsWithExitNo: evWithExit,
      elevatorRowsWithoutExitNo: evTotal - evWithExit,
      facilityStations: fac.size,
      rosterStations: roster.length,
      ridership: rideNote,
    },
    join: {
      exitRefParsed: `${withExitRef}/${n} (${(100 * withExitRef / n).toFixed(1)}%)`,
      stationNameParsed: `${withStationName}/${n} (${(100 * withStationName / n).toFixed(1)}%)`,
      stationJoined: `${joined}/${n} (${(100 * joined / n).toFixed(1)}%)`,
      stationJoinedAmongBtcLines: `${joined}/${joinable} (${(100 * joined / joinable).toFixed(1)}%)`,
      outOfSourceScope: `${outOfScope}/${n} — 동해선(코레일)·부산김해경전철 출구. 부산교통공사 CSV 에 애초에 없다`,
      entrancesWithElevator: `${withElevator}/${n} (${(100 * withElevator / n).toFixed(1)}%)`,
      entrancesWithElevatorAmongJoined: `${withElevator}/${joined} (${(100 * withElevator / joined).toFixed(1)}%)`,
      refVsDescriptionConflict: refDescConflict,
      // 🔴 반대 방향도 센다. "붙은 것" 만 세면 CSV 쪽에 남은 구멍이 안 보인다.
      elevatorExitPairsMatched: `${evPairsMatched}/${evAtExit.size}`,
      elevatorExitPairsUnmatched: `${evAtExit.size - evPairsMatched} — 엘리베이터 CSV 에는 있는 (역,출입구번호) 인데 그 번호의 OSM 출구 노드가 없다`,
      byMethod: {
        name: out.filter((r) => r.joinMethod === 'name').length,
        nearest: out.filter((r) => r.joinMethod === 'nearest').length,
        none: out.filter((r) => r.joinMethod === 'none').length,
      },
    },
    // 🔴 조인이 실패한 것을 조용히 버리지 않는다. 개수와 예시를 남긴다.
    misses: {
      '//': '대부분은 부산교통공사가 운영하지 않는 노선(동해선·부산김해경전철)이다. 그 CSV 에 애초에 없다.',
      noExitNo: { count: miss.noExitNo.length, examples: ex(miss.noExitNo.map((x) => x.id)) },
      noStationName: { count: miss.noStationName.length, examples: ex(miss.noStationName) },
      nameNotInBtcRoster: { count: miss.nameNotInRoster.length, distinct: [...new Set(miss.nameNotInRoster.map((x) => x.canon))].sort(), examples: ex(miss.nameNotInRoster) },
      distanceRejected: { count: miss.distanceRejected.length, limitMeters: MAX_JOIN_M, examples: ex(miss.distanceRejected, 10), '//': '이름은 같은데 멀다 = 동명이역. 붙였으면 틀린 조인이 됐을 것들이다' },
      nearestFallback: { count: miss.nearestFallback.length, limitMeters: NEAREST_FALLBACK_M, examples: ex(miss.nearestFallback, 10) },
      elevatorExitPairsWithoutOsmEntrance: {
        count: evPairsUnmatched.length,
        examples: ex(evPairsUnmatched, 15),
        '//': 'OSM 쪽 결측이다. 엘리베이터는 실제로 있는데 그 번호의 출구 노드가 OSM 에 없어서 좌표를 못 준다',
      },
    },
    emptyOnPurpose: {
      stationFirstTrain: '열차시각표는 오픈API(데이터셋 15158990)뿐이고 활용신청=로그인이 필요하다. 못 받았다',
      stationLastTrain: '같음',
      transferWalkMeters: '부산교통공사는 환승 도보거리를 공개하지 않는다(시설안내도가 이미지뿐). 환승역이 7곳(서면·연산·수영·덕천·미남·동래·사상)뿐이라 손으로 잴 수 있다. 추정하지 않는다',
    },
    invariantsBroken: broken,
  }
  await writeFile(join(RUNDIR, '_summary.json'), JSON.stringify(summary, null, 1))

  stamp(RUNDIR, {           // 🔴 data/staged/_run.json 이 아니라 이 하위 폴더에 찍는다
    step: 'process/subway-access',
    inputs: [TRANSIT, join(RAW, 'elevator.csv'), join(RAW, 'facility.csv'), join(RAW, 'station.csv'), join(RAW, 'ridership.csv')],
    params: { maxJoinMeters: MAX_JOIN_M, nearestFallbackMeters: NEAREST_FALLBACK_M, aliases: [...ALIAS] },
    result: summary.join,
  })

  console.log(`출구 ${n}개  ·  역 명부 ${roster.length}개`)
  console.log(`  출구번호      ${summary.join.exitRefParsed}`)
  console.log(`  역명 파싱     ${summary.join.stationNameParsed}`)
  console.log(`  역 조인       ${summary.join.stationJoined}   (1~4호선 출구만 보면 ${summary.join.stationJoinedAmongBtcLines})`)
  console.log(`  엘리베이터    ${summary.join.entrancesWithElevator}   (조인된 출구만 보면 ${summary.join.entrancesWithElevatorAmongJoined})`)
  console.log(`  범위 밖       ${outOfScope} (동해선·부산김해경전철 — 부산교통공사 CSV 에 없다)`)
  // 최근접 보정은 역명이 없는 출구에만 걸리므로 nearestFallback ⊆ noStationName 이다.
  console.log(`  못 붙임: 거리로 거부 ${miss.distanceRejected.length}(동명이역) · 역명도 없고 근처 역도 없음 ${miss.noStationName.length - miss.nearestFallback.length}`)
  console.log(`  → ${OUT.replace(ROOT, '.')}`)
  console.log(`  → ${join(RUNDIR, '_summary.json').replace(ROOT, '.')}`)

  if (broken.length) {
    console.error('\n🔴 불변식이 깨졌다:')
    broken.forEach((b) => console.error('   - ' + b))
    process.exit(1)
  }
}

main().catch((e) => {
  console.error('🔴 예상 못 한 실패:', e)
  process.exit(1)
})
