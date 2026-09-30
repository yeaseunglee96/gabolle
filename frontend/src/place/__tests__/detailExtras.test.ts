import {
  admissionFeeRow, amenitiesRow, bestTimeRow, extraRows, foreignMenuRow, formatDistance, formatWon, homepageUrl,
  menuLines, nearbyLandmarkRow, pickBestTime, walkDifficulty, walkDifficultyRow,
} from '../detailExtras';

const ko = (k: string) => k;
const en = (_k: string, e: string) => e;
const f = (featureType: string, value: unknown, evidenceStatus = 'VERIFIED') => ({ featureType, value, evidenceStatus });

describe('대표 메뉴', () => {
  const items = f('MENU_ITEMS', { items: [
    { nameKo: '물회', nameEn: null, priceWon: 15000, ingredientsKo: '회, 야채', ingredientsEn: null, signature: false },
    { nameKo: '낙곱새', nameEn: 'Nakgopsae', priceWon: 12000, ingredientsKo: '야채, 낙지, 새우', ingredientsEn: 'vegetables, octopus, shrimp', signature: true },
    { nameKo: '', nameEn: null, priceWon: 1, signature: false },
  ] });

  it('한국어: 한국어 이름·재료·원, 대표 메뉴가 앞, 이름 없는 줄은 뺀다', () => {
    const lines = menuLines([items], 'ko', ko);
    expect(lines.map((l) => l.name)).toEqual(['낙곱새', '물회']);
    expect(lines[0]).toMatchObject({ price: '12,000원', ingredients: '재료: 야채, 낙지, 새우', subName: null });
  });

  it('영어: 영어 이름(없으면 한국어), 한국어 이름은 작게, 영어 재료가 없으면 재료 줄 없음', () => {
    const lines = menuLines([items], 'en', en);
    expect(lines[0]).toMatchObject({ name: 'Nakgopsae', subName: '낙곱새', price: '₩12,000', ingredients: 'Ingredients: vegetables, octopus, shrimp' });
    expect(lines[1]).toMatchObject({ name: '물회', subName: null, ingredients: null });
  });

  it('없으면 빈 목록', () => {
    expect(menuLines([], 'ko', ko)).toEqual([]);
    expect(menuLines(undefined, 'ko', ko)).toEqual([]);
    expect(menuLines([f('MENU_ITEMS', { items: 'x' })], 'ko', ko)).toEqual([]);
  });

  it('🔴 재료에서 알레르기·비건을 끌어내지 않는다 — 결과에 그런 칸이 없다', () => {
    const line = menuLines([items], 'ko', ko)[0] as Record<string, unknown>;
    expect(Object.keys(line).sort()).toEqual(['ingredients', 'name', 'price', 'signature', 'subName']);
  });

  it('원 표기', () => {
    expect(formatWon(1234567, 'ko')).toBe('1,234,567원');
    expect(formatWon(9000, 'ja')).toBe('₩9,000');
  });
});

describe('정보 줄', () => {
  it('외국어 메뉴판', () => {
    expect(foreignMenuRow([f('FOREIGN_MENU', { available: true })], ko)?.value).toBe('있어요');
    expect(foreignMenuRow([f('FOREIGN_MENU', { available: false })], en)?.value).toBe('Not available');
    expect(foreignMenuRow([f('FOREIGN_MENU', { available: true }, 'ESTIMATED')], ko)?.value).toBe('있어요 (추정)');
    expect(foreignMenuRow([f('FOREIGN_MENU', {})], ko)).toBeNull();
    expect(foreignMenuRow([f('FOREIGN_MENU', { available: true }, 'UNKNOWN')], ko)).toBeNull();
  });

  it('편의시설 — null 은 빼고, 다 null 이면 줄 없음', () => {
    expect(amenitiesRow([f('AMENITIES', { wifi: true, parking: false, restroom: null, reservation: null, homepage: null })], ko)?.value).toBe('와이파이 있음 · 주차 없음');
    expect(amenitiesRow([f('AMENITIES', { wifi: null, parking: null, restroom: null, reservation: null, homepage: null })], ko)).toBeNull();
    expect(homepageUrl([f('AMENITIES', { homepage: 'https://a.kr' })])).toBe('https://a.kr');
    expect(homepageUrl([f('AMENITIES', { homepage: 'javascript:x' })])).toBeNull();
  });

  it('입장료', () => {
    expect(admissionFeeRow([f('ADMISSION_FEE', { raw: '어른 3,000원' })], ko)?.value).toBe('어른 3,000원');
    expect(admissionFeeRow([f('ADMISSION_FEE', { raw: ' ' })], ko)).toBeNull();
  });

  it('가까운 명소', () => {
    expect(nearbyLandmarkRow([f('NEARBY_LANDMARK', { name: '용두산공원', distanceM: 216 })], ko)?.value).toBe('용두산공원 · 216m');
    expect(formatDistance(1234)).toBe('1.2km');
    expect(nearbyLandmarkRow([f('NEARBY_LANDMARK', { distanceM: 3 })], ko)).toBeNull();
  });

  it('좋은 때 — 가장 많은 쪽, 낮=밤 동점이나 「언제든」이 1등(공동 포함)이면 언제든', () => {
    expect(pickBestTime({ day: 2, night: 9, any: 3 })).toEqual({ pick: 'night', total: 14 });
    expect(pickBestTime({ day: 5, night: 1, any: 0 })?.pick).toBe('day');
    expect(pickBestTime({ day: 4, night: 4, any: 1 })?.pick).toBe('any');
    expect(pickBestTime({ day: 1, night: 5, any: 5 })?.pick).toBe('any');
    expect(pickBestTime({ day: 0, night: 0, any: 0 })).toBeNull();
    const row = bestTimeRow([f('BEST_TIME', { day: 2, night: 9, any: 3 })], ko);
    expect(row).toMatchObject({ value: '밤', caption: '현지인 설문 14명' });
  });

  it('걷기 난이도 — 5 이하 쉬움 · 10 이하 보통 · 그 위 힘듦, 자연·산책만', () => {
    expect([5, 5.1, 10, 10.1].map(walkDifficulty)).toEqual(['easy', 'moderate', 'moderate', 'hard']);
    expect(walkDifficultyRow('NATURE_WALK', [f('SLOPE_PERCENT', { score: 12.8 })], ko)?.value).toBe('힘듦');
    expect(walkDifficultyRow('FOOD', [f('SLOPE_PERCENT', { score: 12.8 })], ko)).toBeNull();
    expect(walkDifficultyRow('NATURE_WALK', [], ko)).toBeNull();
  });

  it('아무 것도 없으면 줄이 없다(백엔드 !1930 전 서버)', () => {
    expect(extraRows('FOOD', [f('OPENING_HOURS', { raw: 'x' })], ko)).toEqual([]);
  });
});
