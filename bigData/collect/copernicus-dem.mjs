#!/usr/bin/env node
/**
 * Copernicus DEM GLO-30 — 고도 타일 수집 (인증 불필요)
 *
 * 🔴 왜 또 받나: 지금 쓰는 terrarium(collect/terrain.mjs, 원본 SRTM 2000-02)에
 *    **실제로 없는 혹**이 박혀 있다. 평지에 능선이 서고 경사 계산까지 오염된다.
 *    이걸 눈이 아니라 **다른 자료로** 잡아내려고 받는다.
 *    대조는 process/dem-crosscheck.mjs 가 한다. 여기는 받아 두기만 한다.
 *
 *    실측 한 점 — 마린시티 앞 (35.15395, 129.14485):
 *      terrarium 51.5m · ASTER GDEM 0m · **GLO-30 13.9m** (주변은 0~7m)
 *    장산을 재면 GLO-30 이 627m 로 나온다 (실제 634m). 산은 맞고 혹만 없다.
 *
 * ⚠️ GLO-30 은 DSM(**Digital Surface Model** — 땅이 아니라 **땅 위에 있는 것의
 *    꼭대기**를 잰 것)이다. 건물과 나무가 높이에 들어가 있다. 그래서 도심에서는
 *    실제 지면보다 높게 나온다 — 이 방향의 오차는 "가짜 혹" 판정을 **깐깐하게**
 *    만들 뿐이라(가짜인데 진짜로 보일 수는 있어도 그 반대는 어렵다) 안전한 쪽이다.
 *
 * ⚠️ 높이 기준면이 다르다. GLO-30 은 EGM2008, terrarium(SRTM) 은 EGM96 지오이드가
 *    기준이다. 한국에서 둘의 차이는 1m 안쪽이라 40m 짜리 혹을 가리는 데는 무시한다.
 *
 * 📦 출처 — AWS 공개 버킷 (S3 Open Data, 요청자 부담 아님, 인증 불필요)
 *      버킷   copernicus-dem-30m      (리전 eu-central-1)
 *      경로   /{NAME}/{NAME}.tif
 *      이름   Copernicus_DSM_COG_10_N{lat}_00_E{lon}_00_DEM
 *             10 = 1초 격자(≈30m) · N/E 뒤는 타일 **남서쪽 모서리**의 정수 도
 *      예시   https://copernicus-dem-30m.s3.amazonaws.com/
 *               Copernicus_DSM_COG_10_N35_00_E129_00_DEM/
 *               Copernicus_DSM_COG_10_N35_00_E129_00_DEM.tif
 *    한 장이 1도x1도 = 3600x3600 Float32, 받는 크기 4~48MB (압축돼 있다).
 *    부산 bbox 는 넉 장으로 덮인다.
 *
 * 🔴 받은 파일은 커밋하지 않는다. data/ 는 .gitignore 에 있고, 그 이유가
 *    거기 적혀 있다 — git 에 넣으면 clone 이 영원히 느려지고 되돌릴 수 없다.
 *
 *   node collect/copernicus-dem.mjs           부산 bbox 를 덮는 타일 전부
 *   node collect/copernicus-dem.mjs --force   이미 있어도 다시 받는다
 */
