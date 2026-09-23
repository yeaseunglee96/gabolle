// 추천 실패가 «어느 조건이» 막았는지 말하는가 — S15P21E201-1514.
//
// 🔴 이 시험이 지키는 것 셋.
//    ① 「확인 못 함」과 「위반」이 다른 말이다 — 앞쪽을 「없다」로 적으면 사용자는
//       부산에 자기가 먹을 것이 없다고 읽는다
//    ② 사전에 없는 것은 지어내지 않는다 — 모르는 갈래는 숨기고, 모르는 값은 원문 코드
//       대신 갈래 이름으로 적는다
//    ③ 적을 것이 없으면 null — 부르는 쪽이 예전 문구로 떨어진다
import { setCurrentLanguage } from '@/i18n/languages';
import { describeBlockedBy, readBlockedBy, type BlockedBy } from '@/plan/blockedByMessage';

const item = (over: Partial<BlockedBy>): BlockedBy => ({
  constraintType: 'DIET', constraintKey: 'VEGAN', reason: 'UNVERIFIED', code: 'DIET_SUPPORT_UNVERIFIED', blockedCandidates: 120, ...over,
});

afterEach(() => setCurrentLanguage('ko'));

describe('막은 조건을 사람 말로', () => {
  it('식단이 막았으면 식단이라고, 막은 값까지 짚는다', () => {
    const message = describeBlockedBy([item({})]) ?? '';
    expect(message).toContain('식단 조건에 맞는지 확인된 곳이 없어');
    expect(message).toContain('걸린 조건: 비건');
    // 🔴 「안」이 줄 끝에 홀로 남지 않게 둘째 문장은 제 줄에서 시작한다
    expect(message.split('\n')[1]).toBe('안 맞는다는 뜻이 아니라, 아직 확인한 자료가 없어요.');
    // 🔴 화면이 바로 아래 「조건을 조금 넓혀서 다시 해 볼까요?」를 그리므로 같은 할 일을 또 적지 않는다
    expect(message).not.toContain('다시 만들어 주세요');
    // 예전처럼 셋을 한꺼번에 늘어놓지 않는다
    expect(message).not.toContain('알레르기 · 식단 · 이동');
  });

  it('🔴 「확인 못 함」은 「없다」가 아니다 — 두 이유가 다른 문장이다', () => {
    const unverified = describeBlockedBy([item({ reason: 'UNVERIFIED' })]) ?? '';
    const violated = describeBlockedBy([item({ reason: 'VIOLATED' })]) ?? '';
    expect(unverified).toContain('안 맞는다는 뜻이 아니라');
    expect(violated).not.toContain('안 맞는다는 뜻이 아니라');
    expect(violated).toContain('지키는 곳을 찾지 못해');
    expect(unverified).not.toEqual(violated);
  });

  it('🔴 모르는 이유가 오면 「확인 못 함」 쪽으로 적는다 — 「없다」고 잘못 말하는 쪽이 더 큰 거짓이다', () => {
    const message = describeBlockedBy([item({ reason: 'SOMETHING_NEW' })]) ?? '';
    expect(message).toContain('안 맞는다는 뜻이 아니라');
  });

  it('첫 문장은 가장 많이 막은 조건(서버가 앞에 준 것)으로 정한다', () => {
    const message = describeBlockedBy([
      item({ constraintType: 'MOBILITY', constraintKey: 'WHEELCHAIR', blockedCandidates: 300 }),
      item({ constraintType: 'DIET', constraintKey: 'HALAL', blockedCandidates: 40 }),
    ]) ?? '';
    expect(message.split('\n')[0]).toContain('이동 조건');
    expect(message).toContain('걸린 조건: 휠체어 · 할랄');
  });

  it('🔴 모르는 갈래는 통째로 숨긴다 — 지어내지 않는다', () => {
    expect(describeBlockedBy([item({ constraintType: null })])).toBeNull();
    expect(describeBlockedBy([item({ constraintType: 'WEATHER' })])).toBeNull();
    const mixed = describeBlockedBy([item({ constraintType: 'WEATHER', constraintKey: 'RAIN' }), item({})]) ?? '';
    expect(mixed).not.toContain('RAIN');
    expect(mixed).not.toContain('WEATHER');
    expect(mixed).toContain('걸린 조건: 비건');
  });

  it('🔴 모르는 값은 원문 코드를 안 내보내고 갈래 이름으로 적는다', () => {
    const message = describeBlockedBy([item({ constraintKey: 'KETO' })]) ?? '';
    expect(message).not.toContain('KETO');
    expect(message).toContain('걸린 조건: 식단');
  });

  it('갈래 전체가 막혔으면(값 없음) 갈래 이름으로 적는다', () => {
    expect(describeBlockedBy([item({ constraintKey: null })]) ?? '').toContain('걸린 조건: 식단');
  });

  it('같은 말은 한 번만 적는다', () => {
    const message = describeBlockedBy([item({ constraintKey: null }), item({ constraintKey: 'KETO' })]) ?? '';
    expect(message).toContain('걸린 조건: 식단');
    expect(message).not.toContain('식단 · 식단');
  });

  it('🔴 적을 것이 없으면 null — 빈 목록 · 없음 · 모양이 다른 값', () => {
    expect(describeBlockedBy([])).toBeNull();
    expect(describeBlockedBy(null)).toBeNull();
    expect(describeBlockedBy(undefined)).toBeNull();
  });

  it('🔴 일본어를 고른 사람에게 영어로 떨어지지 않는다 — 문장에 값을 안 끼워 번역표에서 찾힌다', () => {
    setCurrentLanguage('ja');
    const message = describeBlockedBy([item({})]) ?? '';
    expect(message).toContain('食事制限に合うと確認できた場所がなく');
    expect(message).toContain('該当した条件：');
    expect(message).not.toContain('No place could be confirmed');
  });

  it('영어를 고른 사람에게는 영어로', () => {
    setCurrentLanguage('en');
    const message = describeBlockedBy([item({})]) ?? '';
    expect(message).toContain("No place could be confirmed to fit your diet");
    expect(message).toContain('Blocked by: Vegan');
  });
});

describe('서버 값을 모양만 확인해 옮긴다', () => {
  it('배열이 아니면 빈 목록', () => {
    expect(readBlockedBy(undefined)).toEqual([]);
    expect(readBlockedBy(null)).toEqual([]);
    expect(readBlockedBy({ constraintType: 'DIET' })).toEqual([]);
  });

  it('빈 문자열 · 다른 자료형은 null 로, 모르는 줄은 버린다', () => {
    expect(readBlockedBy([null, 3, { constraintType: '', constraintKey: 7, reason: 'VIOLATED', blockedCandidates: '9' }])).toEqual([
      { constraintType: null, constraintKey: null, reason: 'VIOLATED', code: null, blockedCandidates: 0 },
    ]);
  });
});
