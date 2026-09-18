#!/usr/bin/env node
/**
 * 부산 도시철도 노선망 — 역 순서 · 역간 소요시간 · 배차간격
 *
 * 만드는 것:
 *   data/staged/busan-subway-network.json   버스 노선망과 **같은 모양**
 *   data/staged/_subway-network-summary.json  검산 결과
 *
 * 🔴 **`backend/src/main/resources/transit/busan-bus-network.json` 의 모양을 그대로 따른다.**
 *    읽는 쪽(`BusanBusNetworkPort`)이 이미 그 모양을 알고, 두 망을 같은 자로 다뤄야
 *    환승 계산이 된다. 다른 모양으로 내면 읽는 쪽이 두 벌이 된다.
 *
 * ── 무엇을 어디서 얻나 ───────────────────────────────────────────────────
 *
 *   역·노선·좌표·환승   data/raw/subway/station.csv       (collect/subway.mjs)
 *   시각표             data/raw/subway/timetable/stations/ (collect/subway-timetable.mjs)
 *
 * 🔴 **역 순서를 지어내지 않는다.** 역번호 순으로 정렬하면 그럴듯하지만 그건 우리 가정이다.
 *    대신 **같은 열차(`trainno`)가 역마다 찍은 도착시각 순서**를 쓴다 — 실제 운행 순서다.
 *    역번호 순서와 어긋나면 요약에 적고 **역번호를 믿지 않는다.**
 *
 * 🔴 **역간 소요시간도 지어내지 않는다.** 좌표 거리 ÷ 표정속도로 내면 그건 가정이고,
 *    여기서는 **같은 열차의 인접 역 도착시각 차이의 중앙값**이다. 관측값이다.
 *
 * ── 요일 ─────────────────────────────────────────────────────────────────
 *
 * 응답에 `dayType` 1(평일)·2(토)·3(일·공휴일) 이 함께 온다. **평일을 기본으로 내고**
 * 요일별 배차간격을 따로 적는다 — 주말 배차가 더 긴 것이 여행 일정에서 중요하다.
 *
 * ── 불변식 (하나라도 깨지면 종료 코드 1) ─────────────────────────────────
 *
 *   · 노선별 역 수가 station.csv 와 같다 (1호선 40 · 2호선 43 · 3호선 17 · 4호선 14)
 *   · 역간 소요시간이 0 보다 크고 15분 미만이다
 *   · 모든 역이 적어도 한 노선에 들어간다
 *
 * 실행: node process/subway-network.mjs
 * 종료 코드: 0 냈다 / 1 불변식 깨짐 / 2 입력 없음
 */
import { readFile, writeFile, mkdir, readdir } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { log } from '../lib/log.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const STATION_CSV = join(ROOT, 'data/raw/subway/station.csv')
const TT_DIR = join(ROOT, 'data/raw/subway/timetable/stations')
const OUT = join(ROOT, 'data/staged/busan-subway-network.json')
const OUT_SUM = join(ROOT, 'data/staged/_subway-network-summary.json')

/** 이보다 긴 역간 시간은 시각표를 잘못 읽은 것이다 (부산 최장 구간도 5분 안쪽이다). */
const MAX_HOP_MIN = 15

/** 이보다 긴 간격은 「다음 열차」가 아니라 운행 중단 구간이다 (막차~첫차). */
const MAX_HEADWAY_MIN = 60

const die = (code, msg) => { log(msg); process.exit(code) }

const sec = (t) => { const [h, m, s] = String(t).split(':').map(Number); return h * 3600 + m * 60 + (s || 0) }
const median = (a) => { if (!a.length) return null; const s = [...a].sort((x, y) => x - y); return s[s.length >> 1] }
const round1 = (n) => Math.round(n * 10) / 10

/** station.csv — 🔴 탭으로 나뉘고 UTF-16LE 이다. 쉼표로 가르면 한 줄이 통째로 첫 칸에 들어간다. */
async function stations() {
  if (!existsSync(STATION_CSV)) die(2, `🔴 역 목록이 없습니다: ${STATION_CSV}\n   먼저: node collect/subway.mjs`)
  const buf = await readFile(STATION_CSV)
  let text = buf.toString('utf16le')
  if (!/[가-힣]/.test(text.slice(0, 300))) text = buf.toString('utf8')
  const lines = text.split(/\r?\n/).filter((l) => l.trim())
  const head = lines[0].replace(/^﻿/, '').split('\t').map((c) => c.trim())
  const at = (n) => { const i = head.indexOf(n); if (i < 0) die(1, `🔴 「${n}」 칸이 없습니다: ${head.join(' · ')}`); return i }
  const iCode = at('역번호'), iName = at('역사명'), iLine = at('노선명')
  const iLat = at('역위도'), iLng = at('역경도'), iTr = at('환승역구분'), iTrLine = at('환승노선명')
  const out = new Map()
  for (const l of lines.slice(1)) {
    const c = l.split('\t')
    const scode = (c[iCode] ?? '').trim()
    if (!/^\d+$/.test(scode)) continue
    out.set(scode, {
      scode,
      name: (c[iName] ?? '').trim(),
      line: (c[iLine] ?? '').trim(),
      lat: Number(c[iLat]), lng: Number(c[iLng]),
      transfer: (c[iTr] ?? '').trim() === '환승역',
      transferLines: (c[iTrLine] ?? '').trim() || null,
    })
  }
  if (!out.size) die(1, '🔴 역을 하나도 못 읽었습니다.')
  return out
}

