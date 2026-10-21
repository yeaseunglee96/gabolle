// 늘 여는 곳의 영업시간 — 서버 status ALWAYS_OPEN 을 번역해서 보인다 (S15P21E201-2071).
// 전에는 원문 「상시 개방」(raw.hoursValue)이 영어 화면에도 그대로 나왔다.
import { formatFeatureSlot } from '../places';

const en = (_ko: string, english: string) => english;
const ko = (korean: string) => korean;
const slot = {
  featureType: 'OPENING_HOURS', evidenceStatus: 'ESTIMATED',
  value: { raw: { restField: 'restdate', restValue: '연중무휴', hoursField: 'usetime', hoursValue: '상시 개방' }, status: 'ALWAYS_OPEN' },
} as never;

describe('상시 개방', () => {
  it('영어 화면은 영어로', () => {
    expect(formatFeatureSlot(slot, en)).toBe('Open at all times (est.)');
  });
  it('한국어 화면은 그대로', () => {
    expect(formatFeatureSlot(slot, ko)).toBe('상시 개방 (추정)');
  });
});
