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

  it('🔴 문항 화면이 열 칸을 함께 보낸다 — 주소만 바꾸면 예전과 같다', () => {
    expect(QUESTIONS).toContain("params: { edit: 'dates' }");
    // 그냥 밀어 보내던 옛 꼴이 남아 있으면 안 된다.
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
    expect(BAR).toContain('useState<Section>(initialSection)');
  });
});
