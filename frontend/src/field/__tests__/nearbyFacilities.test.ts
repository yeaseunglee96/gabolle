// 근처 편의시설 도우미 (S15P21E201-2052) — 걸음·요금 글·영업 상태·물품보관함 요금 글·서버 호출.
// 🔴 지키는 것: «모른다»를 «0원/닫았다/없다»로 읽지 않는다. 서버가 null 로 보낸 칸은 null 로 남아 화면이 «정보 없음»을 그린다.
import { apiRequest } from '@/api/client';

import {
  DEFAULT_LIMIT,
  DEFAULT_RADIUS_M,
  feeSummaryOf,
  formatWon,
  getNearbyFacilities,
  openStatusOf,
  PARKING_LINE_STAY_MINUTES,
  parkingLineOf,
  parseLockerFee,
  SEOMYEON,
  walkMinutesFor,
  type FacilityItem,
} from '../nearbyFacilities';

jest.mock('@/api/client', () => ({ apiRequest: jest.fn() }));

const apiMock = apiRequest as jest.Mock;

describe('걸어서 몇 분 — 직선거리 ÷ 80m/분', () => {
  it('분당 80m 로 나눠 반올림하고, 가까워도 1분보다 짧게 말하지 않는다', () => {
    expect(walkMinutesFor(0)).toBe(1);
    expect(walkMinutesFor(30)).toBe(1);
    expect(walkMinutesFor(80)).toBe(1);
    expect(walkMinutesFor(119)).toBe(1);
    expect(walkMinutesFor(120)).toBe(2);
    expect(walkMinutesFor(400)).toBe(5);
    expect(walkMinutesFor(700)).toBe(9);
    expect(walkMinutesFor(2000)).toBe(25);
  });
});

describe('원 단위 글', () => {
  it('세 자리마다 쉼표를 찍는다', () => {
    expect(formatWon(0)).toBe('0');
    expect(formatWon(300)).toBe('300');
    expect(formatWon(1200)).toBe('1,200');
    expect(formatWon(21000)).toBe('21,000');
    expect(formatWon(1234567)).toBe('1,234,567');
  });
});

describe('주차장 예상요금을 그릴 네 가지로', () => {
  const full = { baseMinutes: 30, baseWon: 300, addMinutes: 10, addWon: 100, dayMaxWon: 2400, freeMinutes: 0 };

  it('무료 주차장은 free', () => {
    expect(feeSummaryOf({ free: true, estimatedWon: 0, fee: null })).toEqual({ kind: 'free' });
  });

  it('🔴 예상요금이 0 보다 크면 그 금액이다', () => {
    expect(feeSummaryOf({ free: false, estimatedWon: 1200, fee: full })).toEqual({ kind: 'estimate', won: 1200 });
  });

  it('유료인데 예상요금이 0 인 것은 무료 시간 안의 체류다 — 무료로 그린다', () => {
    expect(feeSummaryOf({ free: false, estimatedWon: 0, fee: { ...full, freeMinutes: 60 } })).toEqual({ kind: 'free' });
  });

  it('🔴 예상요금이 null 이어도 기본요금을 알면 «기본 30분 300원»만 말한다 — 추가요금을 지어내지 않는다', () => {
    expect(feeSummaryOf({ free: false, estimatedWon: null, fee: { ...full, addWon: null } }))
      .toEqual({ kind: 'partial', baseMinutes: 30, baseWon: 300 });
  });

  it('🔴 요금표도 예상요금도 없으면 모른다 — 0원이 아니다', () => {
    expect(feeSummaryOf({ free: false, estimatedWon: null, fee: null })).toEqual({ kind: 'unknown' });
    expect(feeSummaryOf({ free: false, estimatedWon: null, fee: { ...full, baseWon: null } })).toEqual({ kind: 'unknown' });
  });
});

