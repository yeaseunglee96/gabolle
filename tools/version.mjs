#!/usr/bin/env node
/**
 * 버전 태그를 붙이는 CLI — git 과 시계를 담당하는 층.
 *
 * 판정은 전부 `src/version.mjs` 의 순수 함수에 있다. 여기는 git 호출과 출력만 한다.
 *
 *   node tools/version.mjs current            지금 버전
 *   node tools/version.mjs next               다음 버전만 계산 (아무것도 안 바꾼다)
 *   node tools/version.mjs bump [--push]      태그를 만든다
 *
 *   --branch <이름>   브랜치를 직접 준다 (CI 에서 detached HEAD 일 때)
 *   --dry-run         bump 가 무엇을 할지만 보여준다
 *
 * ── 왜 파일이 아니라 태그인가 ────────────────────────────────────────────────
 *
 * `VERSION` 파일이나 `package.json` 의 version 을 올리면 **머지마다 텍스트 충돌**이
 * 난다. dev 두 갈래가 각자 1.0.1 로 올리면 같은 줄이 부딪히고, 사람이 손으로 푸는
 * 순간 어느 쪽 숫자가 맞는지 아무도 모른다.
 *
 * 이 저장소는 이미 같은 문제를 한 번 풀었다 — 장부를 append 로그 하나가 아니라
 * 파일 하나씩으로 쪼갠 이유가 그것이다(SPEC 2절). 태그는 ref 라서 같은 줄을 다투지
 * 않고, git 이 이름 충돌을 CAS 로 막아준다. 이미 있는 태그는 두 번 만들 수 없다.
 */

import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { planBump, latestVersion, formatVersion } from '../src/version.mjs'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

function git(args) {
  const r = spawnSync('git', args, { cwd: ROOT, encoding: 'utf8', windowsHide: true })
  return { code: r.status ?? 1, out: (r.stdout ?? '').trim(), err: (r.stderr ?? '').trim() }
}

function die(msg, code = 1) { console.error(msg); process.exit(code) }

const argv = process.argv.slice(2)
const cmd = argv[0] ?? 'current'
const flag = (n) => { const i = argv.indexOf(n); return i < 0 ? null : argv[i + 1] }
const has = (n) => argv.includes(n)

/**
 * 지금 브랜치.
 *
 * 🔴 CI 는 보통 detached HEAD 다. `rev-parse --abbrev-ref HEAD` 가 "HEAD" 를 준다.
 *    그 상태에서 브랜치 이름을 추측하면 엉뚱한 자리를 올린다 — GitLab 이 주는
 *    `CI_COMMIT_BRANCH` 를 먼저 본다. 둘 다 없으면 **추측하지 않고 멈춘다.**
 */
function currentBranch() {
  const given = flag('--branch') ?? process.env.CI_COMMIT_BRANCH ?? process.env.CI_COMMIT_REF_NAME
  if (given) return given
  const b = git(['rev-parse', '--abbrev-ref', 'HEAD']).out
  if (!b || b === 'HEAD') {
    die(
      '지금 어느 브랜치인지 알 수 없습니다 (detached HEAD).\n' +
        '  --branch <이름> 으로 직접 주거나, CI 라면 CI_COMMIT_BRANCH 를 씁니다.\n' +
        '추측하지 않는 이유: 단계를 잘못 읽으면 major 를 올려야 할 때 patch 를 올립니다.',
    )
  }
  return b
}

function tags() {
  // 정렬은 src/version.mjs 가 다시 하므로 여기서는 목록만 가져온다.
  const r = git(['tag', '--list', 'v*'])
  if (r.code !== 0) die(`git tag 실패\n${r.err}`)
  return r.out.split('\n').map((s) => s.trim()).filter(Boolean)
}

if (cmd === 'current') {
  const v = latestVersion(tags())
  console.log(v ? `v${formatVersion(v)}` : '아직 버전 태그가 없습니다.')
  process.exit(0)
}

if (cmd === 'next' || cmd === 'bump') {
  const branch = currentBranch()
  let plan
  try { plan = planBump(branch, tags()) } catch (e) { die(e.message) }

  const label = { main: 'major (한 주치를 main 으로)', func: 'minor (하루치를 func 로)', dev: 'patch (dev 작업)' }[plan.level]
  console.log(`브랜치 ${branch}  →  ${label}`)
  console.log(`  ${plan.from ? 'v' + plan.from : '(없음)'}  →  ${plan.tag}`)

  if (cmd === 'next' || has('--dry-run')) process.exit(0)

  // 🔴 이미 있으면 덮어쓰지 않는다. 같은 번호가 서로 다른 커밋을 가리키는 순간
  //    버전은 아무것도 가리키지 않게 된다. git 이 여기서 CAS 역할을 한다.
  const t = git(['tag', '-a', plan.tag, '-m', `${plan.tag} (${plan.level}: ${branch})`])
  if (t.code !== 0) die(`태그를 만들지 못했습니다\n${t.err}`)
  console.log(`  태그 생성: ${plan.tag}`)

  if (has('--push')) {
    const remote = git(['config', '--get', 'axmap.remote']).out || 'origin'
    const p = git(['push', remote, plan.tag])
    if (p.code !== 0) die(`태그 push 실패 (${remote})\n${p.err}`)
    console.log(`  push 완료: ${remote}`)
  } else {
    console.log(`  (원격에 올리려면 --push)`)
  }
  process.exit(0)
}

die(`알 수 없는 명령: ${cmd}\n  current | next | bump`)
