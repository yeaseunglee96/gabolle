#!/usr/bin/env node
/**
 * Geofabrik 대한민국 OSM 추출본 — 이어받기 + md5 검증
 *
 * Overpass 로 받은 주제별 추출본과 겹치지만 역할이 다르다.
 *   Overpass  = 대상 구역만, 주제별로 골라서. 지금 쓰는 것
 *   PBF       = 전국 전체, 원본 그대로. 구역을 넓힐 때 다시 안 받으려고 둔다
 *
 * 🔴 285MB 다. data/ 는 .gitignore 이므로 커밋되지 않는다.
 *
 *   node collect/osm-pbf.mjs
 */
import { spawn } from 'node:child_process'
import { mkdir, stat } from 'node:fs/promises'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const OUT  = join(ROOT, 'data/raw/osm')
const URL_ = 'https://download.geofabrik.de/asia/south-korea-latest.osm.pbf'
const FILE = 'south-korea-latest.osm.pbf'

const run = (cmd, args, opts) => new Promise((res, rej) => {
  const p = spawn(cmd, args, { stdio: 'inherit', ...opts })
  p.on('close', c => c === 0 ? res() : rej(new Error(`${cmd} exit ${c}`)))
  p.on('error', rej)
})

await mkdir(OUT, { recursive: true })
console.log(`받는 중: ${URL_}`)
// -C - 는 이어받기. 중간에 끊겨도 처음부터 다시 받지 않는다
await run('curl', ['-L', '--retry', '5', '--retry-delay', '5', '-C', '-', '-o', FILE, URL_], { cwd: OUT })
await run('curl', ['-sL', '-o', FILE + '.md5', URL_ + '.md5'], { cwd: OUT })

console.log('md5 검증…')
await run('md5sum', ['-c', FILE + '.md5'], { cwd: OUT })   // 실패하면 여기서 종료 코드가 선다
const s = await stat(join(OUT, FILE))
console.log(`✅ ${(s.size / 1024 / 1024).toFixed(0)} MB`)
