#!/usr/bin/env node
/**
 * 부산 도시철도 열차시각표 수집 — 역별·요일별 도착시각
 *
 * 🔴 **`collect/subway.mjs` 가 「못 받음」으로 적어 둔 그 자리다.** 거기 이렇게 돼 있었다 —
 *    *「활용신청이 필요하고 활용신청은 로그인을 요구한다. 엔드포인트 주소는 로그인 전에는
 *    페이지에 노출되지 않아 지어내지 않았다」*. 2026-09-18 에 사람이 활용신청을 했고,
 *    **규격이 서비스 페이지 안에 Swagger 로 들어 있어** 지어내지 않고 그대로 옮겼다.
 *
 *      https://apis.data.go.kr/B551542/trainTime/getTrainTime
 *      serviceKey(필수) · act=json(필수) · scode(필수) · pageNo · numOfRows
 *      updown(0상행 1하행) · stime · etime · enum
 *      🔴 day(1평일 2토 3일·공휴일) 는 규격에 있지만 **서버가 무시한다** — 아래 DAY_NOTE
 *
 * 무엇을 만드나:
 *   **역간 소요시간과 배차간격.** 이 둘이 없으면 대중교통 경로가 시간을 못 낸다 —
 *   버스는 BIMS 로 이미 있는데(`busan-bus-network.json`) 지하철만 비어 있었다.
 *
 *   🔴 **`trainno`(열차번호)가 이 수집의 핵심이다.** 같은 열차를 역마다 이으면
 *      역→역 소요시간이 **관측값으로** 나온다. 표정속도를 가정해 거리로 나누는 것과
 *      다르다 — 그건 지어낸 숫자고 이건 시각표가 말한 것이다.
 *      배차간격도 마찬가지다: 같은 역·방향·요일의 연속 도착시각 차이다.
 *
 * 🔴 역코드(`scode`)는 **우리가 이미 가진 값**이다. `data/raw/subway/station.csv` 의
 *    「역번호」가 그대로 `scode` 다 (규격의 예시 «신평:101 · 안평:414» 가 그 파일의
 *    101·414 와 같다. 2026-09-18 확인). 그래서 역 목록을 지어내지 않는다 —
 *    **그 파일이 없으면 멈춘다.**
 *
 * 🔴 **응답 원문을 그대로 남긴다.** `collect/bims-poll.mjs`·`collect/street-trees.mjs` 와
 *    같은 태도다 — 파싱 규칙은 나중에 바꿀 수 있지만 안 받은 데이터는 못 만든다.
 *
 * ── 이어받기 ─────────────────────────────────────────────────────────────
 *
 * 역 하나에 파일 하나를 쓴다(`stations/<scode>.json`). 다시 돌리면 **이미 있는 역은
 * 건너뛴다.** 한 벌짜리 파일에 모아 쓰지 않는 이유는 tourapi 수집기가 그렇게 만들어져서
 * `--resume` 없이 돌리면 **통째로 비워졌기** 때문이다.
 *
 * 🔴 **내용까지 본다** (편의시설 수집기가 S15P21E201-1212 에서 배운 것). 이름만 보면
 *    빈 파일이나 오류 응답을 「받았다」로 읽고 그 역을 영영 건너뛴다.
 *
 * ── 실패를 숨기지 않는다 ─────────────────────────────────────────────────
 *
 * 한도를 넘기면 200 에 오류 본문이 실려 오기도 한다. 그래서 **편성이 하나도 없는 응답은
 * 파일로 쓰지 않고 그 자리에서 멈춘다.** 빈 파일을 저장하면 다음 실행이 「받았다」고
 * 믿고 건너뛴다 — 구멍이 조용히 생긴다.
 *
 * 실행
 *   node collect/subway-timetable.mjs                # 남은 역을 전부
 *   node collect/subway-timetable.mjs --budget 20    # 20번만 부른다
 *   node collect/subway-timetable.mjs --status       # 어디까지 받았나 (호출 안 함)
 *   node collect/subway-timetable.mjs --dry-run      # 키·역목록만 검사. 호출 안 함
 *
 * 종료 코드: 0 받았다(또는 몫 소진) / 1 응답이 이상하다 / 2 입력·키 없음
 */
import { readFile, writeFile, mkdir, readdir } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { log } from '../lib/log.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const ENV = join(ROOT, '.env')
const STATION_CSV = join(ROOT, 'data/raw/subway/station.csv')
const OUT_DIR = join(ROOT, 'data/raw/subway/timetable/stations')

