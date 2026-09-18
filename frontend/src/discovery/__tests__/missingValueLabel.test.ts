import { formatFeatureSlot, missingValueLabel, type FeatureSlot } from '../places';

// S15P21E201-1015 — 값이 없는 이유가 둘인데 한 문구로 뭉개고 있었다.
// 🔴 「안 알아봤다」가 「없다」처럼 읽히면, 알레르기에서 사람이 다칠 수 있었던 것과 같은
// 종류의 거짓말이 된다(S15P21E201-996). 셋이 화면에서 갈리는지 여기서 잠근다.
const tx = (ko: string) => ko;
const slot = (evidenceStatus: FeatureSlot['evidenceStatus'], value: unknown = null): FeatureSlot => ({ value, evidenceStatus });

describe('값이 없는 이유를 구분한다', () => {
  it('아직 안 알아본 것과 알아봤지만 못 정한 것이 다른 문구다', () => {
    const notCollected = missingValueLabel(slot('NOT_COLLECTED'), tx);
    const unknown = missingValueLabel(slot('UNKNOWN'), tx);
    expect(notCollected).not.toBe(unknown);
  });

  it('🔴 어느 쪽도 「없음」이라고 말하지 않는다', () => {
    for (const status of ['NOT_COLLECTED', 'UNKNOWN'] as const) {
      expect(missingValueLabel(slot(status), tx)).not.toContain('없음');
    }
  });

  it('값이 있으면 값을 그대로 보여준다', () => {
    expect(formatFeatureSlot(slot('VERIFIED', '09:00–21:00'), tx)).toBe('09:00–21:00');
  });

  it('어림값은 어림이라고 붙인다 — 확정값과 헷갈리면 안 된다', () => {
    expect(formatFeatureSlot(slot('ESTIMATED', '보통'), tx)).toBe('보통 (추정)');
  });

  it('칸 자체가 안 온 것(undefined)은 줄을 만들지 않는다', () => {
    expect(formatFeatureSlot(undefined, tx)).toBeNull();
  });
});
