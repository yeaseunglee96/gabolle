// 날짜 계산은 눈으로 검산이 안 된다. 「1박 2일」이 이틀인지 사흘인지, 월이 바뀔 때
// 어떻게 되는지가 여기서 정해진다.
import { RECOMMENDED_LODGING_AREAS } from '@/plan/origins';
import {
  EMPTY_START_BAR,
  START_BAR_PRESETS,
  addDays,
  askForPlanBlocker,
  canAskForPlan,
  dayCount,
  nightCount,
  startBarChips,
  startBarEditSection,
  startBarFromDraft,
  summarizeStartBar,
  toDateKey,
  type StartBarValue,
  placeEnglishOf,
  startBarPlaceName,
  startBarPlaceShortName,
} from '@/home/startBarValue';

// 예전엔 ko: boolean 을 넘겼다. 이제 번역 함수를 받는다 — 시험은 한국어·영어를 그대로 고른다.
const KO = (ko: string, _en: string) => ko;
const EN = (_ko: string, en: string) => en;

const value = (over: Partial<StartBarValue> = {}): StartBarValue => ({ ...EMPTY_START_BAR, ...over });

describe('날짜 세기', () => {
  it('같은 날은 1일 0박 — 당일치기다', () => {
    expect(dayCount('2026-09-20', '2026-09-20')).toBe(1);
    expect(nightCount('2026-09-20', '2026-09-20')).toBe(0);
  });

  it('하루 차이는 2일 1박이다', () => {
    expect(dayCount('2026-09-20', '2026-09-21')).toBe(2);
    expect(nightCount('2026-09-20', '2026-09-21')).toBe(1);
  });

  it('🔴 달을 넘어도 맞는다', () => {
    expect(dayCount('2026-09-30', '2026-10-02')).toBe(3);
    expect(addDays('2026-09-30', 2)).toBe('2026-10-02');
  });

  it('🔴 해를 넘어도 맞는다', () => {
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
  });

  it('🔴 윤년 2월을 안 건너뛴다', () => {
    expect(addDays('2028-02-28', 1)).toBe('2028-02-29');
  });

  it('거꾸로 된 날짜는 0 이다 — 음수 일수를 만들지 않는다', () => {
    expect(dayCount('2026-09-21', '2026-09-20')).toBe(0);
    expect(nightCount('2026-09-21', '2026-09-20')).toBe(0);
  });

  it('🔴 날짜 열쇠는 그 기기의 날짜다 — UTC 로 만들면 한국에서 하루 밀린다', () => {
    // 한국 시각으로 9월 20일 0시 30분. UTC 로 바꾸면 9월 19일이 된다.
    expect(toDateKey(new Date(2026, 8, 20, 0, 30))).toBe('2026-09-20');
  });
});

describe('한 줄 요약', () => {
  it('채워진 것만 적는다', () => {
    expect(summarizeStartBar(value({ origin: '부산역' }), KO)).toBe('부산역 · 성인 2');
  });

  it('🔴 아무것도 안 골랐으면 빈 문자열 — 「미정」으로 채우지 않는다', () => {
    expect(summarizeStartBar(value({ adults: 0 }), KO)).toBe('');
  });

  it('🔴 인원만으로는 요약을 만들지 않는다 — 인원에는 기본값(성인 2)이 들어 있다', () => {
    // 아무것도 안 고른 초기 상태. 전에는 여기서 「성인 2」가 나와
    // 알약에 안내 문구 대신 고른 적 없는 값이 찍혔다.
    expect(summarizeStartBar(value(), KO)).toBe('');
    expect(summarizeStartBar(value({ adults: 4, children: 2 }), KO)).toBe('');
  });

  it('출발지나 날짜가 하나라도 있으면 그때 인원도 같이 적는다', () => {
    expect(summarizeStartBar(value({ origin: '부산역' }), KO)).toContain('성인 2');
    expect(summarizeStartBar(value({ startDate: '2026-09-20' }), KO)).toContain('성인 2');
  });

  it('날짜와 박수와 인원을 한 줄로 붙인다', () => {
    expect(summarizeStartBar(value({ origin: '부산역', startDate: '2026-09-20', endDate: '2026-09-21', adults: 2 }), KO))
      .toBe('부산역 · 9.20(일) – 9.21(월) · 1박 · 성인 2');
  });

  it('당일치기는 「0박」이 아니라 「당일치기」다', () => {
    expect(summarizeStartBar(value({ startDate: '2026-09-20', endDate: '2026-09-20' }), KO))
      .toContain('당일치기');
  });

  it('어린이가 0명이면 그 칸을 안 적는다', () => {
    // 출발지를 같이 준다 — 인원만으로는 요약이 아예 안 만들어진다(위 시험 참고).
    expect(summarizeStartBar(value({ origin: '부산역', adults: 2, children: 0 }), KO)).not.toContain('어린이');
    expect(summarizeStartBar(value({ origin: '부산역', adults: 2, children: 1 }), KO)).toContain('어린이 1');
  });
});