const BASE = 'https://apis.data.go.kr/B551542/trainTime/getTrainTime'

/** 한 역의 하루치가 1,000편 남짓이다(부산역 평일 1,008). 넉넉히 잡아 한 번에 받는다. */
const ROWS = 2000

/**
 * 🔴 **`day` 파라미터를 안 보낸다. 서버가 무시한다** (2026-09-18 실측).
 *
 * 규격에는 «1:평일 2:토요일 3:일/공휴일» 이라고 적혀 있는데, 셋을 각각 불러 보니
 * **똑같은 922줄**이 왔고 그 안에 `dayType` 1·2·3 이 전부 섞여 있었다. 즉 한 번만
 * 부르면 요일 셋이 다 온다 — **요일별로 부르면 호출이 3배 낭비된다.**
 *
 * 편의시설 API 의 `siDoCd` 가 무시되던 것과 같은 모양이다
 * (`collect/disabled-facility.mjs` 머리말). 규격이 아니라 **실측을 믿는다.**
 *
 * 요일을 가르는 것은 **읽는 쪽**의 일이다 — 응답의 `dayType` 으로 거른다.
 */
const DAY_NOTE = '응답에 dayType 1(평일)·2(토)·3(일·공휴일) 이 함께 온다'

/** 서버에 예의. 공공 API 라 몰아치지 않는다. */
const GAP_MS = 300

const EXIT = { OK: 0, BAD: 1, INPUT: 2 }

const arg = (name, dflt) => {
  const i = process.argv.indexOf(name)
  return i > 0 ? Number(process.argv[i + 1]) : dflt
}
const BUDGET = arg('--budget', Number.POSITIVE_INFINITY)
const STATUS_ONLY = process.argv.includes('--status')
const DRY = process.argv.includes('--dry-run')

/**
 * 역 목록 — `station.csv` 에서 읽는다. 지어내지 않는다.
 *
 * 🔴 이 파일은 **탭으로 나뉜다.** 쉼표로 가르면 한 줄이 통째로 첫 칸에 들어가고
 *    「노선 1종·역 1개」처럼 그럴듯하게 틀린 값이 나온다 (2026-09-18 에 실제로 겪었다).
 * 🔴 인코딩이 **UTF-16LE** 다. cp949 인 다른 파일들과 다르다 — `_manifest.json` 이 적어 둔다.
 */
async function stations() {
  if (!existsSync(STATION_CSV))
    die(EXIT.INPUT, `🔴 역 목록이 없습니다: ${STATION_CSV}\n   먼저: node collect/subway.mjs`)
  const buf = await readFile(STATION_CSV)
  let text = buf.toString('utf16le')
  if (!/[가-힣]/.test(text.slice(0, 300))) text = buf.toString('utf8')
  const lines = text.split(/\r?\n/).filter((l) => l.trim())
  const head = lines[0].replace(/^﻿/, '').split('\t').map((c) => c.trim())
  const col = (name) => {
    const i = head.findIndex((c) => c === name)
    if (i < 0) die(EXIT.BAD, `🔴 역 목록에 「${name}」 칸이 없습니다. 칸: ${head.join(' · ')}`)
    return i
  }
  const iCode = col('역번호'), iName = col('역사명'), iLine = col('노선명')
  const out = []
  for (const line of lines.slice(1)) {
    const c = line.split('\t')
    const code = (c[iCode] ?? '').trim()
    if (!/^\d+$/.test(code)) continue
    out.push({ scode: code, name: (c[iName] ?? '').trim(), line: (c[iLine] ?? '').trim() })
  }
  if (!out.length) die(EXIT.BAD, '🔴 역을 하나도 못 읽었습니다.')
  return out
}

function die(code, msg) { log(msg); process.exit(code) }

async function serviceKey() {
  if (!existsSync(ENV)) die(EXIT.INPUT, `🔴 ${ENV} 가 없습니다. DATA_GO_KR_KEY 를 넣으세요.`)
  const line = (await readFile(ENV, 'utf8')).split(/\r?\n/).find((l) => l.startsWith('DATA_GO_KR_KEY='))
  const key = line?.slice('DATA_GO_KR_KEY='.length).trim()
  if (!key) die(EXIT.INPUT, '🔴 .env 에 DATA_GO_KR_KEY 가 없습니다.')
  return key
}

/** 편성 수. 0 이면 「받았다」고 치지 않는다. */
const countTrains = (json) => {
  const item = json?.body?.items?.item
  return Array.isArray(item) ? item.length : item ? 1 : 0
}

