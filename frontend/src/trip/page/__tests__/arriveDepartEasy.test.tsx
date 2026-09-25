// 여행 중 도착·출발 기록 쉽게 — S15P21E201-1690 (운영 기록 11건 중 출발 0건, 조율 세션·사용자 결정).
//
// 🔴 이 시험이 지키는 것:
//    1. 자동 출발 — 머무는 곳 반경 밖에 도착과 같은 시간(2분) 있어야 출발. 잠깐 나갔다 오거나 위치가 튀면 아니다.
//    2. 머무는 곳 — 도착은 적혔고 출발은 아직인 곳(서버는 둘 다 있어야 「다녀옴」).
//    3. 시각 고치기 — 지금 · 5분 전 · 15분 전 · 직접(±5분). 미래는 안 되고, 출발은 도착보다 이를 수 없다(서버 400).
//    4. 지금 카드 — 「출발」 모드에서 도착 단추가 늘 보이고, 머무는 곳이 있으면 「출발 찍기」와 「시각 고치기」.
//    5. 서버로 가는 것은 시각 둘뿐 — 좌표를 싣지 않는다.
//    6. 예전 일정 화면의 결함 셋 — 「출발 찍기」가 뜰 수 없었고, 예측 도착을 실제 도착으로 실었고, 도착을 두 번 찍으면 덮어썼다.
import { fireEvent, render, screen } from '@testing-library/react-native';

import { apiRequest } from '@/api/client';
import { NowCard } from '@/plan/NowCard';
import { recordItineraryItemActual } from '@/plan/itinerary';
import type { StopOutcome } from '@/plan/tripProgress';
import { ActualTimeSheet } from '@/trip/page/ActualTimeSheet';
import { clampActualTime, minutesBefore, stayingStopId, stepTime } from '@/trip/page/actualTime';
import { ARRIVE_RADIUS_M, DEPART_DWELL_MS, stepAway, type Away } from '@/trip/page/autoArrival';

jest.mock('@/api/client', () => ({ ...jest.requireActual('@/api/client'), apiRequest: jest.fn() }));

// 소스를 읽는 시험은 이 저장소의 방식대로 require 로 부른다 — 앱 tsconfig 에 node 타입이 없다.
declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');
const source = (rel: string) => readFileSync(join(__dirname, '..', '..', '..', '..', rel), 'utf8') as string;

const tx = (ko: string) => ko;
const MIN = 60_000;
const STAY = { id: 's1', latitude: 35.1587, longitude: 129.1604 };
// 위도 0.001도 ≈ 111m — 반경(50m) 밖.
const fixAt = (at: number, dLat = 0, accuracy: number | null = 10) => ({ latitude: STAY.latitude + dLat, longitude: STAY.longitude, accuracy, at });

describe('1. 자동 출발', () => {
  it('🔴 반경 밖에 2분 있어야 출발 — 나간 순간에는 아니다', () => {
    let away: Away = null;
    let step = stepAway(away, fixAt(0, 0.001), STAY);
    expect(step.depart).toBe(false);
    away = step.away;
    step = stepAway(away, fixAt(DEPART_DWELL_MS - 1, 0.001), STAY);
    expect(step.depart).toBe(false);
    step = stepAway(step.away, fixAt(DEPART_DWELL_MS, 0.001), STAY);
    expect(step.depart).toBe(true);
  });

  it('🔴 잠깐 나갔다 돌아오면 처음부터 센다', () => {
    let step = stepAway(null, fixAt(0, 0.001), STAY);
    step = stepAway(step.away, fixAt(MIN, 0), STAY);
    expect(step.away).toBeNull();
    step = stepAway(step.away, fixAt(90_000, 0.001), STAY);
    step = stepAway(step.away, fixAt(90_000 + DEPART_DWELL_MS - 1, 0.001), STAY);
    expect(step.depart).toBe(false);
  });

  it('🔴 정확도가 나쁜 위치(튀는 점)로는 나갔다고 보지 않는다', () => {
    const step = stepAway(null, fixAt(0, 0.01, 500), STAY);
    expect(step.away).toBeNull();
    expect(step.depart).toBe(false);
  });

  it('반경은 도착과 같다', () => {
    expect(ARRIVE_RADIUS_M).toBe(50);
    expect(stepAway(null, fixAt(0, 0.0003), STAY).away).toBeNull(); // 약 33m — 아직 안
  });
});