describe('일정 물어보기를 누를 수 있나', () => {
  it('날짜와 인원이 있어야 한다 — 출발지는 선택이다 (S15P21E201-1376)', () => {
    expect(canAskForPlan(value())).toBe(false);
    expect(canAskForPlan(value({ origin: '부산역' }))).toBe(false);
    expect(canAskForPlan(value({ startDate: '2026-09-20' }))).toBe(true);
    expect(canAskForPlan(value({ origin: '부산역', startDate: '2026-09-20' }))).toBe(true);
    expect(canAskForPlan(value({ origin: '부산역', startDate: '2026-09-20', adults: 0 }))).toBe(false);
  });

  it('왜 못 누르는지를 말한다', () => {
    const tx = (ko: string) => ko;
    expect(askForPlanBlocker(value(), tx)).toBe('날짜를 골라 주세요');
    expect(askForPlanBlocker(value({ startDate: '2026-09-20', adults: 0 }), tx)).toBe('인원을 정해 주세요');
    expect(askForPlanBlocker(value({ startDate: '2026-09-20' }), tx)).toBeNull();
  });

  it('🔴 1박 이상이면 숙소가 있어야 한다 — 당일치기는 숙소 없이 된다 (S15P21E201-1584)', () => {
    const tx = (ko: string) => ko;
    const overnight = { startDate: '2026-09-20', endDate: '2026-09-21' };
    expect(canAskForPlan(value(overnight))).toBe(false);
    expect(askForPlanBlocker(value(overnight), tx)).toBe('숙소를 골라 주세요');
    expect(canAskForPlan(value({ startDate: '2026-09-20', endDate: '2026-09-20' }))).toBe(true);
    const haeundae = RECOMMENDED_LODGING_AREAS.find((area) => area.externalId === 'lodging-haeundae')!;
    const withArea = value({ ...overnight, lodging: '해운대', lodgingLat: haeundae.lat, lodgingLng: haeundae.lng });
    expect(canAskForPlan(withArea)).toBe(true);
    expect(askForPlanBlocker(withArea, tx)).toBeNull();
  });
});

describe('바로 시작 프리셋', () => {
  const wednesday = new Date(2026, 8, 16);

  it('「이번 주말 1박 2일」은 토요일에서 시작한다', () => {
    const preset = START_BAR_PRESETS.find((item) => item.id === 'weekend-1n');
    expect(preset?.apply(wednesday)).toEqual({ startDate: '2026-09-19', endDate: '2026-09-20' });
  });

  it('「아이와 당일치기」는 같은 날이고 어린이가 한 명이다', () => {
    const preset = START_BAR_PRESETS.find((item) => item.id === 'kid-day');
    expect(preset?.apply(wednesday)).toEqual({ startDate: '2026-09-19', endDate: '2026-09-19', adults: 2, children: 1 });
  });

  it('🔴 오늘이 토요일이면 그날이다 — 일주일 뒤로 밀지 않는다', () => {
    const saturday = new Date(2026, 8, 19);
    expect(START_BAR_PRESETS.find((item) => item.id === 'weekend-1n')?.apply(saturday)?.startDate).toBe('2026-09-19');
  });

  it('출발지 프리셋은 좌표까지 같이 넣는다 — 이름만 넣으면 지도가 못 찍는다', () => {
    const preset = START_BAR_PRESETS.find((item) => item.id === 'from-station');
    const applied = preset?.apply(wednesday);
    expect(applied?.origin).toBe('부산역');
    expect(typeof applied?.originLat).toBe('number');
    expect(typeof applied?.originLng).toBe('number');
  });
});