describe('장소 상세의 «근처 주차장» 한 줄 — 요금을 아는 곳 중 가장 싼 곳', () => {
  const lot = (id: string, distanceM: number, estimatedWon: number | null, extra: { free?: boolean; fee?: unknown } = {}): FacilityItem => ({
    id, name: id, address: null, lat: 35.1, lng: 129.1, distanceM, kind: 'PARKING',
    detail: {
      operator: null, free: extra.free ?? false, spaces: null,
      fee: (extra.fee as never) ?? null, estimatedWon, note: null, openNow: null, todayHours: null, closesAt: null, allDay: false,
    },
  });
  const wrap = (items: FacilityItem[], total = items.length) => ({ items, total });

  it('1시간 기준으로 묻는다 — 줄과 누르면 열리는 화면이 같은 숫자를 보게', () => {
    expect(PARKING_LINE_STAY_MINUTES).toBe(60);
  });

  it('요금을 아는 곳 중 가장 싼 곳의 요금과 거리', () => {
    expect(parkingLineOf(wrap([lot('비싼', 50, 3000), lot('싼', 450, 1200), lot('중간', 200, 2000)]))).toEqual({ kind: 'fee', won: 1200, distanceM: 450 });
  });

  it('🔴 요금을 모르는 곳은 가까워도 «가장 싼 곳»이 아니다 — null 을 0원으로 읽지 않는다', () => {
    expect(parkingLineOf(wrap([lot('모름', 10, null), lot('아는', 600, 2500)]))).toEqual({ kind: 'fee', won: 2500, distanceM: 600 });
  });

  it('값이 같으면 가까운 곳', () => {
    expect(parkingLineOf(wrap([lot('멀다', 500, 1000), lot('가깝다', 90, 1000)]))).toEqual({ kind: 'fee', won: 1000, distanceM: 90 });
  });

  it('무료가 있으면 무료 — free 표시든 예상요금 0 이든', () => {
    expect(parkingLineOf(wrap([lot('유료', 50, 1000), lot('무료', 300, 0, { free: true })]))).toEqual({ kind: 'free', distanceM: 300 });
    expect(parkingLineOf(wrap([lot('무료시간', 120, 0)]))).toEqual({ kind: 'free', distanceM: 120 });
  });

  it('서버가 순서를 바꿔 보내도 같은 답이다 — 첫 칸에 기대지 않는다', () => {
    const items = [lot('a', 100, null), lot('b', 800, 4000), lot('c', 300, 900), lot('d', 60, 900)];
    expect(parkingLineOf(wrap(items))).toEqual({ kind: 'fee', won: 900, distanceM: 60 });
    expect(parkingLineOf(wrap([...items].reverse()))).toEqual({ kind: 'fee', won: 900, distanceM: 60 });
  });

  it('🔴 요금을 아는 곳이 하나도 없으면 «몇 곳 · 가장 가까운 곳» — 기본요금만 아는 곳도 아는 곳이 아니다', () => {
    const partial = lot('일부', 300, null, { fee: { baseMinutes: 30, baseWon: 300, addMinutes: null, addWon: null, dayMaxWon: null, freeMinutes: null } });
    expect(parkingLineOf(wrap([partial, lot('모름', 150, null)]))).toEqual({ kind: 'unknown', count: 2, nearestM: 150 });
  });

  it('곳 수는 반경 안의 총 개수(total)다 — 보여 준 개수보다 많을 수 있다', () => {
    expect(parkingLineOf(wrap([lot('모름', 150, null)], 34))).toEqual({ kind: 'unknown', count: 34, nearestM: 150 });
  });

  it('🔴 하나도 없으면 null — 줄을 통째로 숨긴다', () => {
    expect(parkingLineOf(wrap([]))).toBeNull();
  });

  it('주차장이 아닌 것이 섞여 와도 세지 않는다', () => {
    const toilet = { id: 't', name: 't', address: null, lat: 1, lng: 1, distanceM: 5, kind: 'TOILET', detail: {} } as unknown as FacilityItem;
    expect(parkingLineOf(wrap([toilet]))).toBeNull();
    expect(parkingLineOf(wrap([toilet, lot('싼', 80, 700)]))).toEqual({ kind: 'fee', won: 700, distanceM: 80 });
  });
});