import { writeFile, mkdir, readFile } from 'node:fs/promises'
import { existsSync, statSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { openGeoTIFF, ELEV_MIN, ELEV_MAX } from '../process/geotiff.mjs'
import { log } from '../lib/log.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const OUT  = join(ROOT, 'data/raw/dem-glo30')
const BASE = 'https://copernicus-dem-30m.s3.amazonaws.com'
const FORCE = process.argv.includes('--force')

// 값 범위 검사(ELEV_MIN·ELEV_MAX)는 process/geotiff.mjs 에 있다 — 고도를 읽는
// 쪽이 전부 같은 상수를 보게 하려고 디코더 옆에 두었다. 왜 이 폭인지도 거기 있다.

const tileName = (lat, lon) =>
  `Copernicus_DSM_COG_10_N${String(lat).padStart(2, '0')}_00_E${String(lon).padStart(3, '0')}_00_DEM`

/** bbox 를 덮는 1도 타일 목록. 타일 이름의 위경도는 **남서쪽 모서리**다. */
function tilesFor(b) {
  const out = []
  for (let lat = Math.floor(b.south); lat <= Math.floor(b.north); lat++)
    for (let lon = Math.floor(b.west); lon <= Math.floor(b.east); lon++) out.push({ lat, lon })
  return out
}

async function main() {
  const area = JSON.parse(await readFile(join(ROOT, 'config/area.json'), 'utf8'))
  const b = area.target.bbox
  await mkdir(OUT, { recursive: true })

  const want = tilesFor(b)
  log(`${area.target.name}  GLO-30 타일 ${want.length}장`)

  let got = 0, skipped = 0, failed = 0, missing = 0
  const meta = []

  for (const { lat, lon } of want) {
    const name = tileName(lat, lon)
    const dest = join(OUT, `${name}.tif`)
    const url  = `${BASE}/${name}/${name}.tif`

    if (existsSync(dest) && !FORCE) { skipped++ }
    else {
      try {
        const res = await fetch(url, { signal: AbortSignal.timeout(300000) })
        // 🔴 404 는 실패가 아니다. GLO-30 은 **육지만** 있다 — 온전히 바다인
        //    1도 칸은 아예 파일이 없다. 그걸 실패로 세면 종료 코드가 늘 1이 된다.
        if (res.status === 404) { log(`  · ${name}: 없음 (전부 바다)`); missing++; continue }
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        await writeFile(dest, Buffer.from(await res.arrayBuffer()))
        got++
      } catch (e) { failed++; log(`  ⚠ ${name}: ${e.message}`); continue }
    }

    // ── 받은 것이 실제로 읽히는지 확인한다. 잘린 파일을 조용히 넘기지 않는다 ──
    try {
      const g = openGeoTIFF(dest)
      let mn = Infinity, mx = -Infinity, bad = 0, n = 0
      for (let r = 0; r < g.height; r += 9) for (let c = 0; c < g.width; c += 9) {
        const v = g.at(c, r); if (v == null) continue
        n++
        if (v < ELEV_MIN || v > ELEV_MAX) { bad++; continue }
        if (v < mn) mn = v; if (v > mx) mx = v
      }
      g.close()
      meta.push({ tile: name, bytes: statSync(dest).size, sampled: n,
        min: Number(mn.toFixed(2)), max: Number(mx.toFixed(2)), outOfRange: bad })
      log(`  ✓ ${name}  ${(statSync(dest).size / 1e6).toFixed(1)}MB  `
        + `표본 ${n} 최저 ${mn.toFixed(1)}m 최고 ${mx.toFixed(1)}m 범위밖 ${bad}`)
    } catch (e) {
      failed++; log(`  ⚠ ${name}: 읽기 실패 — ${e.message}`)
    }
  }

  await writeFile(join(OUT, '_meta.json'), JSON.stringify({
    at: new Date().toISOString(), bbox: b,
    bucket: 'copernicus-dem-30m (AWS S3 Open Data, 인증 불필요)',
    urlPattern: `${BASE}/{NAME}/{NAME}.tif`,
    naming: 'Copernicus_DSM_COG_10_N{남서위도}_00_E{남서경도}_00_DEM — 10 = 1초 격자(≈30m)',
    grid: '3600x3600 Float32 · 1초 · WGS84(EPSG:4326) · GTRasterType=PixelIsPoint',
    vertical: 'EGM2008 지오이드 기준 표고. DSM 이라 건물·나무 높이가 들어 있다',
    rangeCheck: { min: ELEV_MIN, max: ELEV_MAX },
    wanted: want.length, downloaded: got, alreadyHad: skipped, seaOnly: missing, failed,
    tiles: meta,
  }, null, 2))

  log(`받음 ${got} / 이미있음 ${skipped} / 바다라 없음 ${missing} / 실패 ${failed}`)
  if (failed) process.exitCode = 1      // 실패를 숨기지 않는다
}

main().catch(e => { console.error('치명:', e); process.exit(1) })
