import { assistantPrefillPatch } from '../assistantPrefill';
import { EMPTY_PLAN, type PlanDraft } from '../PlanProvider';
import { validateTripBasics } from '../tripBasics';

const TODAY = '2026-09-18';
const draft = (over: Partial<PlanDraft> = {}): PlanDraft => ({ ...EMPTY_PLAN, ...over });

describe('비서가 보낸 일수·인원을 여행 초안에 채운다', () => {
  it('🔴 days=2 는 내일부터 1박 2일이다', () => {
    // "부산에서 이틀 일정 짜줘" → 서버가 /plan?days=2 를 보낸다.
    expect(assistantPrefillPatch({ days: '2' }, draft(), TODAY)).toEqual({
      startDate: '2026-09-19', endDate: '2026-09-20',
    });
  });

  it('🔴 days=1 은 당일치기 — 시작일과 종료일이 같다', () => {
    expect(assistantPrefillPatch({ days: '1' }, draft(), TODAY)).toEqual({
      startDate: '2026-09-19', endDate: '2026-09-19',
    });
  });

  it('🔴 people 은 전원 성인으로 넣는다 — assistant/intent.ts 와 같은 규칙', () => {
    expect(assistantPrefillPatch({ people: '4' }, draft(), TODAY)).toEqual({
      travelers: 4, adults: 4, children: 0,
    });
  });

  it('둘 다 오면 둘 다 채운다', () => {
    expect(assistantPrefillPatch({ days: '3', people: '2' }, draft(), TODAY)).toEqual({
      startDate: '2026-09-19', endDate: '2026-09-21', travelers: 2, adults: 2, children: 0,
    });
  });

  it('달이 넘어가도 날짜가 맞는다', () => {
    expect(assistantPrefillPatch({ days: '3' }, draft(), '2026-09-29')).toEqual({
      startDate: '2026-09-30', endDate: '2026-10-02',
    });
  });

  describe('🔴 채운 값이 화면 검증을 통과해야 한다', () => {
    // 여기가 이 기능의 핵심이다 — 서버는 days 를 30 까지 허용하는데 앱은 7박까지다.
    // 채워 넣은 값이 곧바로 "최대 7박" 에 걸리면 채우지 않느니만 못하다.
    it.each(['1', '2', '5', '8'])('days=%s 를 채운 초안은 날짜 오류가 없다', (days) => {
      const filled = draft(assistantPrefillPatch({ days }, draft(), TODAY));
      const errors = validateTripBasics(filled, TODAY);
      expect(errors.startDate).toBeUndefined();
      expect(errors.endDate).toBeUndefined();
    });

    it('🔴 9일 이상은 아예 안 채운다 — 30일이라 말한 사람에게 8일을 슬쩍 주지 않는다', () => {
      expect(assistantPrefillPatch({ days: '9' }, draft(), TODAY)).toEqual({});
      expect(assistantPrefillPatch({ days: '30' }, draft(), TODAY)).toEqual({});
    });

    it('21명 이상은 안 채운다 — 서버가 막는 범위와 같다', () => {
      expect(assistantPrefillPatch({ people: '21' }, draft(), TODAY)).toEqual({});
    });
  });

  describe('🔴 말하지 않은 것은 덮지 않고, 말한 것은 덮는다 — S15P21E201-1826', () => {
    it('앞으로의 날짜를 골라 뒀으면 그 출발일에서 일수만 센다', () => {
      const chosen = draft({ startDate: '2026-10-01', endDate: '2026-10-03' });
      expect(assistantPrefillPatch({ days: '2' }, chosen, TODAY)).toEqual({ startDate: '2026-10-01', endDate: '2026-10-02' });
    });

    it('🔴 지난·오늘 당일치기 날짜는 AI 가 날짜를 줬을 때 내일부터 다시 잡는다', () => {
      const stale = draft({ startDate: TODAY, endDate: TODAY });
      expect(assistantPrefillPatch({ days: '2' }, stale, TODAY)).toEqual({ startDate: '2026-09-19', endDate: '2026-09-20' });
    });

    it('🔴 AI 가 날짜를 안 주면 지난 날짜라도 건드리지 않는다', () => {
      const stale = draft({ startDate: '2026-09-10', endDate: '2026-09-10' });
      expect(assistantPrefillPatch({ people: '2' }, stale, TODAY)).toEqual({ travelers: 2, adults: 2, children: 0 });
    });

    it('🔴 말한 인원은 예전 인원을 덮는다 — 「2명」이라 했는데 1명이 남던 것', () => {
      const party = draft({ travelers: 3, adults: 2, children: 1 });
      expect(assistantPrefillPatch({ people: '2' }, party, TODAY)).toEqual({ travelers: 2, adults: 2, children: 0 });
    });

    it('🔴 광안리·맛집 — 지역과 취향을 고른다', () => {
      const old = draft({ travelAreas: ['NAMPO'] });
      expect(assistantPrefillPatch({ areas: 'GWANGALLI,haeundae,GWANGALLI', categories: 'FOOD' }, old, TODAY)).toEqual({
        travelAreas: ['GWANGALLI', 'HAEUNDAE'],
        preferences: ['FOOD'],
        preferenceAnswerStatus: { ...old.preferenceAnswerStatus, category: 'SELECTED' },
      });
    });

    it('모르는 지역·취향 코드는 버린다', () => {
      expect(assistantPrefillPatch({ areas: 'SEOUL', categories: 'SHOPPING' }, draft(), TODAY)).toEqual({});
    });

    it('출발일이 오면 그 날부터 일수를 센다', () => {
      expect(assistantPrefillPatch({ start: '2026-10-03', days: '3' }, draft(), TODAY)).toEqual({ startDate: '2026-10-03', endDate: '2026-10-05' });
    });

    it('지난 출발일은 무시하고 일수만 쓴다', () => {
      expect(assistantPrefillPatch({ start: '2026-09-01', days: '1' }, draft(), TODAY)).toEqual({ startDate: '2026-09-19', endDate: '2026-09-19' });
    });

    it('말하지 않은 칸은 조각에 없다', () => {
      const patch = assistantPrefillPatch({ areas: 'GWANGALLI' }, draft({ startDate: TODAY, endDate: TODAY }), TODAY);
      expect(Object.keys(patch)).toEqual(['travelAreas']);
    });
  });

  describe('이상한 값은 조용히 버린다 — 응답 전체를 실패시키지 않는다', () => {
    it.each([
      ['없음', {}],
      ['빈 문자열', { days: '', people: '' }],
      ['0', { days: '0', people: '0' }],
      ['음수', { days: '-2' }],
      ['소수', { days: '1.5' }],
      ['글자', { days: '이틀', people: '네명' }],
      ['공백만', { days: '   ' }],
    ])('%s → 빈 조각', (_label, params) => {
      expect(assistantPrefillPatch(params, draft(), TODAY)).toEqual({});
    });

    it('expo-router 가 배열로 줘도 첫 값을 본다', () => {
      expect(assistantPrefillPatch({ days: ['2', '9'] }, draft(), TODAY)).toEqual({
        startDate: '2026-09-19', endDate: '2026-09-20',
      });
    });
  });
});
