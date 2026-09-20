#!/usr/bin/env node
/**
 * 장애인편의시설 기구표 수집 — 시설 하나에 한 번씩
 *
 * 왜 따로 부르나:
 *   목록(getDisConvFaclList)에는 시설 이름·주소·좌표만 오고 **무엇이 조사됐는지는 안 온다.**
 *   실제 항목은 기구표(getFacInfoOpenApiJpEvalInfoList)에 있고 **시설당 1회** 불러야 한다.
 *   그래서 부를 대상을 먼저 좁힌 다음(process/facility-match.mjs) 여기서 그 목록만 부른다.
 *
 * 🔴 이 수집기는 판정을 하지 않는다. 받아서 그대로 저장만 한다
 *   기구표가 주는 evalInfo 는 "주출입구 높이차이 제거, 주출입구 접근로, 주출입구(문)" 처럼
 *   **조사 항목 이름을 쉼표로 이어 붙인 문자열**이다.
 *   🔴 **항목이 있다는 것은 그 항목을 조사했다는 뜻이지 접근 가능하다는 뜻이 아니다.**
 *   기존 BarrierFreeAccessibility 가 같은 선을 긋고 있다 — 원천이 "휠체어 접근 가능" 이라고
 *   글자 그대로 썼을 때만 WHEELCHAIR 를 붙이고, 주석에 "뜻을 해석하지 않는다" 고 적어 두었다.
 *   그래서 이 파일은 원문만 남기고, 무엇을 뜻하는지는 사람이 사전을 보고 정한다.
 *
 * 🔴 쪽마다 파일 하나로 저장한다 — 이어받기를 위해서다
 *   한 벌짜리 파일에 모아 쓰면 다시 돌릴 때 통째로 비워질 수 있다(tourapi 수집기가 그랬다).
 *   이미 있는 파일은 건너뛰므로 한도에 걸려 멈춰도 다음 날 이어서 받는다.
 *
 * 🔴 하루 한도는 서비스별이다. 개발계정 100회
 *   같은 상품 안의 다른 오퍼레이션이라도 한도를 나눠 쓴다고 보는 것이 안전하다.
 *   --budget 으로 이번 실행에서 부를 최대 횟수를 정한다. 안 주면 부르지 않고 계획만 보여준다.
 *
 * 입력:  data/staged/facility-matched.ndjson   (process/facility-match.mjs)
 * 출력:  data/raw/facility/eval/<wfcltId>.json  (시설마다 하나)
 *
 * 실행:
 *   node collect/facility-eval.mjs                       # 계획만 보여주고 안 부른다
 *   node collect/facility-eval.mjs --budget 40           # 최대 40회
 *   node collect/facility-eval.mjs --budget 40 --source SBIZ
 *
 * 종료 코드:
 *   0  받았다 (또는 계획만 보여줬다)
 *   2  입력이 없다 / 키가 없다 / API 가 거부했다
 */
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { log } from '../lib/log.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const IN_FILE = join(ROOT, 'data/staged/facility-matched.ndjson')
const OUT_DIR = join(ROOT, 'data/raw/facility/eval')

const BASE = 'https://apis.data.go.kr/B554287/DisabledPersonConvenientFacility'
const OP = 'getFacInfoOpenApiJpEvalInfoList'

function argOf(name) {
  const i = process.argv.indexOf(name)
  return i >= 0 && process.argv[i + 1] ? process.argv[i + 1] : null
}

/** 🔴 키를 절대 로그에 찍지 않는다. */
const redact = (s) => String(s).replace(/serviceKey=[^&\s]*/gi, 'serviceKey=<가림>')

