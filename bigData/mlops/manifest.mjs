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
 * 산출물 옆에 `_run.json` 을 쓴다.
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
  fs.writeFileSync(path.join(outDir, '_run.json'), JSON.stringify(rec, null, 1))
  return rec
}