describe('지금 여는가 — 열었다 / 닫았다 / 모른다', () => {
  const hours = { open: '09:00', close: '18:00' };

  it('열었으면 닫는 시각과 하루 종일 여부를 같이', () => {
    expect(openStatusOf({ openNow: true, todayHours: hours, closesAt: '18:00', allDay: false }))
      .toEqual({ kind: 'open', closesAt: '18:00', allDay: false });
  });

  it('닫았으면 오늘 시간을 같이(없으면 null — 오늘 쉬는 날)', () => {
    expect(openStatusOf({ openNow: false, todayHours: hours, closesAt: null, allDay: false })).toEqual({ kind: 'closed', todayHours: hours });
    expect(openStatusOf({ openNow: false, todayHours: null, closesAt: null, allDay: false })).toEqual({ kind: 'closed', todayHours: null });
  });

  it('🔴 openNow 가 null 이면 «닫았다»가 아니라 모른다', () => {
    expect(openStatusOf({ openNow: null, todayHours: null, closesAt: null, allDay: false })).toEqual({ kind: 'unknown' });
  });
});

describe('물품보관함 요금 글에서 크기별 요금 뽑기', () => {
  it('실제 파일의 세 가지 모양을 읽는다', () => {
    expect(parseLockerFee('특대: 6,000원 / 대:4,000원 / 중: 3,000원 / 소: 2,000원(3시간당)')).toEqual({
      perHours: 3, bySize: { xlarge: 6000, large: 4000, medium: 3000, small: 2000 },
    });
    // 중형이 없는 판
    expect(parseLockerFee('특대: 6,000원 / 대:4,500원 /  소: 2,500원(3시간당)')).toEqual({
      perHours: 3, bySize: { xlarge: 6000, large: 4500, small: 2500 },
    });
    // 「소 :」 앞에 공백, 「3시간 당」 사이에 공백
    expect(parseLockerFee('특대: 6,000원 / 대: 4,000원 / 소 : 2,000원 (3시간 당)')).toEqual({
      perHours: 3, bySize: { xlarge: 6000, large: 4000, small: 2000 },
    });
  });

  it('🔴 «특대»의 대가 «대형»으로 읽히지 않는다', () => {
    const fee = parseLockerFee('특대: 6,000원');
    expect(fee?.bySize).toEqual({ xlarge: 6000 });
  });

  it('시간 단위가 글에 없으면 perHours 는 null', () => {
    expect(parseLockerFee('소: 1,000원')).toEqual({ perHours: null, bySize: { small: 1000 } });
  });

  it('읽을 수 있는 크기가 없으면 null — 부르는 쪽이 원문을 그대로 보여 준다', () => {
    expect(parseLockerFee(null)).toBeNull();
    expect(parseLockerFee('')).toBeNull();
    expect(parseLockerFee('무료')).toBeNull();
    expect(parseLockerFee('현장 문의')).toBeNull();
  });
});

