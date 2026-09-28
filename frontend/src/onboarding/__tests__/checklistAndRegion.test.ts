// 체크리스트 ✓ 버그 둘 + 지역 검색 0건 안내 — S15P21E201-1804.
//
// 🔴 셋 다 «오류가 안 나는» 결함이다. 화면은 멀쩡히 그려지고 아무것도 실패하지 않는다.
declare const __dirname: string;

const { readFileSync } = require('fs');
const { join } = require('path');

const read = (...p: string[]) => readFileSync(join(__dirname, '..', '..', '..', ...p), 'utf8') as string;

describe('첫 여행 체크리스트 — 기기 표시가 계정을 가린다', () => {
  it('로그아웃·탈퇴 때 이 기기의 안내 표시를 비운다', () => {
    const auth = read('src', 'auth', 'AuthProvider.tsx');
    // signOut 과 deleteAccount 둘 다. 한쪽만 하면 나머지 길로 그대로 샌다.
    expect((auth.match(/clearFirstRunMarks\(\)/g) ?? []).length).toBe(2);
  });

  it('비우는 함수가 체크리스트를 실제로 지운다', () => {
    const firstRun = read('src', 'onboarding', 'firstRun.ts');
    expect(firstRun).toMatch(/export async function clearFirstRunMarks/);
    expect(firstRun).toMatch(/clearFirstRunMarks[\s\S]{0,400}CHECKLIST/);
  });
});

describe('첫 여행 체크리스트 — hasTrip 이 잘못된 값을 봤다', () => {
  it('home.hasTrips 를 넘긴다 — home.trip 은 «예정» 여행 하나라 지난 여행을 못 센다', () => {
    const home = read('app', '(tabs)', 'home.tsx');
    expect(home).toMatch(/<FirstTripChecklist[^>]*hasTrip=\{home\.hasTrips\}/);
    expect(home).not.toMatch(/hasTrip=\{Boolean\(home\.trip\)\}/);
  });
});

describe('지역 칸 — 찾은 것이 없을 때', () => {
  const picker = () => read('src', 'components', 'RegionPicker.tsx');

  it('0건이면 「적은 그대로 저장된다」고 알린다', () => {
    expect(picker()).toMatch(/searched && items\.length === 0/);
    expect(picker()).toMatch(/찾는 곳이 없어요/);
  });

  it('구·동 이름도 된다는 것을 같이 말한다 — 그것이 사용자가 막혔다고 느낀 지점이다', () => {
    expect(picker()).toMatch(/구·동 이름도 괜찮아요/);
  });

  it('🔴 아직 안 쳤을 때는 말하지 않는다 — 열자마자 「없어요」는 거짓이다', () => {
    // 두 글자 미만이면 searched 를 끈다.
    expect(picker()).toMatch(/text\.length < 2[\s\S]{0,120}setSearched\(false\)/);
  });

  it('고른 뒤에도 안내가 남지 않는다', () => {
    expect(picker()).toMatch(/setItems\(\[\]\);[\s\S]{0,160}setSearched\(false\)/);
  });
});
