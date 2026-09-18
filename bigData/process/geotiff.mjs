/**
 * 최소 GeoTIFF 디코더 — Copernicus DEM GLO-30 COG 전용
 *
 * 의존성을 늘리지 않으려고 직접 푼다. process/png.mjs 와 같은 이유다 —
 * 범용 디코더가 아니고, 우리가 실제로 받는 파일 한 종류만 읽는다.
 *
 * COG(**Cloud-Optimized GeoTIFF** — 안쪽이 바둑판으로 잘려 있어서 필요한
 * 칸만 꺼내 쓸 수 있게 만든 GeoTIFF)라 파일 전체를 펴지 않아도 된다.
 * 그게 여기서 중요한 이유: 1도 타일 하나가 3600x3600 Float32 = 51.8MB 고,
 * 부산을 덮으려면 4장이라 전부 펴면 207MB 다. 우리가 실제로 보는 것은
 * 그중 부산 육지뿐이라 **안쪽 1024x1024 칸을 필요할 때만 푼다.**
 *
 * 🔴 이 파일이 읽을 수 있는 것 (실측으로 확인한 GLO-30 의 생김새):
 *      classic TIFF(BigTIFF 아님) · little-endian · 3600x3600
 *      BitsPerSample 32 · SampleFormat 3 (IEEE float) · SamplesPerPixel 1
 *      Compression 8 (Adobe Deflate = zlib) · Predictor 3 (부동소수점 예측)
 *      TileWidth/TileLength 1024 (바둑판 4x4 = 16칸)
 *      GTRasterTypeGeoKey(1025) = 2 = RasterPixelIsPoint
 *    이 중 하나라도 다르면 **조용히 틀린 값을 내지 않고 예외를 던진다.**
 *
 * 🔴 RasterPixelIsPoint 가 왜 중요한가: ModelTiepoint 가 가리키는 (129, 36) 이
 *    첫 픽셀의 **모서리가 아니라 한가운데**라는 뜻이다. Area 로 착각하면 모든
 *    표본이 반 픽셀(약 15m) 씩 밀린다. 30m 격자에서 15m 는 절반이라,
 *    해안선처럼 값이 급히 바뀌는 곳에서 엉뚱한 픽셀을 읽게 된다.
 */
import { openSync, readSync, closeSync, statSync } from 'node:fs'
import { inflateSync } from 'node:zlib'

/**
 * 🔴 값 범위 검사. 이 밖은 자료가 아니라 고장이다 — 쓰지 않고 버린다.
 *
 * terrarium 타일에 -858 ~ -4,511m 짜리 픽셀이 **한 픽셀 폭 실선**으로 박혀 있었다.
 * 지형이 아니라 타일을 만들 때 생긴 흠이다. 지금까지 어느 코드에도 이 검사가
 * 없어서 그대로 경사 계산에 들어갔다.
 *
 * 폭을 이렇게 잡은 이유: 부산에서 가장 높은 곳이 금정산 801m, 가장 낮은 곳이
 * 해수면이다. -500 ~ +2000m 는 **어떤 실제 지형도 안 걸리면서** 고장은 전부 잡는다.
 * 좁게 잡으면 진짜 산을 버리고, 넓게 잡으면 흠이 새어 들어온다.
 *
 * 고도를 읽는 쪽은 전부 이 상수를 쓴다 — collect/copernicus-dem.mjs 와
 * process/dem-crosscheck.mjs 가 같은 것을 본다.
 */
export const ELEV_MIN = -500, ELEV_MAX = 2000

/** 범위 밖이면 null. 값이 없어도 null. */
export const inRange = v => (v == null || !Number.isFinite(v) || v < ELEV_MIN || v > ELEV_MAX) ? null : v

const T = {                      // 우리가 쓰는 TIFF 태그만
  ImageWidth: 256, ImageLength: 257, BitsPerSample: 258, Compression: 259,
  SamplesPerPixel: 277, PlanarConfig: 284, Predictor: 317,
  TileWidth: 322, TileLength: 323, TileOffsets: 324, TileByteCounts: 325,
  SampleFormat: 339, ModelPixelScale: 33550, ModelTiepoint: 33922,
  GeoKeyDir: 34735, GDALNoData: 42113,
}
const TYPESZ = { 1: 1, 2: 1, 3: 2, 4: 4, 5: 8, 6: 1, 7: 1, 8: 2, 9: 4, 10: 8, 11: 4, 12: 8 }