describe('홈에서 받은 정보 칩', () => {
  it('출발지 · 날짜 · 인원을 따로 준다 — 한 줄로 이어 붙이지 않는다', () => {
    expect(startBarChips(value({ origin: '부산역', startDate: '2026-09-20', endDate: '2026-09-21', adults: 2 }), KO))
      .toEqual(['부산역 출발', '9.20(일) – 9.21(월) · 1박', '성인 2']);
  });

  it('🔴 없는 칸은 칩을 안 만든다 — 빈 날짜 칩을 보여 주지 않는다', () => {
    expect(startBarChips(value({ origin: '부산역', adults: 2 }), KO)).toEqual(['부산역 출발', '성인 2']);
  });

  it('🔴 아무것도 안 골랐으면 칩이 없다 — 인원 기본값만으로 만들지 않는다', () => {
    expect(startBarChips(value(), KO)).toEqual([]);
  });

  it('당일치기는 「0박」이 아니다', () => {
    expect(startBarChips(value({ origin: '부산역', startDate: '2026-09-20', endDate: '2026-09-20' }), KO)[1])
      .toContain('당일치기');
  });

  it('어린이가 있으면 인원 칩 하나에 같이 적는다', () => {
    expect(startBarChips(value({ origin: '부산역', adults: 2, children: 1 }), KO).at(-1)).toBe('성인 2 · 어린이 1');
  });
});

describe('숙소 — design_handoff_home_lodging', () => {
  it('요약 줄에 출발지 다음, 날짜 앞에 들어간다', () => {
    expect(summarizeStartBar(value({ origin: '부산역', lodging: '해운대', startDate: '2026-09-20', endDate: '2026-09-21', adults: 2 }), KO))
      .toBe('부산역 · 해운대 · 9.20(일) – 9.21(월) · 1박 · 성인 2');
  });

  it('🔴 정하지 않았으면(빈 문자열) 요약에서 생략한다', () => {
    expect(summarizeStartBar(value({ origin: '부산역', adults: 2 }), KO)).not.toContain('undefined');
    expect(summarizeStartBar(value({ origin: '부산역' }), KO)).toBe('부산역 · 성인 2');
  });

  it('칩 줄에도 따로 들어간다', () => {
    expect(startBarChips(value({ origin: '부산역', lodging: '해운대', adults: 2 }), KO))
      .toEqual(['부산역 출발', '해운대 숙박', '성인 2']);
  });

  it('숙소만으로는 요약도 칩도 만들지 않는다 — 출발지·날짜와 같은 기준을 따른다', () => {
    expect(summarizeStartBar(value({ lodging: '해운대' }), KO)).toBe('');
    expect(startBarChips(value({ lodging: '해운대' }), KO)).toEqual([]);
  });

  it('주소의 ?edit= 값으로 숙소 칸도 열 수 있다', () => {
    expect(startBarEditSection('lodging')).toBe('lodging');
  });

  it('초안에서 시작 바로 옮길 때 숙소 좌표까지 같이 옮긴다', () => {
    const draft = value({ lodging: '해운대', lodgingLat: 35.1587, lodgingLng: 129.1604 });
    expect(startBarFromDraft(draft)).toMatchObject({ lodging: '해운대', lodgingLat: 35.1587, lodgingLng: 129.1604 });
  });
});

// 🔴 S15P21E201-1923 — 일본어·중국어 화면의 출발지·숙소 칩이 「Busan Station (부산역)」처럼 영어였다(실기기 2026-10-02).
describe('정해 둔 출발지·동네는 일본어·중국어 이름으로', () => {
  const busan = { name: '부산역', nameEn: 'Busan Station' };
  it('🔴 일본어·간체·번체는 그 언어 이름 (한글)', () => {
    expect(startBarPlaceName('부산역', placeEnglishOf(busan), 'ja')).toBe('釜山駅 (부산역)');
    expect(startBarPlaceName('해운대', placeEnglishOf({ name: '해운대', nameEn: 'Haeundae' }), 'zh-Hant')).toBe('海雲臺 (해운대)');
    expect(startBarPlaceName('김해공항', placeEnglishOf({ name: '김해공항', nameEn: 'Gimhae International Airport' }), 'zh-Hans')).toBe('金海国际机场 (김해공항)');
  });
  it('영어·한국어는 그대로, 검색해 고른 이름은 지어내지 않는다', () => {
    expect(startBarPlaceName('부산역', placeEnglishOf(busan), 'en')).toBe('Busan Station (부산역)');
    expect(startBarPlaceName('부산역', placeEnglishOf(busan), 'ko')).toBe('부산역');
    expect(startBarPlaceName('파스쿠치 광안리점', placeEnglishOf({ name: '파스쿠치 광안리점', nameEn: 'Pascucci' }), 'ja')).toBe('Pascucci (파스쿠치 광안리점)');
  });
  it('알약(짧은 이름)도 같은 이름', () => {
    expect(startBarPlaceShortName('부산역', placeEnglishOf(busan), 'ja')).toBe('釜山駅');
  });
});
