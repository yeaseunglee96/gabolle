#!/usr/bin/env node
/**
 * 받아온 완료 코드를 해독해 응답 원본 파일로 되돌린다 — S15P21E201-409.
 *
 * 🔴 여기가 그날을 통째로 잃을 수 있는 자리다. 코드가 해독이 안 되면 다시 물어보러
 *    갈 수 없다. 그래서 검사합에 걸린 코드를 **조용히 버리지 않고** 목록으로 남긴다.
 *
 * 사양: README.md 3절. 실제 인코딩/디코딩 로직은 codec.mjs.
 *
 *   node survey/decode.mjs --file <코드파일> [--design ../data/staged/choice-design.json]
 *
 * 코드파일: 기록지를 옮겨 적은 파일, 한 줄에 코드 하나.
 * 출력: data/raw/survey/decoded/<타임스탬프>.ndjson   (검사합 통과 — 세션 하나에 한 줄)
 *       data/raw/survey/decoded/<타임스탬프>.rejects.json (검사합 실패 목록)
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

const codeFile = arg('--file', null)
if (!codeFile) die('사용법: node survey/decode.mjs --file <코드파일>')
if (!fs.existsSync(codeFile)) die(`코드 파일이 없다: ${codeFile}`)

// --out 은 기본적으로 안 준다 — 검증 테스트가 임시 폴더로 돌릴 때만 쓴다.
// 실전에서는 항상 data/raw/survey/decoded 다.
const OUT_DIR = arg('--out', path.join(ROOT, 'data/raw/survey/decoded'))

const designFile = arg('--design', path.join(ROOT, 'data/staged/choice-design.json'))
if (!fs.existsSync(designFile)) {
  die(`문항 설계가 없다: ${designFile}\n  먼저: npm run choice:design`)
}
const design = JSON.parse(fs.readFileSync(designFile, 'utf8'))

// 🔴 순서가 곧 자리다 — 응답열의 n번째 글자가 이 목록의 n번째 항목이다.
//    design.sets 뒤에 design.traps(함정 문항, 지금은 없음)가 있으면 이어 붙인다 —
//    함정 문항이 생겨도 코드 형식을 새로 만들지 않고 자리만 늘어난다.
const slots = [...design.sets, ...(design.traps ?? [])].map((s) => s.id)
if (!slots.length) die(`설계에 문항이 하나도 없다: ${designFile}`)

const lines = fs.readFileSync(codeFile, 'utf8').split('\n').map((l) => l.trim()).filter(Boolean)
if (!lines.length) die(`코드 파일이 비어 있다: ${codeFile}`)

const sessions = []
const rejects = []

lines.forEach((code, i) => {
  const r = decodeCode(code)
  if (!r.ok) {
    rejects.push({ line: i + 1, code, reason: r.reason })
    return
  }
  if (r.answers.length > slots.length) {
    rejects.push({ line: i + 1, code, reason: `응답 ${r.answers.length}개인데 문항은 ${slots.length}개뿐이다 — 다른 회차의 설계와 섞였을 수 있다` })
    return
  }
  sessions.push({
    sessionId: r.sessionId,
    code,
    answers: r.answers.map((chosen, idx) => ({ setId: slots[idx], chosen })),
  })
})

fs.mkdirSync(OUT_DIR, { recursive: true })
const ts = new Date().toISOString().replace(/[:.]/g, '-')
const outFile = path.join(OUT_DIR, `${ts}.ndjson`)
const rejectFile = path.join(OUT_DIR, `${ts}.rejects.json`)

fs.writeFileSync(outFile, sessions.map((s) => JSON.stringify(s)).join('\n') + (sessions.length ? '\n' : ''))
fs.writeFileSync(rejectFile, JSON.stringify(rejects, null, 1))

stamp(OUT_DIR, {
  step: 'survey-decode',
  inputs: [codeFile, designFile],
  params: { slots: slots.length },
  result: { total: lines.length, ok: sessions.length, rejected: rejects.length },
})

console.log(`코드 ${lines.length}개 — 해독 성공 ${sessions.length}개, 검사합 실패 ${rejects.length}개`)
if (rejects.length) {
  console.log('  🔴 검사합 실패 목록 (기록지와 대조해서 옮겨 적기 실수인지 확인):')
  for (const r of rejects) console.log(`    줄 ${r.line}: ${r.code}  — ${r.reason}`)
  console.log(`  → ${path.relative(ROOT, rejectFile)}`)
}
console.log(`→ ${path.relative(ROOT, outFile)}`)

// 🔴 해독 결과가 하나도 없으면 이후 단계(load.mjs)가 빈 파일을 조용히 넘기지 않도록
//    여기서부터 분명히 알린다. 종료 코드는 그대로 0 이다 — "전부 실패" 도 정상적으로
//    끝난 해독이고, 판단은 사람이 rejects 를 보고 내린다.
if (!sessions.length) console.log('  🔴 해독에 성공한 세션이 0개다. rejects 를 먼저 확인하라.')