describe('2. 머무는 곳', () => {
  const arrived = (at: string): StopOutcome => ({ kind: 'ARRIVED', at, how: 'manual' });
  const outcomes: Record<string, StopOutcome> = { a: arrived('2026-09-26T10:00:00+09:00'), b: { kind: 'SKIPPED', at: '2026-09-26T10:30:00+09:00' }, c: arrived('2026-09-26T11:00:00+09:00') };

  it('🔴 도착은 적혔고 출발은 아직인 곳 중 가장 나중 것', () => {
    expect(stayingStopId(['a', 'b', 'c', 'd'], outcomes, new Set())).toBe('c');
  });

  it('출발까지 적힌 곳은 머무는 곳이 아니다 · 건너뛴 곳도 아니다', () => {
    expect(stayingStopId(['a', 'b', 'c', 'd'], outcomes, new Set(['c']))).toBe('a');
    expect(stayingStopId(['a', 'b', 'c', 'd'], outcomes, new Set(['a', 'c']))).toBeNull();
  });
});

describe('3. 시각 고치기', () => {
  const now = Date.parse('2026-09-26T12:00:00+09:00');

  it('🔴 미래는 고를 수 없다', () => {
    expect(clampActualTime(now + 10 * MIN, { nowMs: now, minMs: 0 })).toBe(now);
    expect(stepTime(now, 5, { nowMs: now, minMs: 0 })).toBe(now);
  });

  it('🔴 출발은 도착보다 이를 수 없다(서버 400)', () => {
    const arrivedAt = now - 3 * MIN;
    expect(minutesBefore(now, 15, arrivedAt)).toBe(arrivedAt);
    expect(stepTime(arrivedAt, -5, { nowMs: now, minMs: arrivedAt })).toBe(arrivedAt);
  });

  it('창에서 「5분 전」을 고르고 저장하면 그 시각', () => {
    const onSave = jest.fn();
    const before = Date.now();
    render(<ActualTimeSheet visible kind="arrival" placeTitle="해운대" currentMs={before} minMs={before - 60 * MIN} onCancel={jest.fn()} onSave={onSave} tx={tx} />);
    fireEvent.press(screen.getByText('5분 전'));
    fireEvent.press(screen.getByText('저장'));
    const saved = onSave.mock.calls[0][0] as number;
    expect(Math.abs(saved - (before - 5 * MIN))).toBeLessThan(5_000);
  });

  it('🔴 창의 「+5」로도 지금을 넘지 못한다', () => {
    const onSave = jest.fn();
    const at = Date.now();
    render(<ActualTimeSheet visible kind="departure" placeTitle="해운대" currentMs={at} minMs={at - 60 * MIN} onCancel={jest.fn()} onSave={onSave} tx={tx} />);
    fireEvent.press(screen.getByLabelText('5분 뒤로'));
    fireEvent.press(screen.getByText('저장'));
    expect(onSave.mock.calls[0][0]).toBeLessThanOrEqual(Date.now());
  });
});