/**
 * 헤더만 읽는다. 화소는 안 읽는다 — 그건 sample() 이 필요할 때 한 칸씩 푼다.
 * 반환된 객체는 파일 핸들을 들고 있으므로 다 쓰면 close() 를 부른다.
 */
export function openGeoTIFF(path) {
  const fd = openSync(path, 'r')
  const size = statSync(path).size
  const rd = (off, len) => { const b = Buffer.alloc(len); readSync(fd, b, 0, len, off); return b }

  const head = rd(0, 8)
  const bo = head.toString('ascii', 0, 2)
  if (bo !== 'II') { closeSync(fd); throw new Error(`little-endian 만 읽는다 (byteorder=${bo})`) }
  const magic = head.readUInt16LE(2)
  if (magic !== 42) { closeSync(fd); throw new Error(`classic TIFF 만 읽는다 (magic=${magic}, BigTIFF?)`) }

  // ── IFD0 — 태그 목록 ────────────────────────────────────────────────────
  const ifdOff = head.readUInt32LE(4)
  const nEntries = rd(ifdOff, 2).readUInt16LE(0)
  const entries = rd(ifdOff + 2, nEntries * 12)
  const tags = new Map()
  for (let i = 0; i < nEntries; i++) {
    const p = i * 12
    const tag = entries.readUInt16LE(p)
    const type = entries.readUInt16LE(p + 2)
    const count = entries.readUInt32LE(p + 4)
    const sz = (TYPESZ[type] || 1) * count
    // 4바이트에 들어가면 자리에 박혀 있고, 넘으면 그 자리는 파일 오프셋이다
    const blob = sz <= 4 ? entries.subarray(p + 8, p + 12) : rd(entries.readUInt32LE(p + 8), sz)
    const vals = []
    for (let k = 0; k < count; k++) {
      const q = k * (TYPESZ[type] || 1)
      if (type === 3) vals.push(blob.readUInt16LE(q))
      else if (type === 4) vals.push(blob.readUInt32LE(q))
      else if (type === 12) vals.push(blob.readDoubleLE(q))
      else if (type === 2) { vals.push(blob.toString('ascii', 0, count).replace(/\0+$/, '')); break }
      else vals.push(blob[q])
    }
    tags.set(tag, vals)
  }
  const one = (tag, dflt) => tags.has(tag) ? tags.get(tag)[0] : dflt

  // ── 우리가 읽을 수 있는 모양인지 확인. 아니면 던진다 ─────────────────────
  const must = (what, got, want) => {
    if (got !== want) { closeSync(fd); throw new Error(`${what}=${got} 미지원 (${want} 만 읽는다)`) }
  }
  must('BitsPerSample', one(T.BitsPerSample), 32)
  must('SampleFormat', one(T.SampleFormat, 1), 3)          // 3 = IEEE float
  must('SamplesPerPixel', one(T.SamplesPerPixel, 1), 1)
  must('PlanarConfig', one(T.PlanarConfig, 1), 1)
  must('Compression', one(T.Compression), 8)               // 8 = Adobe Deflate (zlib)
  must('Predictor', one(T.Predictor, 1), 3)                // 3 = 부동소수점 예측
  if (!tags.has(T.TileOffsets)) { closeSync(fd); throw new Error('스트립 TIFF 미지원 — 바둑판(tiled) 만 읽는다') }

  const width = one(T.ImageWidth), height = one(T.ImageLength)
  const tw = one(T.TileWidth), th = one(T.TileLength)
  const tileOffsets = tags.get(T.TileOffsets), tileBytes = tags.get(T.TileByteCounts)

  // ── 좌표: ModelPixelScale + ModelTiepoint ───────────────────────────────
  const scale = tags.get(T.ModelPixelScale)
  const tie = tags.get(T.ModelTiepoint)
  if (!scale || !tie) { closeSync(fd); throw new Error('ModelPixelScale/ModelTiepoint 없음 — 좌표를 못 정한다')
  }
  // 🔴 PixelIsPoint 확인. GeoKeyDir 은 4개씩 묶인 uint16 배열이고
  //    첫 묶음이 헤더(version, rev, minor, keyCount)다.
  const gk = tags.get(T.GeoKeyDir) || []
  let rasterType = 1
  for (let k = 1; k * 4 + 3 < gk.length; k++) if (gk[k * 4] === 1025) rasterType = gk[k * 4 + 3]
  if (rasterType !== 2) {
    closeSync(fd)
    throw new Error(`GTRasterType=${rasterType} 미지원 — PixelIsPoint(2) 만 읽는다. `
      + 'Area(1) 면 표본이 반 픽셀 밀리므로 조용히 통과시키지 않는다')
  }

  const [sx, sy] = scale                       // 도/픽셀 (GLO-30 은 1/3600 = 1초)
  const lon0 = tie[3], lat0 = tie[4]           // 픽셀 (0,0) 의 **한가운데** 좌표
  const nodata = tags.has(T.GDALNoData) ? Number(tags.get(T.GDALNoData)[0]) : null

  const tilesX = Math.ceil(width / tw)
  const cache = new Map()                      // "tx_ty" → Float32Array(tw*th)

  /** 안쪽 바둑판 한 칸을 푼다. 이미 푼 것은 다시 안 푼다. */
  function innerTile(tx, ty) {
    const key = `${tx}_${ty}`
    if (cache.has(key)) return cache.get(key)
    const idx = ty * tilesX + tx
    const raw = inflateSync(rd(tileOffsets[idx], tileBytes[idx]))

    // ── Predictor 3 되돌리기 ────────────────────────────────────────────
    // 두 단계다. libtiff 의 fpAcc 와 같은 순서로 한다.
    //  ① 한 줄(=바둑판 한 칸의 가로 한 줄) 안에서 **바이트 단위** 누적합.
    //     예측기는 float 이 아니라 바이트에 걸려 있다.
    //  ② 그 줄은 바이트 평면으로 갈라져 저장돼 있다 — 모든 화소의 1번째
    //     바이트가 먼저, 그다음 2번째 … 이렇게 4덩이다. 이걸 다시 짜맞춘다.
    //     맨 앞 평면이 **가장 큰 자리(MSB)** 라, little-endian 으로 되돌리려면
    //     거꾸로 넣는다.
    const rowBytes = tw * 4
    const out = new Float32Array(tw * th)
    const line = Buffer.alloc(rowBytes)
    for (let y = 0; y < th; y++) {
      const base = y * rowBytes
      for (let i = 1; i < rowBytes; i++) raw[base + i] = (raw[base + i] + raw[base + i - 1]) & 0xff  // ①
      for (let c = 0; c < tw; c++) {                                                                 // ②
        line[c * 4 + 3] = raw[base + 0 * tw + c]
        line[c * 4 + 2] = raw[base + 1 * tw + c]
        line[c * 4 + 1] = raw[base + 2 * tw + c]
        line[c * 4 + 0] = raw[base + 3 * tw + c]
      }
      for (let c = 0; c < tw; c++) out[y * tw + c] = line.readFloatLE(c * 4)
    }
    cache.set(key, out)
    return out
  }

  /** 화소 하나 (열 col, 행 row). 밖이면 null. */
  function at(col, row) {
    if (col < 0 || row < 0 || col >= width || row >= height) return null
    const t = innerTile(Math.floor(col / tw), Math.floor(row / th))
    const v = t[(row % th) * tw + (col % tw)]
    if (nodata !== null && v === nodata) return null
    return v
  }

  return {
    path, width, height, lon0, lat0, sx, sy, nodata, size,
    /** 이 파일이 덮는 픽셀 **중심** 의 경위도 범위 */
    bounds: { west: lon0, east: lon0 + (width - 1) * sx, north: lat0, south: lat0 - (height - 1) * sy },
    /** 경위도 → 실수 격자 좌표. PixelIsPoint 라 그대로 나눈다 */
    colOf: lon => (lon - lon0) / sx,
    rowOf: lat => (lat0 - lat) / sy,
    at,
    tileCount: tileOffsets.length,
    close: () => closeSync(fd),
    /** 캐시를 비운다 — 여러 파일을 훑을 때 메모리를 붙잡지 않게 */
    drop: () => cache.clear(),
  }
}