describe('서버 호출', () => {
  beforeEach(() => apiMock.mockReset());

  const response = { kind: 'PARKING', radiusM: 700, stayMinutes: 120, total: 0, limit: 20, items: [] };

  it('🔴 좌표는 약 100m 단위(소수 셋째 자리)로 줄여서 보낸다 — 위치 동의가 약속한 대로', async () => {
    apiMock.mockResolvedValue(response);

    await getNearbyFacilities('PARKING', { latitude: 35.158287, longitude: 129.059561 }, 'token', { stayMinutes: 120 });

    const [path, options] = apiMock.mock.calls[0];
    const query = new URLSearchParams(String(path).split('?')[1]);
    expect(String(path).startsWith('/api/v1/facilities/nearby?')).toBe(true);
    expect(query.get('kind')).toBe('PARKING');
    expect(query.get('lat')).toBe('35.158');
    expect(query.get('lng')).toBe('129.06');
    expect(query.get('radiusM')).toBe(String(DEFAULT_RADIUS_M));
    expect(query.get('limit')).toBe(String(DEFAULT_LIMIT));
    expect(query.get('stayMinutes')).toBe('120');
    expect(query.has('accessible')).toBe(false);
    expect(options.accessToken).toBe('token');
  });

  it('🔴 exact — 장소의 자리는 줄이지 않고 보낸다(소수 다섯째 자리까지). 줄이면 «그 장소에서 몇 m»가 틀어진다', async () => {
    apiMock.mockResolvedValue(response);

    await getNearbyFacilities('PARKING', { latitude: 35.158287, longitude: 129.059561 }, 'token', { exact: true, stayMinutes: 60 });

    const query = new URLSearchParams(String(apiMock.mock.calls[0][0]).split('?')[1]);
    expect(query.get('lat')).toBe('35.15829');
    expect(query.get('lng')).toBe('129.05956');
    expect(query.get('stayMinutes')).toBe('60');
  });

  it('exact 가 아니면(내 위치) 그대로 100m 단위다', async () => {
    apiMock.mockResolvedValue(response);

    await getNearbyFacilities('PARKING', { latitude: 35.158287, longitude: 129.059561 }, 'token', { exact: false });

    const query = new URLSearchParams(String(apiMock.mock.calls[0][0]).split('?')[1]);
    expect(query.get('lat')).toBe('35.158');
    expect(query.get('lng')).toBe('129.06');
  });

  it('체류 시간은 주차장에만, 필터는 화장실에만 붙는다', async () => {
    apiMock.mockResolvedValue({ ...response, kind: 'TOILET' });

    await getNearbyFacilities('TOILET', SEOMYEON, null, { stayMinutes: 120, accessible: true, diaper: true });
    const toilet = new URLSearchParams(String(apiMock.mock.calls[0][0]).split('?')[1]);
    expect(toilet.has('stayMinutes')).toBe(false);
    expect(toilet.get('accessible')).toBe('true');
    expect(toilet.get('diaper')).toBe('true');

    await getNearbyFacilities('PHARMACY', SEOMYEON, null, { accessible: true, diaper: true });
    const pharmacy = new URLSearchParams(String(apiMock.mock.calls[1][0]).split('?')[1]);
    expect(pharmacy.has('accessible')).toBe(false);
    expect(pharmacy.has('diaper')).toBe(false);
  });

  it('반경·개수·취소 신호를 그대로 넘긴다. 필터를 false 로 주면 붙이지 않는다', async () => {
    apiMock.mockResolvedValue({ ...response, kind: 'TOILET' });
    const controller = new AbortController();

    await getNearbyFacilities('TOILET', SEOMYEON, 'token', { radiusM: 2000, limit: 5, accessible: false, diaper: false, signal: controller.signal });

    const [path, options] = apiMock.mock.calls[0];
    const query = new URLSearchParams(String(path).split('?')[1]);
    expect(query.get('radiusM')).toBe('2000');
    expect(query.get('limit')).toBe('5');
    expect(query.has('accessible')).toBe(false);
    expect(options.signal).toBe(controller.signal);
  });

  it('응답의 모양이 틀리면 던진다 — 빈 목록처럼 보이게 두지 않는다', async () => {
    apiMock.mockResolvedValue({ kind: 'PARKING' });
    await expect(getNearbyFacilities('PARKING', SEOMYEON, null)).rejects.toThrow();
    apiMock.mockResolvedValue(null);
    await expect(getNearbyFacilities('PARKING', SEOMYEON, null)).rejects.toThrow();
  });

  it('서버 오류는 그대로 던진다 — 화면이 «불러오지 못했어요»를 그린다', async () => {
    apiMock.mockRejectedValue(new Error('boom'));
    await expect(getNearbyFacilities('LOCKER', SEOMYEON, null)).rejects.toThrow('boom');
  });
});