describe('4. 지금 카드', () => {
  const card = (props: Partial<Parameters<typeof NowCard>[0]> = {}) => render(
    <NowCard status="RUNNING" gpsUsable title="해운대에 머무는 중" detail={null} clock="11:00" driftText={null} progress={null}
      showManualArrival onStart={jest.fn()} onPause={jest.fn()} onArrive={jest.fn()} onSkip={jest.fn()} tx={tx} {...props} />,
  );

  it('🔴 「출발」 모드에서는 위치가 잘 잡혀도 「도착 찍기」가 보인다(펼치지 않아도)', () => {
    card();
    expect(screen.getByText('도착 찍기')).toBeTruthy();
  });

  it('🔴 머무는 곳이 있으면 「출발 찍기」 — 도착·건너뛰기 대신', () => {
    const onDepart = jest.fn();
    card({ onDepart });
    fireEvent.press(screen.getByText('출발 찍기'));
    expect(onDepart).toHaveBeenCalled();
    expect(screen.queryByText('도착 찍기')).toBeNull();
    expect(screen.queryByText('건너뛰기')).toBeNull();
  });

  it('적은 시각 한 줄과 「시각 고치기」', () => {
    const onEdit = jest.fn();
    card({ record: { text: '도착 11:02', onEdit } });
    expect(screen.getByText('도착 11:02')).toBeTruthy();
    fireEvent.press(screen.getByText('시각 고치기'));
    expect(onEdit).toHaveBeenCalled();
  });

  it('🔴 앱을 닫으면 멈춘다는 한계를 말한다', () => {
    card();
    expect(screen.getByText('도착·출발은 위치로 자동 기록돼요 · 앱을 닫으면 멈춰요')).toBeTruthy();
  });

  it('일정을 다 돈 뒤에도 마지막 곳의 출발을 적을 수 있다', () => {
    card({ status: 'DONE', onDepart: jest.fn() });
    expect(screen.getByText('출발 찍기')).toBeTruthy();
  });
});

describe('5. 서버로 가는 것은 시각 둘뿐', () => {
  it('🔴 도착·출발 창구의 본문에는 arrivedAt · departedAt 만 — 좌표 칸이 없다', async () => {
    (apiRequest as jest.Mock).mockResolvedValue({ id: 'it1', days: [] });
    await recordItineraryItemActual({ itineraryId: 'it1', itemId: 's1', arrivedAt: '2026-09-26T11:00:00+09:00', departedAt: '2026-09-26T11:40:00+09:00', accessToken: 'tok' });
    const [path, options] = (apiRequest as jest.Mock).mock.calls[0];
    expect(path).toBe('/api/v1/itineraries/it1/items/s1/actual');
    expect(Object.keys(options.body).sort()).toEqual(['arrivedAt', 'departedAt']);
  });
});

describe('6. 화면이 규칙을 지킨다', () => {
  const mobile = source('src/trip/page/TripPageMobile.tsx');
  const classic = source('app/trips/[id]/itinerary.tsx');

  it('🔴 출발을 보낼 때 실제 도착(진행 기록)을 싣는다 — 예측 도착(pace.predictedArrival)이 아니라', () => {
    expect(classic).not.toContain('paceByItemId.get(item.id)?.predictedArrival');
    expect(classic).toContain('const existingArrival = arrivalOf(item.id);');
    expect(mobile).toContain("void recordDeparture(stayItem, stayArrivedAt, 'manual')");
  });

  it('🔴 예전 일정 화면의 「출발 찍기」 — 「다녀옴 + 출발 없음」이 아니라 「도착만 적힘」일 때', () => {
    expect(classic).not.toContain(': !pace.predictedDeparture ? <Pressable');
    expect(classic).toContain('onRecordDeparture={recordToday && arrivalOf(item.id)');
  });

  it('🔴 도착을 두 번 찍어 첫 도착을 덮지 않는다 · 오늘 방문지만', () => {
    expect(classic).toContain('onRecordArrival={recordToday && !arrivalOf(item.id)');
    expect(mobile).toContain('onArrive={canEdit && todayDay && !arrivedAtOf(progress.outcomes, item.id)');
  });

  it('🔴 도착을 고친 직후의 출발에는 고친 도착을 싣고, 출발은 도착보다 이르게 보내지 않는다(서버 400)', () => {
    // 로컬 캡처에서 잡았다 — 진행 기록을 다시 받기 전에 「출발 찍기」를 누르면 고치기 전 도착이 실렸다.
    expect(mobile).toContain('arrivalFix?.id === stayId ? arrivalFix.at');
    expect(mobile).toContain('Math.max(Date.now(), Date.parse(arrivedAt) || 0)');
  });

  it('자동 출발은 동의가 있을 때만 켜지는 위치를 쓴다', () => {
    expect(mobile).toContain("useLiveLocation(progress.status === 'RUNNING' && locationGate.consent === true)");
    expect(mobile).toContain('stepAway(awayRef.current, stamped, stayTargetRef.current)');
  });
});
