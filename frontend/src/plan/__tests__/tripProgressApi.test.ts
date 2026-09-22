// 서버가 준 진행 상태를 화면 모양으로 — S15P21E201-1325.
//
// 🔴 이 시험이 지키는 것은 **「다녀옴」과 「건너뜀」이 안 합쳐지는가**이다. 합치면 화면이
//    둘을 같은 모습으로 그리고, 나중에 「거기 갔었나?」를 기억으로만 풀어야 한다.
//    건너뛴 곳은 안 간 곳이다.
import { adaptProgress } from '@/plan/tripProgressApi';

describe('서버가 준 진행 상태', () => {
  it('상태와 몇 번째를 그대로 들고 온다', () => {
    const progress = adaptProgress({ status: 'RUNNING', currentStopIndex: 2, stops: [] });

    expect(progress.status).toBe('RUNNING');
    expect(progress.currentStopIndex).toBe(2);
  });

  it.each([undefined, null, '', 'WHATEVER'])('모르는 상태(%s)는 아직으로 떨어진다', (status) => {
    expect(adaptProgress({ status: status as string }).status).toBe('PLANNED');
  });

  it('🔴 다녀온 곳과 건너뛴 곳을 다르게 담는다', () => {
    const progress = adaptProgress({
      status: 'RUNNING',
      currentStopIndex: 2,
      stops: [
        { itemKey: 'a', index: 0, arrivedAt: '2026-10-03T09:41:00Z', arrivedHow: 'auto', skipped: false },
        { itemKey: 'b', index: 1, arrivedAt: null, arrivedHow: null, skipped: true },
        { itemKey: 'c', index: 2, arrivedAt: null, arrivedHow: null, skipped: false },
      ],
    });

    expect(progress.outcomes.a).toEqual({ kind: 'ARRIVED', at: '2026-10-03T09:41:00Z', how: 'auto' });
    expect(progress.outcomes.b).toEqual({ kind: 'SKIPPED', at: '' });
    // 아직 안 간 곳은 아예 안 적는다 — 「없음」이 곧 「아직」이다.
    expect(progress.outcomes.c).toBeUndefined();
  });

  /**
   * 🔴 모르면 「손으로 찍었다」로 떨어뜨리지 않는다. 기본 경로가 GPS 라서, 모르는 것을
   * 손으로 떨어뜨리면 「GPS 가 얼마나 맞히나」를 재는 값이 조용히 나빠진다.
   */
  it('🔴 어떻게 도착했는지 모르면 자동으로 본다', () => {
    const progress = adaptProgress({
      stops: [{ itemKey: 'a', arrivedAt: '2026-10-03T09:41:00Z' }],
    });

    expect(progress.outcomes.a).toEqual({ kind: 'ARRIVED', at: '2026-10-03T09:41:00Z', how: 'auto' });
  });

  it('손으로 찍은 것은 손으로 남긴다', () => {
    const progress = adaptProgress({
      stops: [{ itemKey: 'a', arrivedAt: '2026-10-03T09:41:00Z', arrivedHow: 'manual' }],
    });

    expect(progress.outcomes.a).toEqual({ kind: 'ARRIVED', at: '2026-10-03T09:41:00Z', how: 'manual' });
  });

  it('🔴 이름 없는 정차지는 버린다 — 열쇠가 없으면 어느 줄에 붙일지 알 수 없다', () => {
    const progress = adaptProgress({
      stops: [{ itemKey: '', arrivedAt: '2026-10-03T09:41:00Z' }, { arrivedAt: '2026-10-03T10:00:00Z' }],
    });

    expect(Object.keys(progress.outcomes)).toEqual([]);
  });

  it('빈 답에도 안 깨진다 — 서버가 아직 아무것도 안 남겼을 때다', () => {
    expect(adaptProgress({})).toEqual({ status: 'PLANNED', currentStopIndex: 0, outcomes: {} });
  });

  it('🔴 음수 자리는 0 으로 — 「몇 번째」가 음수면 목록에서 아무 줄도 못 가리킨다', () => {
    expect(adaptProgress({ currentStopIndex: -3 }).currentStopIndex).toBe(0);
  });
});
