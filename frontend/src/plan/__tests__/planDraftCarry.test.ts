// 손님으로 채운 여행 초안을 로그인 계정이 이어받는가 — S15P21E201-1541.
import { hasPlanInput, parseStoredDraft } from '../planDraftCarry';

describe('저장된 초안 읽기', () => {
  it('판이 맞으면 초안을 준다', () => {
    expect(parseStoredDraft<{ startDate: string }>(JSON.stringify({ version: 1, draft: { startDate: '2026-09-28' } }), 1)).toEqual({ startDate: '2026-09-28' });
  });

  it('판이 다르거나 깨졌거나 없으면 null', () => {
    expect(parseStoredDraft(JSON.stringify({ version: 0, draft: { startDate: 'x' } }), 1)).toBeNull();
    expect(parseStoredDraft('{깨짐', 1)).toBeNull();
    expect(parseStoredDraft(null, 1)).toBeNull();
  });
});

describe('사람이 무언가 채운 초안인가', () => {
  it('🔴 날짜·출발지·숙소·여행 범위 중 하나라도 있으면 이어받는다', () => {
    expect(hasPlanInput({ startDate: '2026-09-28' })).toBe(true);
    expect(hasPlanInput({ origin: '부산역' })).toBe(true);
    expect(hasPlanInput({ lodging: '해운대 어느 호텔' })).toBe(true);
    expect(hasPlanInput({ travelAreas: ['HAEUNDAE'] })).toBe(true);
  });

  it('🔴 빈 초안은 이어받지 않는다 — 계정에 저장해 둔 초안을 빈 것으로 덮으면 안 된다', () => {
    expect(hasPlanInput({ startDate: '', origin: '  ', lodging: '', travelAreas: [] })).toBe(false);
    expect(hasPlanInput(null)).toBe(false);
  });
});
