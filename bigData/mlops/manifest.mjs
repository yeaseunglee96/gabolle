/**
 * 실행 지문 — "이 숫자는 무엇으로 만들었나" 를 남긴다.
 *
 * 🔴 MLOps 가 이 단계에서 막아야 할 첫 번째 사고가 이것이다. 표본 400개짜리
 *    모델에 서빙 인프라를 붙이는 것은 과잉이지만, **어떤 입력으로 낸 계수인지
 *    모르는 것**은 표본 크기와 무관하게 치명적이다. 재현이 안 되면 틀렸는지도
 *    알 수 없고, 틀렸는지 모르는 숫자가 제품의 비용함수가 된다.
 */
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'

const sha = (buf) => createHash('sha256').update(buf).digest('hex').slice(0, 16)

/** 파일 하나의 지문. 없으면 null — **"없다" 와 "빈 파일" 을 구별한다.** */
export function fingerprint(file) {
  if (!fs.existsSync(file)) return null
  const b = fs.readFileSync(file)
  return {
    path: path.relative(process.cwd(), file).split(path.sep).join('/'),
    bytes: b.length,
    sha256_16: sha(b),
  }
}

function gitCommit(cwd) {
  try {
    // stdio 를 막는다 — git 저장소 밖(테스트의 임시 폴더 등)에서 stderr 로 떠드는 것을
    // 로그에 섞지 않는다. 못 구하면 null 이고, 그건 정상적인 경우다.
    return execFileSync('git', ['rev-parse', 'HEAD'], { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim()
  } catch { return null }
}

/**
 * 산출물 옆에 `_run-<단계>.json` 을 쓴다.
 *
 * 🔴 단계 이름이 파일 이름에 들어간다. 여러 단계가 같은 폴더를 쓰므로 고정 이름이면
 *    서로를 덮는다. step 을 안 주면 예전처럼 `_run.json` 이지만, **주는 편이 맞다.**
 *
 * @param {string} outDir  산출물 폴더
 * @param {{step:string, inputs?:string[], params?:object, result?:object}} o
 */
export function stamp(outDir, o) {
  fs.mkdirSync(outDir, { recursive: true })
  const rec = {
    step: o.step,
    at: new Date().toISOString(),
    git: gitCommit(process.cwd()),
    node: process.version,
    inputs: (o.inputs || []).map(fingerprint),
    params: o.params ?? null,
    result: o.result ?? null,
  }
  // 🔴 파일 이름에 단계를 넣는다. 예전에는 `_run.json` 한 칸에 썼는데, 여러 단계가
  //    같은 폴더(data/staged/)에 산출물을 넣으므로 **마지막에 돈 것만 남고 앞의 실행
  //    지문이 지워졌다.** 재현 불가를 막으려고 만든 장치가 자기들끼리 서로를 지운 것이다.
  //    실제로 vista 가 choice-design 의 지문을 덮은 적이 있다.
  //    단계별 파일로 나누면 덮을 수가 없고, 병렬로 돌아도 경합이 없다.
  const slug = String(rec.step ?? '').replace(/[^A-Za-z0-9._-]+/g, '-').replace(/^-+|-+$/g, '')
  const name = slug ? `_run-${slug}.json` : '_run.json'
  fs.writeFileSync(path.join(outDir, name), JSON.stringify(rec, null, 1))
  return rec
}
