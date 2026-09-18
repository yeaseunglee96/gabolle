#!/usr/bin/env node
/**
 * 부산 도시철도(지하철) 접근성 원본 수집 — 공공데이터포털 파일데이터
 *
 * 무엇을 만드나:
 *   "이 역까지 가면 엘리베이터로 올라갈 수 있나" 를 판정할 원본. 우리 온톨로지에
 *   유일하게 비어 있던 자리다 — 역(Station)은 있어도 **출구(StationEntrance)** 가 없었다.
 *
 *   🔴 이 CSV 들에는 좌표가 없다. 대신 **출입구번호**가 있고, 우리 디스크의
 *      data/raw/pbf/transit.ndjson 에 railway=subway_entrance 노드가 좌표와 함께
 *      있으며 그 노드의 `ref`(출구번호) 보유율이 99.6% 다 (2026-08-28 실측).
 *      그래서 좌표는 OSM 이 대고 시설 정보는 이 CSV 가 댄다.
 *      실제 결합은 process/subway-access.mjs 가 한다 — 여기는 네트워크만 한다.
 *
 * 이 파일이 네트워크를 쓰는 유일한 파일이다 (collect/ 규칙: 네트워크 O, 계산 X).
 *
 * 왜 원본 바이트를 그대로 두나:
 *   collect/bims-poll.mjs 가 응답 원문을 그대로 남기는 것과 같은 이유다.
 *   **파싱은 나중에 바꿀 수 있지만 안 받은 데이터는 못 만든다.**
 *   한국 공공 CSV 는 인코딩이 파일마다 다르다 (여기서도 CP949 와 UTF-16LE 가 섞여
 *   나온다). 받는 쪽에서 글자로 바꿔 저장하면 그 순간 되돌릴 수 없는 손실이 생긴다.
 *   그래서 **바이트는 손대지 않고, 어떤 인코딩으로 보이는지만 _manifest.json 에 적는다.**
 *
 * 로그인:
 *   🔴 이 스크립트는 로그인·회원가입·본인인증을 하지 않는다. 필요한 출처가 있으면
 *      멈추고 `_manifest.json` 의 `unavailable` 에 이유를 적는다. 사람이 처리한다.
 *      (2026-08-28 확인: 아래 파일데이터 4종은 전부 로그인 없이 받힌다.
 *       열차시각표는 오픈API 뿐이고 활용신청=로그인이 필요해서 못 받았다.
 *       🟢 2026-09-18 — 그 활용신청이 됐다. 시각표는 collect/subway-timetable.mjs 가 받는다.)
 *
 * 실행:
 *   node collect/subway.mjs
 *   node collect/subway.mjs --dry-run    # 내려받지 않고 접근 가능 여부만 검사
 *
 * 종료 코드:
 *   0  필수 3종(엘리베이터·역사 편의시설·역사정보)을 전부 받았다
 *   1  사이트는 살아 있는데 필수 중 일부를 못 받았다 (부분 실패를 숨기지 않는다)
 *   2  아무것도 못 받았다 = 입력이 없다 (망 차단·사이트 점검)
 */
