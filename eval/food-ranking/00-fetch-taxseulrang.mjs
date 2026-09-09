// 2026 택슐랭 가이드맵(구글 My Maps 공개 지도)의 KML 을 정답지 TSV 로 옮긴다.
//
// 이 지도는 부산시가 만든 축제 가이드맵이고, 공개로 열려 있어 구글 My Maps 가
// 제공하는 KML 내보내기 주소로 그대로 받았다. 크롤링이 아니라 **그 서비스가 낸
// 내보내기 창구**다.

import fs from 'node:fs'

const SRC = process.argv[2]
const OUT = process.argv[3]
const kml = fs.readFileSync(SRC, 'utf8')

const cdata = (s) => (s ?? '').replace(/^<!\[CDATA\[/, '').replace(/\]\]>$/, '').trim()
const tag = (block, name) => {
  const m = block.match(new RegExp(`<${name}>([\\s\\S]*?)</${name}>`))
  return m ? cdata(m[1]) : ''
}
/** <Data name="X"><value>Y</value></Data> 에서 Y 를 꺼낸다. */
const data = (block, key) => {
  const m = block.match(new RegExp(`<Data name="${key}">\\s*<value>([\\s\\S]*?)</value>`))
  return m ? cdata(m[1]).replace(/\s+/g, ' ').trim() : ''
}
/** 폴더 이름의 "… <국밥>" 에서 꺾쇠 안만 남긴다. 없으면 통째로. */
const shortCat = (name) => {
  const m = name.match(/<([^<>]+)>\s*$/)
  return (m ? m[1] : name).trim()
}
/** "돼지국밥(10,000원)" → 10000. 여러 개면 첫 번째. 없으면 빈 칸. */
const priceOf = (pick) => {
  const m = (pick ?? '').match(/([\d,]+)\s*원/)
  if (!m) return ''
  const n = Number(m[1].replace(/,/g, ''))
  return Number.isFinite(n) ? String(n) : ''
}

// 폴더별로 Placemark 를 모은다 — 폴더 이름이 곧 음식 분류다.
const rows = []
const folders = kml.split('<Folder>').slice(1)
for (const folder of folders) {
  const cat = shortCat(tag(folder, 'name'))
  for (const pm of folder.split('<Placemark>').slice(1)) {
    const name = tag(pm, 'name')
    if (!name) continue
    const coord = (pm.match(/<coordinates>([\s\S]*?)<\/coordinates>/) || [])[1] ?? ''
    const [lon = '', lat = ''] = coord.trim().split(',')
    const pick = data(pm, '추천 pick')
    rows.push({
      상호: name.replace(/\s+/g, ' ').trim(),
      분류: cat,
      도로명주소: data(pm, '도로명 주소'),
      위도: lat ? Number(lat).toFixed(6) : '',
      경도: lon ? Number(lon).toFixed(6) : '',
      추천메뉴: pick,
      가격: priceOf(pick),
      로컬포인트: data(pm, '로컬 포인트'),
      한입태그: data(pm, '한입태그'),
      영업시간: data(pm, '영업시간'),
    })
  }
}

const cols = ['상호', '분류', '도로명주소', '위도', '경도', '추천메뉴', '가격', '로컬포인트', '한입태그', '영업시간']
const head = [
  '# 출처: 2026 택슐랭 가이드맵 — 제11회 부산원도심활성화축제 (부산광역시). 구글 My Maps 공개 지도',
  '#       https://www.google.com/maps/d/u/0/viewer?mid=1eSft2ATXR6AIiePkxH59lvVe0yCnjys',
  '# 수집: 구글 My Maps 의 KML 내보내기 주소(/maps/d/kml?mid=…&forcekml=1)로 받음. 크롤링 아님.',
  `# 수집일: ${new Date().toISOString().slice(0, 10)}`,
  '# 용도: 내부 채점표(정답지)로만 사용. 앱 빌드에 넣지 않는다.',
  '#',
  '# 🔴 이 정답지가 앞의 셋과 다른 점 — 고른 사람이 다르다.',
  '#    미쉐린·블루리본은 전문가가, 블로그100 은 글 쓰는 사람이 골랐다.',
  '#    택슐랭은 부산 길을 10년 넘게 누빈 택시기사가 골랐고, 범위가 관광지가',
  '#    아니라 원도심(중구·서구·동구·영도구)과 남구다. 우리 모델이 맞히려는',
  '#    "현지인이 가는 맛집" 에 가장 가까운 목록이다.',
  '#',
  '# 🔴 그리고 이 목록에는 앞의 셋에 없던 것이 셋 있다 — 도로명 주소 · 좌표 · 추천 메뉴 가격.',
  '#    주소와 좌표는 후보 풀 매칭(정답표를 우리 후보에 붙이는 일)에 직접 쓰인다.',
  '#    그 매칭이 라운드1 에서 329건 중 37곳밖에 안 붙었던 가장 큰 벽이었다.',
  `# 컬럼: ${cols.join('\\t')}`,
].join('\n')

const body = rows.map((r) => cols.map((c) => r[c] ?? '').join('\t')).join('\n')
fs.writeFileSync(OUT, head + '\n' + body + '\n', 'utf8')

console.log(`${rows.length}곳`)
console.log('분류별:', Object.entries(rows.reduce((a, r) => ((a[r.분류] = (a[r.분류] ?? 0) + 1), a), {})).map(([k, v]) => `${k} ${v}`).join(' · '))
console.log('주소 있는 곳:', rows.filter((r) => r.도로명주소).length, '· 좌표:', rows.filter((r) => r.위도).length, '· 가격:', rows.filter((r) => r.가격).length)
