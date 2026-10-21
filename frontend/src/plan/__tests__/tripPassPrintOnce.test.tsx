// 여행표는 한 번만 출력된다 — S15P21E201-1577.
//
// 🔴 이 시험이 지키는 것은 **다 나온 티켓이 처음부터 다시 나오지 않는가**다.
//    일정이 완성된 순간 티켓이 나타나는데, 그때는 일정을 아직 받아 오기 전이라 코드가 빈 값이다.
//    곧 일정이 오면 코드가 「GB-…」로 채워지고, 전에는 그 변화가 출력을 처음부터 다시 돌렸다 —
//    사람 눈에는 영수증이 두 번 나왔다.
import { act, render } from '@testing-library/react-native';

import { PRINT_FEED, PRINT_MS, TripPass } from '@/plan/TripPass';
import type { TripPassData, TripPassDetail } from '@/plan/tripPassData';

const tx = (ko: string) => ko;
const HINT = '눌러서 여행표 상세 보기 ↻';

/** 일정을 받아 오기 전 — 초안에서 만든 티켓. 코드도 방문지도 없다. */
const draftPass: TripPassData = {
  code: '',
  stampDate: '03 · OCT · 2026',
  fromLabel: '부산역',
  toLabel: '부산',
  startTime: null,
  endTime: null,
  dateRange: '10.3(금) – 10.5(일)',
  mode: '대중교통',
  owner: '진미리',
  fields: [],
  url: null,
  validText: '이 승차권은 10.3(금) – 10.5(일) 여행에만 쓸 수 있어요', firstStop: null, lastStop: null, conditions: []
};

/** 일정을 받아 온 뒤 — 코드와 방문지가 채워진다. */
const loadedPass: TripPassData = {
  ...draftPass,
  code: 'GB-ABC123',
  startTime: '09:30',
  endTime: '17:30',
  fields: [{ key: '방문지', value: '9곳' }],
  url: 'https://example.test/trips/1',
};

const details: TripPassDetail[] = [{ key: '출발지', value: '부산역' }];

describe('여행표는 한 번만 출력된다', () => {
  beforeEach(() => { jest.useFakeTimers(); });
  afterEach(() => { jest.useRealTimers(); });

  it('🔴 다 나온 뒤 일정이 채워져도 처음부터 다시 나오지 않는다', () => {
    const view = render(<TripPass data={draftPass} details={details} tx={tx} />);
    act(() => { jest.runAllTimers(); });
    // 다 나왔다 — 뒤집기 힌트는 출력이 끝나야 나온다.
    expect(view.getByText(HINT)).toBeTruthy();

    view.rerender(<TripPass data={loadedPass} details={details} tx={tx} />);

    // 다시 출력을 시작했다면 힌트가 사라진다(종이가 다시 프린터 안으로 들어간다).
    expect(view.getByText(HINT)).toBeTruthy();
    // 새 값은 그 자리에서 찍힌다 — 한 번 나온 종이에 코드가 채워진다.
    expect(view.getAllByText('GB-ABC123').length).toBeGreaterThan(0);
  });

  it('🔴 값이 다 오기 전(ready=false)에는 종이를 내보내지 않고, 오면 그때 한 번 나온다', () => {
    const view = render(<TripPass data={draftPass} details={details} ready={false} tx={tx} />);
    act(() => { jest.runAllTimers(); });
    expect(view.queryByText(HINT)).toBeNull();

    view.rerender(<TripPass data={loadedPass} details={details} ready tx={tx} />);
    act(() => { jest.runAllTimers(); });
    expect(view.getByText(HINT)).toBeTruthy();
  });
});

// ── 감열 프린터처럼 끊겨 나온다 ───────────────────────────────────────────────
//
// 한 번에 매끄럽게 미끄러져 나오면 「레이저 프린터 같다」고 했다. 조금 나오고 멈추고를 되풀이한다.
// 🔴 이 시험은 «보기 좋은가»는 못 잡는다 — 그건 화면으로 본다. 잡는 것은 셋이다:
//    끝까지 나오는가 · 전체 시간이 늘어 사람을 기다리게 하지 않는가 · 정말 끊기는가.
describe('감열 프린터식 출력', () => {
  const moves = PRINT_FEED.map((step) => step.move);
  const total = PRINT_FEED.reduce((sum, step) => sum + step.ms + step.pauseMs, 0);

  it('끝까지 나온다 — 조각을 다 더하면 종이 한 장이다', () => {
    expect(moves.reduce((a, b) => a + b, 0)).toBeCloseTo(1, 6);
  });

  it('🔴 전체 시간은 예전 한 번에 나오던 시간과 같다 — 길어져서 기다리게 만들지 않는다', () => {
    expect(total).toBe(PRINT_MS);
  });

  it('여러 번 멈춘다 — 조각 길이와 멈춤이 제각각이다', () => {
    const pauses = PRINT_FEED.slice(0, -1).map((step) => step.pauseMs);
    expect(PRINT_FEED.length).toBeGreaterThanOrEqual(5);
    expect(pauses.every((ms) => ms > 0)).toBe(true);
    expect(new Set(moves).size).toBeGreaterThan(moves.length / 2);
    expect(new Set(pauses).size).toBeGreaterThan(pauses.length / 2);
  });
});
