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
  parseLockerFee,
  SEOMYEON,
  walkMinutesFor,
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
