/**
 * 최소 PNG 디코더 — terrarium 고도 타일 전용 (256x256, RGB 8bit, 비인터레이스)
 *
 * 의존성을 늘리지 않으려고 직접 푼다. Node 의 zlib 이 압축을 풀어주고,
 * 남는 일은 PNG 의 스캔라인 필터를 되돌리는 것뿐이다.
 * 범용 디코더가 아니다 — 팔레트·인터레이스·16bit 는 거부한다.
 */
import { inflateSync } from 'node:zlib'

const SIG = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])

export function decodePNG(buf) {
  if (!buf.subarray(0, 8).equals(SIG)) throw new Error('PNG 시그니처가 아니다')
  let off = 8, ihdr = null
  const idat = []

  while (off < buf.length) {
    const len  = buf.readUInt32BE(off)
    const type = buf.toString('ascii', off + 4, off + 8)
    const data = buf.subarray(off + 8, off + 8 + len)
    if (type === 'IHDR') {
      ihdr = {
        width: data.readUInt32BE(0), height: data.readUInt32BE(4),
        bitDepth: data[8], colorType: data[9], interlace: data[12],
      }
    } else if (type === 'IDAT') idat.push(data)
    else if (type === 'IEND') break
    off += 12 + len                       // len + type(4) + data + crc(4)
  }

  if (!ihdr) throw new Error('IHDR 없음')
  if (ihdr.bitDepth !== 8)   throw new Error(`bitDepth ${ihdr.bitDepth} 미지원`)
  if (ihdr.interlace !== 0)  throw new Error('인터레이스 미지원')
  const channels = { 0: 1, 2: 3, 4: 2, 6: 4 }[ihdr.colorType]
  if (!channels) throw new Error(`colorType ${ihdr.colorType} 미지원 (팔레트?)`)

  const raw = inflateSync(Buffer.concat(idat))
  const { width: w, height: h } = ihdr
  const bpp = channels, stride = w * bpp
  const out = Buffer.alloc(h * stride)

  for (let y = 0; y < h; y++) {
    const filter = raw[y * (stride + 1)]
    const line   = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1))
    const cur    = out.subarray(y * stride, (y + 1) * stride)
    const prev   = y > 0 ? out.subarray((y - 1) * stride, y * stride) : null

    for (let i = 0; i < stride; i++) {
      const a = i >= bpp ? cur[i - bpp] : 0            // 왼쪽
      const b = prev ? prev[i] : 0                     // 위
      const c = (prev && i >= bpp) ? prev[i - bpp] : 0 // 왼쪽 위
      let v = line[i]
      switch (filter) {
        case 0: break
        case 1: v += a; break
        case 2: v += b; break
        case 3: v += (a + b) >> 1; break
        case 4: {                                      // Paeth
          const p = a + b - c
          const pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c)
          v += (pa <= pb && pa <= pc) ? a : (pb <= pc ? b : c)
          break
        }
        default: throw new Error(`알 수 없는 필터 ${filter} (행 ${y})`)
      }
      cur[i] = v & 0xff
    }
  }
  return { width: w, height: h, channels, data: out }
}

/** terrarium: 고도(m) = (R*256 + G + B/256) - 32768 */
export function terrariumToElevation(png) {
  const { width, height, channels, data } = png
  const elev = new Float32Array(width * height)
  for (let i = 0, p = 0; i < elev.length; i++, p += channels)
    elev[i] = (data[p] * 256 + data[p + 1] + data[p + 2] / 256) - 32768
  return elev
}
