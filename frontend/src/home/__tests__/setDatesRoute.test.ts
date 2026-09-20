// 「날짜 정하기」·「수정」이 그냥 홈으로 튕기던 것 — S15P21E201-1350.
//
// 🔴 실기에서 두 번 확인했다.
//      2026-09-19 일본어 「編集」  → 홈. 아무것도 안 열림
//      2026-09-20 영어   「Set dates」 → 홈. 아무것도 안 열림
//
//    문항 화면 바로 위 주석이 「막아 놓고 문을 안 준 상태였다」라고 적어 두고 문을 달았는데,
//    그 문이 **아무것도 열려 있지 않은 방**으로 이어져 있었다.
declare const require: (id: string) => any;
declare const __dirname: string;

import { EMPTY_START_BAR, startBarEditSection, startBarFromDraft } from '@/home/startBarValue';

const { readFileSync } = require('fs');
const { join } = require('path');

const FRONTEND = join(__dirname, '..', '..', '..');
const read = (...parts: string[]) => readFileSync(join(FRONTEND, ...parts), 'utf8') as string;

describe('주소의 ?edit= 를 열 칸으로 바꾼다', () => {
  it('아는 값이면 그 칸을 연다', () => {
    expect(startBarEditSection('dates')).toBe('dates');
    expect(startBarEditSection('origin')).toBe('origin');
    expect(startBarEditSection('people')).toBe('people');
  });

  it('배열로 와도 첫 값을 본다 — expo-router 가 그렇게 줄 때가 있다', () => {
    expect(startBarEditSection(['dates', 'origin'])).toBe('dates');
  });

  it('🔴 모르는 값이면 아무 칸도 안 연다 — 예전처럼 접힌 바가 뜰 뿐이라 나빠지지 않는다', () => {
    expect(startBarEditSection(undefined)).toBeNull();
    expect(startBarEditSection('')).toBeNull();
    expect(startBarEditSection('DATES')).toBeNull();
    expect(startBarEditSection('아무거나')).toBeNull();
    expect(startBarEditSection([])).toBeNull();
  });
});

describe('이미 답한 것을 시작 바에 다시 채운다', () => {
  const draft = {
    origin: '부산역', originLat: 35.1152, originLng: 129.0403,
    startDate: '2026-09-20', endDate: '2026-09-22',
    adults: 3, children: 1,
  };

  it('🔴 칸을 하나도 안 빠뜨린다 — 하나라도 빠지면 그 값은 화면에서 사라진다', () => {
    expect(startBarFromDraft(draft)).toEqual(draft);
  });

  it('인원이 비어 있으면 시작 바의 기본값을 쓴다 — 0명짜리 여행은 없다', () => {
    const empty = startBarFromDraft({ ...draft, adults: 0, children: 0 });
    expect(empty.adults).toBe(EMPTY_START_BAR.adults);
    expect(empty.children).toBe(0);
  });

  it('출발지를 아직 안 골랐으면 좌표는 비운 채로 옮긴다', () => {
    const noOrigin = startBarFromDraft({ ...draft, origin: '', originLat: null, originLng: null });
    expect(noOrigin.originLat).toBeNull();
    expect(noOrigin.startDate).toBe('2026-09-20');
  });
});

describe('화면들이 실제로 그렇게 이어져 있다', () => {
  const QUESTIONS = read('app', '(plan)', 'questions.tsx');
  const HOME = read('app', '(tabs)', 'home.tsx');
  const INDEX = read('app', 'index.tsx');
  const BAR = read('src', 'home', 'PlanStartBar.tsx');

  it('🔴 문항 화면은 날짜를 «그 자리에서» 고른다 — 홈으로 보내지 않는다 (S15P21E201-1376)', () => {
    // 1350 은 홈의 날짜 칸을 열어 보냈다. 그래도 돌아오면 문항이 1번부터라(2026-09-21 실기)
    // 이제는 문항 화면 안의 달력 카드(DateRangeCard)를 편다. 밀어 보내는 옛 꼴 둘 다 없어야 한다.
    expect(QUESTIONS).toContain('DateRangeCard');
    expect(QUESTIONS).not.toContain("params: { edit: 'dates' }");
    expect(QUESTIONS).not.toContain("router.push(wide ? '/' : '/home')");
  });

  it('🔴 좁은 화면과 넓은 화면 «둘 다» 그 값을 읽는다 — 한쪽만 고치면 그쪽에서만 열린다', () => {
    for (const source of [HOME, INDEX]) {
      expect(source).toContain('startBarEditSection(useLocalSearchParams().edit)');
      expect(source).toContain('initialSection={editSection}');
      expect(source).toContain('startBarFromDraft(planDraft)');
    }
  });

  it('시작 바가 그 둘을 처음 상태로 쓴다', () => {
    expect(BAR).toContain('useState<StartBarValue>(initialValue ?? EMPTY_START_BAR)');
    // 🔴 2026-09-21 — 모바일 전체 화면 시트가 생기면서 뒤에 갈래가 붙었다
    //    (`initialSection ?? (sheet ? 'origin' : null)`). 그래서 앞부분만 본다.
    //    이 시험이 막는 것은 「initialSection 을 처음 상태로 쓰는가」이고 그건 그대로다.
    //    통째로 지우지 않고 기준점만 옮긴다 — 지우면 이 방어가 같이 사라진다.
    expect(BAR).toContain('useState<Section>(initialSection ??');
  });

  it('🔴 폰은 시트가 «열린 채로» 받는다 — 접힌 알약을 보여 주면 아무 데도 안 간 것이다', () => {
    // 1350 이 고친 것과 같은 실수다. 시트가 생기면서 「연다」의 뜻이 «칸을 펼친다»에서
    // «시트를 띄운다»로 바뀌었으므로, 그 새 자리도 같이 잠근다.
    expect(HOME).toContain('useState(Boolean(editSection))');
    expect(HOME).toContain('hidden={startBarSheet}');
  });
});