/**
 * 이미 받은 역. 🔴 이름이 아니라 **내용**으로 센다.
 * @returns {{done: Set<string>, broken: string[]}}
 */
async function donePages() {
  if (!existsSync(OUT_DIR)) return { done: new Set(), broken: [] }
  const done = new Set(), broken = []
  for (const f of await readdir(OUT_DIR)) {
    if (!f.endsWith('.json')) continue
    const scode = f.replace(/\.json$/, '')
    try {
      const saved = JSON.parse(await readFile(join(OUT_DIR, f), 'utf8'))
      // 🔴 두 모양을 다 받는다. 옛 판은 요일별로 세 번 불러 `days` 에 넣었는데,
      //    `day` 가 무시된다는 것을 안 뒤로 한 번만 불러 `timetable` 에 넣는다.
      //    내용은 같으므로 이미 받은 것을 **다시 받지 않는다** — 한도를 아낀다.
      const n = countTrains(saved.timetable) || countTrains(saved.days?.['1'])
      n > 0 ? done.add(scode) : broken.push(f)
    } catch { broken.push(f) }
  }
  return { done, broken }
}

async function fetchStation(key, scode) {
  const url = new URL(BASE)
  url.searchParams.set('serviceKey', key)
  url.searchParams.set('act', 'json')
  url.searchParams.set('scode', scode)
  url.searchParams.set('numOfRows', String(ROWS))
  url.searchParams.set('pageNo', '1')
  const res = await fetch(url, { headers: { accept: 'application/json' } })
  const text = await res.text()
  let json = null
  try { json = JSON.parse(text) } catch { /* 오류는 XML 로 오기도 한다 */ }
  return { status: res.status, json, text }
}

async function main() {
  const list = await stations()
  const { done, broken } = await donePages()

  if (broken.length) {
    log(`🔴 읽을 수 없는 파일 ${broken.length}개: ${broken.slice(0, 5).join(', ')}`)
    log('   지우고 다시 돌리세요. 조용히 다시 받지 않는 것은 하루 한도를 태우지 않기 위해서입니다.')
    process.exit(EXIT.BAD)
  }

  const todo = list.filter((s) => !done.has(s.scode))
  log(`역 ${list.length}개 · 받음 ${done.size} · 남음 ${todo.length}`)
  if (STATUS_ONLY) {
    if (todo.length) log(`  다음: ${todo.slice(0, 6).map((s) => `${s.scode}(${s.name})`).join(', ')} …`)
    return
  }
  if (DRY) {
    await serviceKey()
    log(`설정 검사 통과 — 호출하지 않았습니다. 역 ${todo.length}개 = ${todo.length}회 예상 (역당 1회. ${DAY_NOTE})`)
    return
  }
  if (!todo.length) { log('전부 받았습니다.'); return }

  const key = await serviceKey()
  await mkdir(OUT_DIR, { recursive: true })
  let calls = 0, saved = 0

  for (const st of todo) {
    if (calls >= BUDGET) { log(`오늘 몫(${BUDGET}회)을 다 썼습니다. 남은 역 ${todo.length - saved}개`); break }
    const { status, json, text } = await fetchStation(key, st.scode)
    calls++
    const n = countTrains(json)
    if (status !== 200 || n === 0) {
      log(`🔴 ${st.scode}(${st.name}) — HTTP ${status} · 편성 ${n}개. 저장하지 않고 멈춥니다.`)
      log(`   응답 앞부분: ${text.slice(0, 200).replace(/\s+/g, ' ')}`)
      log(`   (하루 한도를 넘었거나 키가 막혔을 수 있습니다. ${calls}회 썼습니다.)`)
      break
    }
    await writeFile(join(OUT_DIR, `${st.scode}.json`),
      JSON.stringify({ scode: st.scode, name: st.name, line: st.line, at: new Date().toISOString(), timetable: json }))
    saved++
    log(`  ${st.scode} ${st.name.padEnd(12)} ${st.line.padEnd(16)} 편성 ${n}`)
    await new Promise((r) => setTimeout(r, GAP_MS))
  }

  log(`받은 역 ${saved}개 · 호출 ${calls}회 · 남음 ${todo.length - saved}개`)
  if (todo.length - saved > 0) log('  다시 돌리면 남은 역부터 이어서 받습니다.')
}

main().catch((e) => die(EXIT.BAD, `🔴 ${e?.stack ?? e}`))
