// 마이페이지 하위 화면은 **페이지가 아니다** — 겹쳐 여는 것 하나만 남긴다 (-1331).
//
// 🔴 지운 주소로 가는 줄이 하나라도 남으면 눌렀을 때 **빈 화면**이 뜬다. 타입도 시험도
//    안 잡는다 — expo-router 의 주소는 그냥 문자열이라서다. 실제로 이 저장소에서 같은
//    이유로 두 번 깨졌다(여행 이름 페이지, 자산 경로).
//
// 🔴 **API 경로는 건드리지 않는다.** `/api/v1/me/preferences` 같은 서버 주소가 글자로는
//    겹친다. 그래서 따옴표 바로 뒤에 `/me/` 가 오는 것만 잡는다 — 서버 주소는 그 사이에
//    `/api/v1` 이 끼어 있어 안 걸린다.
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '..', '..', '..');
const GONE = ['profile', 'preferences', 'posts', 'saved', 'identities', 'blocked', 'terms'];

function sources(dir, found = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name === '__tests__') continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) sources(full, found);
    else if (/\.(ts|tsx)$/.test(entry.name)) found.push(full);
  }
  return found;
}

describe('마이페이지 하위 주소', () => {
  it('🔴 app/me/ 가 통째로 없다 — 하위 화면은 겹쳐 뜨는 것이지 페이지가 아니다', () => {
    expect(fs.existsSync(path.join(ROOT, 'app', 'me'))).toBe(false);
  });

  it('🔴 그 일곱으로 가는 줄이 아무 데도 없다 — 남아 있으면 눌렀을 때 빈 화면이 뜬다', () => {
    const pattern = new RegExp(`['"\`]/me/(${GONE.join('|')})\\b`);
    const offenders = [];
    for (const file of [...sources(path.join(ROOT, 'app')), ...sources(path.join(ROOT, 'src'))]) {
      for (const line of fs.readFileSync(file, 'utf8').split('\n')) {
        // 🔴 주석은 뺀다. 「전에는 이 주소였다」고 적어 둔 설명까지 잡으면, 왜 없앴는지
        //    적지 못하게 된다 — 시험이 기록을 막는 꼴이다.
        const code = line.replace(/\/\/.*$/, '').replace(/\/\*.*?\*\//g, '');
        if (pattern.test(code)) {
          offenders.push(`${path.relative(ROOT, file)} → ${line.trim().slice(0, 90)}`);
        }
      }
    }
    expect(offenders).toEqual([]);
  });

  it('🔴 서버 주소는 안 건드린다 — 글자가 겹친다고 잡으면 취향 읽기가 막힌다', () => {
    const pattern = new RegExp(`['"\`]/me/(${GONE.join('|')})\\b`);
    expect(pattern.test(`'/api/v1/me/preferences'`)).toBe(false);
    expect(pattern.test(`'/me/preferences'`)).toBe(true);
  });

  it('대신 가는 길이 살아 있다 — 밖에서는 창을 지목해 들어온다', () => {
    const me = fs.readFileSync(path.join(ROOT, 'app', '(tabs)', 'me.tsx'), 'utf8');
    expect(me).toContain('isPanelKey');
    // 🔴 한 번 열고 다시는 안 연다. 이 기억이 없으면 시트를 내려도 주소에 값이 그대로라
    //    곧바로 다시 열린다 — 내려지지 않는 시트가 된다.
    expect(me).toContain('openedFromUrl');
  });

  it('🔴 웹 소셜 연결이 돌아올 자리가 있다 — 없으면 연결을 마치고 빈 화면에 떨어진다', () => {
    const body = fs.readFileSync(path.join(ROOT, 'src', 'me', 'panels', 'IdentitiesBody.tsx'), 'utf8');
    expect(body).toContain("'/me?panel=identities'");
  });
});