import { mkdir, writeFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

import { stamp } from '../mlops/manifest.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const OUT = join(ROOT, 'data/raw/subway')

const DRY = process.argv.includes('--dry-run')

/** data.go.kr 은 브라우저가 아닌 요청을 거른다. UA 와 Referer 를 붙여야 200 이 온다. */
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36'

/**
 * 받을 것. `id` 는 공공데이터포털 데이터셋 번호다.
 *
 * 🔴 다운로드 URL(atchFileId)을 여기에 박지 않는다. 그 값은 기관이 파일을 갱신할
 *    때마다 바뀌고, 박아두면 어느 날 조용히 낡은 파일을 받거나 404 가 된다.
 *    대신 데이터셋 페이지의 JSON-LD(`schema.org/DataDownload`) 안에 있는
 *    `contentUrl` 을 매번 읽는다 — 페이지가 스스로 알려주는 현재 파일이다.
 */
const DATASETS = [
  {
    key: 'elevator',
    id: 15119875,
    name: '부산교통공사_엘리베이터 정보',
    required: true,
    gives: '호선 · 역명 · 출입구번호 · 상세위치 · 시작층/종료층',
    why: '🔴 조인의 주인공. 출입구번호가 있어서 OSM 출구 노드의 ref 와 붙는다 = 휠체어·캐리어 판정의 직접 입력',
  },
  {
    key: 'facility',
    id: 15052664,
    name: '부산교통공사_역사 편의시설현황',
    required: true,
    gives: '역별 에스컬레이터·외부경사로(지상역 출구)·엘리베이터(내부/외부)·휠체어리프트·시각장애인 유도로 개수',
    why: '역 단위 정보다. 출구가 아니라 **역**에 붙는다 — 그 차이를 필드 이름으로 구분한다',
  },
  {
    key: 'station',
    id: 15043686,
    name: '부산교통공사_도시철도역사정보',
    required: true,
    gives: '역번호 · 역사명 · 노선 · 역위도/역경도 · 환승역구분 · 도로명주소',
    why: '🔴 조인의 안전장치. 역명만으로 붙이면 동해선 좌천역이 1호선 좌천역의 엘리베이터를 물려받는다. 역 중심 좌표로 거리를 재서 그런 조인을 거부한다',
  },
  {
    key: 'ridership',
    id: 3057229,
    name: '부산교통공사_시간대별 승하차인원',
    required: false,
    gives: '역번호 · 일자 · 요일 · 승/하차 · 시간대별(01시~24시) 인원',
    why: '역 혼잡. 조작할 수 없는 관측 신호다 (CLAUDE.md 2절)',
  },
]

/**
 * 못 받던 것 중 **풀린 것** — 지우지 않고 날짜와 함께 남긴다.
 *
 * 🔴 낡은 실측을 지우면 다음 사람이 「왜 이건 안 받나」를 처음부터 다시 조사한다.
 *    이 저장소가 여러 번 겪은 실패라 정정은 덮어쓰기가 아니라 덧붙이기로 한다.
 */
const RESOLVED = [
  {
    key: 'timetable',
    id: 15158990,
    name: '부산교통공사_부산도시철도 열차시각표 조회 서비스_GW',
    wasBlockedBy:
      '활용신청이 필요하고 활용신청은 로그인을 요구한다. 엔드포인트 주소는 로그인 전에는 페이지에 노출되지 않아 지어내지 않았다. (2026-08-28 조사)',
    resolvedAt: '2026-09-18',
    resolvedHow:
      '사람이 활용신청을 했고(심의 자동승인·이용허락범위 "제한 없음"), 규격이 서비스 페이지 안에 Swagger 로 들어 있어 지어내지 않고 그대로 옮겼다. ' +
      'https://apis.data.go.kr/B551542/trainTime/getTrainTime — serviceKey·act=json·scode 가 필수. scode 는 station.csv 의 「역번호」와 같은 값이다.',
    nowCollectedBy: 'collect/subway-timetable.mjs',
    unblocks: '역간 소요시간과 배차간격 — 대중교통 경로가 시간을 낼 수 있게 된다',
  },
]

/**
 * 아직 못 받는 것 — 지어내지 않고 이유를 적는다.
 *
 * 🔴 이 목록을 읽는 사람이 "왜 이 칸이 비어 있나" 를 다시 조사하지 않게 하려고 남긴다.
 *    풀린 것은 위 RESOLVED 로 옮기되 **지우지는 않는다.**
 */
const UNAVAILABLE = [
  {
    key: 'transfer-walk',
    id: null,
    name: '환승 통로 도보 거리',
    kind: '공개된 데이터 없음',
    reason:
      '서울교통공사는 환승 도보거리를 공개하지만 부산교통공사는 시설안내도를 **이미지로만** 제공한다. ' +
      '부산 환승역은 7곳(서면·연산·수영·덕천·미남·동래·사상 — 역사정보 CSV 의 환승역 표기 실측)뿐이라 나중에 손으로 잴 수 있다.',
    blocks: 'transferWalkMeters 가 null 로 남는다. 🔴 추정해서 채우지 않는다',
    humanStep: '부산교통공사 역사 시설안내도를 보고 7개 환승역만 손으로 입력',
  },
  {
    key: 'dataset-15000522',
    id: 15000522,
    name: '(앞선 조사가 "역별 시각표" 로 지목한 데이터셋 번호)',
    kind: '존재하지 않음',
    reason:
      '🔴 2026-08-28 확인: /data/15000522/fileData.do · openapi.do · standard.do 전부 HTTP 404. ' +
      '그 번호의 데이터셋은 없다. 부산 도시철도 시각표는 위 15158990 오픈API 하나뿐이다.',
    blocks: null,
    humanStep: null,
  },
]

const sha256 = (buf) => createHash('sha256').update(buf).digest('hex')

/**
 * 인코딩 추정 — **바꾸지 않고 이름만 붙인다.**
 *
 * 한국 공공 CSV 는 파일마다 다르다. 실제로 여기서 받는 4개 중 3개는 CP949
 * (=EUC-KR 확장), 역사정보 1개는 UTF-16LE + 탭 구분인데 확장자만 .csv 다.
 * UTF-8 로 읽어서 글자가 깨지면 CP949 로 다시 읽는 판단을 **읽는 쪽**
 * (process/subway-access.mjs) 이 하고, 여기서는 그 판단의 근거만 기록한다.
 */
function sniffEncoding(buf) {
  if (buf.length >= 2 && buf[0] === 0xff && buf[1] === 0xfe) return 'utf-16le'
  if (buf.length >= 2 && buf[0] === 0xfe && buf[1] === 0xff) return 'utf-16be'
  if (buf.length >= 3 && buf[0] === 0xef && buf[1] === 0xbb && buf[2] === 0xbf) return 'utf-8-bom'
  // UTF-8 로 엄격하게 디코딩해서 통과하면 UTF-8. 실패하면 한국 공공 CSV 의 기본인 CP949.
  try {
    new TextDecoder('utf-8', { fatal: true }).decode(buf)
    return 'utf-8'
  } catch {
    // euc-kr 로 읽었을 때 한글이 실제로 나오는지까지 본다 — 그냥 "utf-8 아님" 은 근거가 약하다.
    const t = new TextDecoder('euc-kr').decode(buf.subarray(0, 4096))
    return /[가-힣]/.test(t) ? 'cp949' : 'unknown'
  }
}

async function get(url, referer) {
  const res = await fetch(url, {
    headers: { 'User-Agent': UA, ...(referer ? { Referer: referer } : {}), 'Accept-Language': 'ko' },
    redirect: 'follow',
  })
  return res
}

/** 데이터셋 페이지에서 지금 유효한 다운로드 URL 과 메타데이터를 읽는다. */
async function probe(ds) {
  const page = `https://www.data.go.kr/data/${ds.id}/fileData.do`
  const res = await get(page)
  if (!res.ok) return { page, ok: false, http: res.status, error: `페이지 HTTP ${res.status}` }
  const html = await res.text()

  // JSON-LD 안의 DataDownload.contentUrl — 페이지가 스스로 밝히는 현재 파일 주소
  const m = html.match(/"contentUrl"\s*:\s*"([^"]*fileDownload\.do[^"]*)"/)
  const flat = (kw) => {
    const i = html.indexOf(kw)
    if (i < 0) return null
    return html.slice(i, i + 400).replace(/<[^>]*>/g, ' ').replace(/&#034;/g, '"').replace(/\s+/g, ' ').trim()
  }
  const lic = flat('이용허락범위')
  const title = (html.match(/<title>([^<|]*)/) || [])[1]?.trim() || null

  return {
    page,
    ok: !!m,
    http: res.status,
    downloadUrl: m ? m[1].replace(/&amp;/g, '&') : null,
    portalTitle: title,
    // 발표에서 무너지지 않으려면 라이선스는 페이지에서 읽은 문구를 그대로 남긴다
    licenseText: lic ? lic.replace(/^이용허락범위\s*이용허락범위\s*/, '').replace(/\s*이 페이지.*$/, '').trim() : null,
    noLoginText: html.includes('로그인 없이 다운로드')
      ? '파일데이터는 로그인 없이 다운로드를 통해 이용하실 수 있습니다 (포털 문구)'
      : null,
    rows: flat('전체 행')?.match(/전체 행\s*([\d,]+)/)?.[1] ?? null,
    error: m ? null : 'contentUrl 을 페이지에서 못 찾았다 (포털 화면 구조가 바뀌었을 수 있다)',
  }
}

async function download(ds, info) {
  const res = await get(info.downloadUrl, info.page)
  if (!res.ok) return { ok: false, error: `다운로드 HTTP ${res.status}` }
  const buf = Buffer.from(await res.arrayBuffer())
  if (buf.length === 0) return { ok: false, error: '빈 응답 (0 바이트)' }

  // 로그인 벽에 부딪히면 CSV 대신 HTML 이 온다. 그것을 CSV 로 저장하면 안 된다.
  const ctype = res.headers.get('content-type') || ''
  if (/text\/html/i.test(ctype)) {
    return { ok: false, error: `CSV 가 아니라 HTML 이 왔다 (content-type: ${ctype}) — 로그인 벽일 가능성` }
  }

  // 기관이 붙인 원래 파일명. Content-Disposition 은 퍼센트 인코딩돼 있다.
  const cd = res.headers.get('content-disposition') || ''
  let original = (cd.match(/filename="?([^"]+)"?/) || [])[1] || null
  if (original) { try { original = decodeURIComponent(original) } catch { /* 그대로 둔다 */ } }

  const encoding = sniffEncoding(buf)
  const file = join(OUT, `${ds.key}.csv`)
  if (!DRY) await writeFile(file, buf)          // 🔴 바이트 그대로. 디코딩하지 않는다

  return {
    ok: true,
    saved: `data/raw/subway/${ds.key}.csv`,
    originalFilename: original,
    bytes: buf.length,
    sha256: sha256(buf),
    encoding,
    contentType: ctype || null,
  }
}

async function main() {
  await mkdir(OUT, { recursive: true })
  console.log(`부산 도시철도 접근성 원본 수집${DRY ? ' (dry-run — 저장하지 않는다)' : ''}`)
  console.log(`대상 폴더: data/raw/subway/\n`)

  const got = []
  const failed = []

  for (const ds of DATASETS) {
    process.stdout.write(`[${ds.id}] ${ds.name} … `)
    let info, dl
    try {
      info = await probe(ds)
    } catch (e) {
      console.log(`실패 (페이지 접근: ${e.message})`)
      failed.push({ ...ds, error: `페이지 접근 실패: ${e.message}` })
      continue
    }
    if (!info.ok) {
      console.log(`실패 (${info.error})`)
      failed.push({ ...ds, ...info })
      continue
    }
    if (DRY) {
      console.log(`OK (접근 가능, 행 ${info.rows ?? '?'}, 이용허락범위 "${info.licenseText}")`)
      got.push({ ...ds, ...info, ok: true, dryRun: true })
      continue
    }
    try {
      dl = await download(ds, info)
    } catch (e) {
      console.log(`실패 (다운로드: ${e.message})`)
      failed.push({ ...ds, ...info, error: `다운로드 실패: ${e.message}` })
      continue
    }
    if (!dl.ok) {
      console.log(`실패 (${dl.error})`)
      failed.push({ ...ds, ...info, ...dl })
      continue
    }
    console.log(`OK  ${(dl.bytes / 1024).toFixed(0)}KB  ${dl.encoding}  "${dl.originalFilename}"`)
    got.push({ ...ds, ...info, ...dl })
  }

  console.log('')
  for (const u of UNAVAILABLE) {
    console.log(`🔴 못 받음 — ${u.name}: ${u.reason.split('.')[0]}.`)
  }

  const manifest = {
    at: new Date().toISOString(),
    script: 'collect/subway.mjs',
    dryRun: DRY,
    note:
      '원본 바이트를 그대로 저장한다. 디코딩은 process/subway-access.mjs 가 encoding 필드를 보고 한다. ' +
      '좌표는 이 CSV 들에 없고 data/raw/pbf/transit.ndjson 의 railway=subway_entrance 노드에서 온다.',
    obtained: got.map((g) => ({
      key: g.key,
      datasetId: g.id,
      name: g.name,
      portalTitle: g.portalTitle,
      datasetPage: g.page,
      downloadUrl: g.downloadUrl,
      license: g.licenseText,
      loginRequired: g.noLoginText ? false : null,
      loginNote: g.noLoginText,
      portalRows: g.rows,
      saved: g.saved ?? null,
      originalFilename: g.originalFilename ?? null,
      bytes: g.bytes ?? null,
      sha256: g.sha256 ?? null,
      encoding: g.encoding ?? null,
      gives: g.gives,
      why: g.why,
    })),
    failed: failed.map((f) => ({ key: f.key, datasetId: f.id, name: f.name, required: f.required, error: f.error })),
    unavailable: UNAVAILABLE,
    resolved: RESOLVED,
  }
  if (!DRY) await writeFile(join(OUT, '_manifest.json'), JSON.stringify(manifest, null, 1))

  const missingRequired = DATASETS.filter((d) => d.required && !got.some((g) => g.key === d.key))
  console.log(`\n받음 ${got.length}/${DATASETS.length}  ·  못 받음(사전 확인) ${UNAVAILABLE.length}건`)

  if (got.length === 0) {
    console.error('🔴 아무것도 못 받았다 — 입력이 없다. 망 차단이나 사이트 점검을 의심하라.')
    process.exit(2)
  }
  if (missingRequired.length) {
    console.error(`🔴 필수 데이터가 빠졌다: ${missingRequired.map((d) => d.key).join(', ')}`)
    process.exit(1)
  }

  if (!DRY) {
    stamp(OUT, {
      step: 'collect/subway',
      inputs: [],
      params: { datasets: DATASETS.map((d) => d.id), unavailable: UNAVAILABLE.map((u) => u.key), resolved: RESOLVED.map((r) => r.key) },
      result: { obtained: got.length, bytes: got.reduce((s, g) => s + (g.bytes || 0), 0) },
    })
  }
  console.log('완료. 다음: node process/subway-access.mjs')
}

main().catch((e) => {
  console.error('🔴 예상 못 한 실패:', e)
  process.exit(1)
})
