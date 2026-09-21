// 여행 이름은 **페이지가 아니다** — 시안 ④ 가 없앤 라우트가 되살아나지 않게 잠근다.
//
// 🔴 지운 라우트로 가는 줄이 하나라도 남으면 눌렀을 때 **빈 화면**이 뜬다. 타입도 시험도
//    안 잡는다 — expo-router 의 주소는 그냥 문자열이라서다. 실제로 이 저장소에서 자산
//    경로가 같은 이유로 두 번 깨졌다.
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '..', '..', '..');
const DELETED = path.join(ROOT, 'app', '(trip)', '[id]', 'name.tsx');

/** app/ 과 src/ 의 소스를 전부 모은다. */
function sources(dir, found = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name === '__tests__') continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) sources(full, found);
    else if (/\.(ts|tsx)$/.test(entry.name)) found.push(full);
  }
  return found;
}

describe('여행 이름 페이지', () => {
  it('🔴 라우트 파일이 없다 — 이름은 겹쳐 뜨는 것이지 화면이 아니다', () => {
    expect(fs.existsSync(DELETED)).toBe(false);
  });

  it('🔴 그 주소로 가는 줄이 아무 데도 없다 — 남아 있으면 눌렀을 때 빈 화면이 뜬다', () => {
    const offenders = [];
    for (const file of [...sources(path.join(ROOT, 'app')), ...sources(path.join(ROOT, 'src'))]) {
      const text = fs.readFileSync(file, 'utf8');
      // `/{tripId}/name` 꼴로 가는 곳. 주소 조각이라 정규식으로만 잡힌다.
      for (const line of text.split('\n')) {
        // 🔴 주석은 뺀다. 「전에는 이 주소였다」고 적어 둔 설명까지 잡으면, 왜 없앴는지
        //    적지 못하게 된다 — 시험이 기록을 막는 꼴이다.
        const code = line.replace(/\/\/.*$/, '').replace(/\/\*.*?\*\//g, '');
        if (/\/name\?|\/name['"`]|pathname:\s*['"`]\/\[id\]\/name/.test(code)) {
          offenders.push(`${path.relative(ROOT, file)} → ${line.trim().slice(0, 90)}`);
        }
      }
    }
    expect(offenders).toEqual([]);
  });

  it('찾는 방법 자체가 살아 있다 — 있는 주소는 있다고 말한다', () => {
    const itinerary = path.join(ROOT, 'app', 'trips', '[id]', 'itinerary.tsx');
    expect(fs.existsSync(itinerary)).toBe(true);
    expect(fs.readFileSync(itinerary, 'utf8')).toContain('TripNameSheet');
  });
});
