#!/usr/bin/env node
/**
 * 해독된 응답 파일을 짝 비교 응답 표(data/raw/survey/responses.ndjson)에 넣는다
 * — S15P21E201-499.
 *
 * 입력은 decode.mjs 가 만든 세션 ndjson (한 줄 = 세션 하나, 그 안에 응답 여러 개).
 *
 * 🔴 decode.mjs 가 이미 검사합을 확인했지만, 여기서 **다시 확인한다.** 앞 단계를
 *    무조건 믿지 않는다 — 파일이 두 단계 사이에 손으로 고쳐졌을 수도 있다. 각
 *    단계가 자기 눈으로 검사하는 것이 CLAUDE.md 7절("그럴듯한 숫자가 나오는 것이
 *    죽는 방식이다")과 같은 태도다.
 *
 *   node survey/load.mjs --file <decode.mjs 산출물>.ndjson
 *
 * 출력: data/raw/survey/responses.ndjson 에 이어 쓴다 (choice-fit.mjs 가 읽는 바로 그 파일)
 *       data/raw/survey/decoded/<타임스탬프>.load-rejects.json  (재검사 실패 목록)
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { decodeCode } from './codec.mjs'
import { stamp } from '../mlops/manifest.mjs'

const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..')

const arg = (k, d) => {
  const i = process.argv.indexOf(k)
  return i < 0 ? d : process.argv[i + 1]
}

function die(msg, code = 2) {
  console.error(msg)
  process.exit(code)
}

// --responses·--out 은 기본적으로 안 준다 — 검증 테스트가 임시 폴더로 돌릴 때만 쓴다.
// 실전에서는 항상 data/raw/survey/ 아래다.
const RESPONSES = arg('--responses', path.join(ROOT, 'data/raw/survey/responses.ndjson'))
const OUT_DIR = arg('--out', path.join(ROOT, 'data/raw/survey/decoded'))

const inFile = arg('--file', null)
if (!inFile) die('사용법: node survey/load.mjs --file <decode.mjs 산출물>.ndjson')
if (!fs.existsSync(inFile)) die(`입력 파일이 없다: ${inFile}`)

const sessionLines = fs.readFileSync(inFile, 'utf8').split('\n').map((l) => l.trim()).filter(Boolean)
if (!sessionLines.length) die(`입력 파일이 비어 있다: ${inFile}`)

// 🔴 sessionId::setId 로 중복을 판정한다. 같은 파일을 두 번 넣어도 줄 수가 늘지
//    않아야 한다 — 기존 responses.ndjson 을 먼저 읽어 이미 있는 짝을 전부 안다.
const existingKeys = new Set()
let existingLines = []
if (fs.existsSync(RESPONSES)) {
  existingLines = fs.readFileSync(RESPONSES, 'utf8').split('\n').map((l) => l.trim()).filter(Boolean)
  for (const l of existingLines) {
    const r = JSON.parse(l)
    existingKeys.add(`${r.sessionId}::${r.setId}`)
  }
}

const checksumFailed = []
const toAppend = []
let loaded = 0
let duplicate = 0

sessionLines.forEach((line, i) => {
  let session
  try {
    session = JSON.parse(line)
  } catch (e) {
    checksumFailed.push({ line: i + 1, raw: line, reason: `JSON 파싱 실패: ${e.message}` })
    return
  }

  // 🔴 다시 검사한다 — session.code 원본에서 다시 계산해서, 저장된 answers 가
  //    그 코드가 실제로 내놓는 것과 같은지 본다. session.code 자체가 손상됐어도
  //    decodeCode 가 걸러낸다.
  const r = decodeCode(session.code ?? '')
  if (!r.ok) {
    checksumFailed.push({ line: i + 1, code: session.code, reason: r.reason })
    return
  }
  const expectedChoices = session.answers?.map((a) => a.chosen).join('') ?? ''
  if (expectedChoices !== r.answers.join('')) {
    checksumFailed.push({ line: i + 1, code: session.code, reason: '응답열이 코드 재계산 결과와 다르다 — 파일이 손상됐을 수 있다' })
    return
  }

  for (const a of session.answers ?? []) {
    const key = `${session.sessionId}::${a.setId}`
    if (existingKeys.has(key)) {
      duplicate++
      continue
    }
    existingKeys.add(key)
    toAppend.push(JSON.stringify({ sessionId: session.sessionId, setId: a.setId, chosen: a.chosen }))
    loaded++
  }
})

fs.mkdirSync(path.dirname(RESPONSES), { recursive: true })
if (toAppend.length) {
  const prefix = existingLines.length ? '\n' : ''
  fs.appendFileSync(RESPONSES, prefix + toAppend.join('\n') + '\n')
}

fs.mkdirSync(OUT_DIR, { recursive: true })
const ts = new Date().toISOString().replace(/[:.]/g, '-')
const rejectFile = path.join(OUT_DIR, `${ts}.load-rejects.json`)
fs.writeFileSync(rejectFile, JSON.stringify(checksumFailed, null, 1))

stamp(path.dirname(RESPONSES), {
  step: 'survey-load',
  inputs: [inFile],
  params: {},
  result: { loaded, checksumFailed: checksumFailed.length, duplicate },
})

console.log(`세션 ${sessionLines.length}개 — 들어간 응답 ${loaded}건, 검사합 재확인 실패 ${checksumFailed.length}건, 중복 건너뜀 ${duplicate}건`)
if (checksumFailed.length) {
  console.log(`  🔴 검사합 재확인 실패 목록 → ${path.relative(ROOT, rejectFile)}`)
  for (const r of checksumFailed) console.log(`    줄 ${r.line}: ${r.code ?? r.raw}  — ${r.reason}`)
}
console.log(`→ ${path.relative(ROOT, RESPONSES)}`)