async function loadEnv() {
  const p = join(ROOT, '.env')
  if (!existsSync(p)) return
  for (const line of (await readFile(p, 'utf8')).split('\n')) {
    const m = line.match(/^\s*([A-Z_][A-Z0-9_]*)\s*=\s*(.*)\s*$/)
    if (m && !process.env[m[1]]) process.env[m[1]] = m[2].replace(/^["']|["']$/g, '')
  }
}

async function main() {
  const budget = argOf('--budget') == null ? null : Number(argOf('--budget'))
  const onlySource = argOf('--source')

  await loadEnv()
  if (!process.env.DATA_GO_KR_KEY) {
    log('🔴 .env 에 DATA_GO_KR_KEY 가 없습니다. 부르지 않았습니다.')
    process.exit(2)
  }

  if (!existsSync(IN_FILE)) {
    log('🔴 붙인 목록이 없습니다. 먼저 process/facility-match.mjs 를 돌리십시오.')
    process.exit(2)
  }

  const rows = (await readFile(IN_FILE, 'utf8')).split('\n').filter(Boolean).map((l) => JSON.parse(l))
  const wanted = new Map()
  for (const r of rows) {
    if (onlySource && r.sourceType !== onlySource) continue
    if (r.wfcltId && !wanted.has(r.wfcltId)) wanted.set(r.wfcltId, r)
  }

  await mkdir(OUT_DIR, { recursive: true })
  const have = new Set((await readdir(OUT_DIR)).filter((f) => f.endsWith('.json')).map((f) => f.slice(0, -5)))
  const todo = [...wanted.keys()].filter((id) => !have.has(id.replace(/[^\w.-]/g, '_')))

  log('기구표 수집 (시설당 1회)')
  log('  붙인 줄        ' + rows.length + (onlySource ? '  (→ ' + onlySource + ' 만)' : ''))
  log('  부를 시설      ' + wanted.size)
  log('  이미 받은 것   ' + have.size)
  log('  남은 것        ' + todo.length)

  if (budget == null) {
    log('')
    log('--budget 을 안 주었습니다. **한 번도 안 부르고 끝냅니다.**')
    log('   부르려면: node collect/facility-eval.mjs --budget ' + Math.min(todo.length, 40))
    log('   🔴 하루 한도는 개발계정 100회이고 서비스별입니다.')
    return
  }

  let calls = 0
  let saved = 0
  for (const id of todo) {
    if (calls >= budget) { log('  예산 ' + budget + '회를 다 썼습니다. 여기서 멈춥니다.'); break }
    const url = BASE + '/' + OP + '?serviceKey=' + encodeURIComponent(process.env.DATA_GO_KR_KEY) + '&wfcltId=' + encodeURIComponent(id)
    let text
    try {
      const res = await fetch(url, { signal: AbortSignal.timeout(30000) })
      text = await res.text()
      calls++
      if (!res.ok) { log('🔴 HTTP ' + res.status + ' — ' + redact(text).slice(0, 200)); process.exit(2) }
    } catch (e) {
      log('🔴 네트워크 실패 (' + id + '): ' + redact(e.message))
      process.exit(2)
    }

    if (/SERVICE_KEY_IS_NOT_REGISTERED|LIMITED_NUMBER_OF_SERVICE_REQUESTS|SERVICE ACCESS DENIED/i.test(text)) {
      log('🔴 API 가 거부했습니다. 가짜 자료를 만들지 않고 멈춥니다.')
      log('   응답: ' + redact(text).replace(/\s+/g, ' ').slice(0, 300))
      process.exit(2)
    }

    const evalInfo = (text.match(/<evalInfo>([^<]*)<\/evalInfo>/) || [])[1] ?? null
    const faclNm = (text.match(/<faclNm>([^<]*)<\/faclNm>/) || [])[1] ?? null
    const resultCode = (text.match(/<resultCode>([^<]*)<\/resultCode>/) || [])[1] ?? null

    const safeName = id.replace(/[^\w.-]/g, '_')
    await writeFile(join(OUT_DIR, safeName + '.json'), JSON.stringify({
      ts: Date.now(), wfcltId: id, resultCode, faclNm, evalInfo, raw: text,
    }) + '\n')
    saved++
    if (saved % 10 === 0) log('  ' + saved + ' / ' + Math.min(todo.length, budget))
  }

  log('')
  log('  호출 ' + calls + '회 · 저장 ' + saved + '건')
  if (saved === 0 && todo.length > 0) {
    log('🔴 하나도 못 받았습니다.')
    process.exit(2)
  }
  log('수집 완료')
}

main().catch((e) => { console.error('치명:', e?.stack || e); process.exit(1) })