/** 시각표 — 옛 모양(days)과 새 모양(timetable)을 둘 다 읽는다. */
async function timetable() {
  if (!existsSync(TT_DIR)) die(2, `🔴 시각표가 없습니다: ${TT_DIR}\n   먼저: node collect/subway-timetable.mjs`)
  const rows = []
  const seen = new Set()
  for (const f of await readdir(TT_DIR)) {
    if (!f.endsWith('.json')) continue
    const o = JSON.parse(await readFile(join(TT_DIR, f), 'utf8'))
    const bodies = o.timetable ? [o.timetable] : Object.values(o.days ?? {})
    for (const b of bodies) {
      const it = b?.body?.items?.item
      if (!it) continue
      for (const r of (Array.isArray(it) ? it : [it])) {
        // 🔴 요일별로 세 번 부르던 옛 판이 같은 줄을 세 벌 남겼다. 열쇠로 한 번만 센다.
        const k = `${r.scode}|${r.trainno}|${r.updown}|${r.dayType}|${r.arrtime}`
        if (seen.has(k)) continue
        seen.add(k)
        rows.push(r)
      }
    }
  }
  if (!rows.length) die(1, '🔴 시각표에서 한 줄도 못 읽었습니다.')
  return rows
}

async function main() {
  const st = await stations()
  const rows = await timetable()
  log(`역 ${st.size}개 · 시각표 ${rows.length.toLocaleString()}줄`)

  const byLineCsv = {}
  for (const s of st.values()) byLineCsv[s.line] = (byLineCsv[s.line] ?? 0) + 1

  const problems = []
  const routes = []
  const headwayByLine = {}
  const hopStats = []

  // (노선, 방향, 요일) 별로 모은다
  const groups = new Map()
  for (const r of rows) {
    const k = `${r.line}|${r.updown}|${r.dayType}`
    if (!groups.has(k)) groups.set(k, [])
    groups.get(k).push(r)
  }

  for (const [key, list] of [...groups.entries()].sort()) {
    const [line, updown, dayType] = key.split('|')

    // ── 역 순서: 가장 많은 역을 지나는 열차의 도착시각 순서 ──────────────
    const trains = new Map()
    for (const r of list) {
      if (!trains.has(r.trainno)) trains.set(r.trainno, [])
      trains.get(r.trainno).push(r)
    }
    let longest = null
    for (const v of trains.values()) if (!longest || v.length > longest.length) longest = v
    longest = [...longest].sort((a, b) => sec(a.arrtime) - sec(b.arrtime))
    const order = longest.map((r) => r.scode)

    // ── 역간 소요시간: 모든 열차의 인접 역 차이의 중앙값 ────────────────
    const hopSamples = new Map()
    for (const v of trains.values()) {
      const seq = [...v].sort((a, b) => sec(a.arrtime) - sec(b.arrtime))
      for (let i = 1; i < seq.length; i++) {
        const d = (sec(seq[i].arrtime) - sec(seq[i - 1].arrtime)) / 60
        if (d <= 0 || d >= MAX_HOP_MIN) continue
        const k = `${seq[i - 1].scode}>${seq[i].scode}`
        if (!hopSamples.has(k)) hopSamples.set(k, [])
        hopSamples.get(k).push(d)
      }
    }
    const hops = []
    for (let i = 1; i < order.length; i++) {
      const m = median(hopSamples.get(`${order[i - 1]}>${order[i]}`) ?? [])
      hops.push(m == null ? null : round1(m))
    }
    const known = hops.filter((h) => h != null)
    hopStats.push(...known)
    if (known.length < hops.length) problems.push(`${line}호선 ${updown === '0' ? '상행' : '하행'} 요일${dayType}: 역간 시간을 모르는 구간 ${hops.length - known.length}개`)

    // ── 배차간격: 대표역(첫 역)에서 연속 열차 도착 간격의 중앙값 ─────────
    const head = list.filter((r) => r.scode === order[0]).map((r) => sec(r.arrtime)).sort((a, b) => a - b)
    const gaps = []
    for (let i = 1; i < head.length; i++) { const d = (head[i] - head[i - 1]) / 60; if (d > 0 && d < MAX_HEADWAY_MIN) gaps.push(d) }
    const headway = median(gaps)

    const times = list.map((r) => sec(r.arrtime)).sort((a, b) => a - b)
    const hhmm = (s) => `${String(Math.floor(s / 3600) % 24).padStart(2, '0')}:${String(Math.floor(s / 60) % 60).padStart(2, '0')}`

    routes.push({
      id: `SUBWAY-${line}-${updown}-${dayType}`,
      num: `${line}호선`,
      type: '도시철도',
      updown: updown === '0' ? '상행' : '하행',
      dayType: { 1: '평일', 2: '토요일', 3: '일요일·공휴일' }[dayType] ?? dayType,
      headway: headway == null ? null : round1(headway),
      headwayMeasured: true,
      first: hhmm(times[0]),
      last: hhmm(times[times.length - 1]),
      stops: order,
      hopMinutes: hops,
      trains: trains.size,
    })

    if (dayType === '1') {
      if (!headwayByLine[`${line}호선`]) headwayByLine[`${line}호선`] = []
      if (headway != null) headwayByLine[`${line}호선`].push(round1(headway))
    }
  }

  // ── 불변식 ─────────────────────────────────────────────────────────────
  const lineStops = {}
  for (const r of routes) {
    if (r.dayType !== '평일') continue
    const k = r.num
    lineStops[k] = new Set([...(lineStops[k] ?? []), ...r.stops])
  }
  const lineCheck = {}
  for (const [num, set] of Object.entries(lineStops)) {
    const csvName = `부산도시철도 ${num}`
    const expected = byLineCsv[csvName]
    lineCheck[num] = { 시각표: set.size, 역목록: expected ?? null, 같나: expected === set.size }
    if (expected !== set.size) problems.push(`${num}: 시각표 ${set.size}역 vs 역목록 ${expected}역 — 다르다`)
  }
  const covered = new Set(Object.values(lineStops).flatMap((s) => [...s]))
  const missing = [...st.keys()].filter((c) => !covered.has(c))
  if (missing.length) problems.push(`어느 노선에도 안 들어간 역 ${missing.length}개: ${missing.slice(0, 5).join(', ')}`)

  // ── 저장 ───────────────────────────────────────────────────────────────
  const stops = {}
  for (const s of st.values()) stops[s.scode] = [s.lat, s.lng, s.name, s.line.replace('부산도시철도 ', ''), s.transfer ? 1 : 0]

  const net = {
    generatedAt: new Date().toISOString(),
    source: '부산교통공사 — data/raw/subway/station.csv (역·노선·좌표·환승) + data/raw/subway/timetable (열차시각표 API 15158990)',
    note: '🔴 역 순서와 역간 소요시간은 같은 열차(trainno)가 역마다 찍은 도착시각에서 나왔다. 역번호 순서나 좌표 거리로 지어낸 것이 아니다. stops 는 버스 노선망과 같은 모양이다 — [위도, 경도, 역명, 호선, 환승역이면1].',
    headwayMedianByLine: Object.fromEntries(Object.entries(headwayByLine).map(([k, v]) => [k, median(v)])),
    stops,
    routes,
  }
  await mkdir(dirname(OUT), { recursive: true })
  await writeFile(OUT, JSON.stringify(net))

  const summary = {
    step: 'process/subway-network',
    at: new Date().toISOString(),
    입력: { 역: st.size, 시각표줄: rows.length },
    노선: lineCheck,
    '역간소요시간분': { 최소: round1(Math.min(...hopStats)), 중앙: round1(median(hopStats)), 최대: round1(Math.max(...hopStats)), 표본: hopStats.length },
    '평일배차중앙분': net.headwayMedianByLine,
    노선수: routes.length,
    문제: problems,
    '//': '🔴 「문제」가 비어 있지 않으면 이 산출물을 쓰기 전에 읽는다. 종료 코드는 1 이다.',
  }
  await writeFile(OUT_SUM, JSON.stringify(summary, null, 1))

  log(`노선 ${routes.length}개 (노선×방향×요일)`)
  for (const [num, c] of Object.entries(lineCheck)) log(`  ${num.padEnd(6)} 시각표 ${String(c.시각표).padStart(3)}역 · 역목록 ${String(c.역목록).padStart(3)}역  ${c.같나 ? '🟢' : '🔴'}`)
  log(`역간 소요시간 중앙 ${round1(median(hopStats))}분 (표본 ${hopStats.length.toLocaleString()})`)
  log(`평일 배차 중앙: ${Object.entries(net.headwayMedianByLine).map(([k, v]) => `${k} ${v}분`).join(' · ')}`)
  log(`저장: ${OUT}`)
  if (problems.length) { problems.forEach((p) => log(`  🔴 ${p}`)); die(1, `🔴 불변식 ${problems.length}건이 깨졌습니다.`) }
  log('불변식 통과')
}

main().catch((e) => die(1, `🔴 ${e?.stack ?? e}`))
