// 일정 조정 — 머무는 시간 바꾸기 · 고른 자리에 더하기 · 순서 고정/시각 고정 (S15P21E201-2017).
//
// 🔴 이 시험이 지키는 것:
//    1. 머무는 시간은 15분 눈금, 최소 15분, 최대 12시간이다 — 서버(ITINERARY_STAY_INVALID)와 같은 규칙.
//    2. 요청 본문이 서버 계약과 같다(/stay · position · mode).
//    3. 예전 고정(lockMode 없음)은 「순서 고정」으로 읽는다.
//    4. 서버가 새로 보내는 경고 두 가지(DAY_END_EXCEEDED · TIME_FIX_MISSED)를 사람 말로 바꾼다.
import { apiRequest, ApiClientError } from '@/api/client';
import {
  addItineraryItem,
  changeItineraryStay,
  setItineraryItemLocked,
  type ItineraryDto,
  type ItineraryItemDto,
} from '@/plan/itinerary';
import {
  describeAdjustWarnings,
  formatStay,
  insertPositionOptions,
  lockModeOf,
  MAX_STAY_MINUTES,
  MIN_STAY_MINUTES,
  stayMinutesOf,
  steppedStay,
} from '@/plan/itineraryAdjust';

jest.mock('@/api/client', () => ({ ...jest.requireActual('@/api/client'), apiRequest: jest.fn() }));

const mockedApi = apiRequest as jest.MockedFunction<typeof apiRequest>;
const ko = (korean: string) => korean;

function item(partial: Partial<ItineraryItemDto>): ItineraryItemDto {
  return { id: 'i1', startsAt: '2026-10-10T09:00:00+09:00', endsAt: '2026-10-10T10:30:00+09:00', title: '해운대', locked: false, placeId: 'p1', ...partial };
}

describe('1. 머무는 시간 눈금', () => {
  it('서버와 같은 한계 — 15분 ~ 12시간', () => {
    expect(MIN_STAY_MINUTES).toBe(15);
    expect(MAX_STAY_MINUTES).toBe(720);
  });

  it('시작·끝에서 머무는 분을 잰다. 하나라도 없으면 null(모른다)', () => {
    expect(stayMinutesOf(item({}))).toBe(90);
    expect(stayMinutesOf(item({ endsAt: null }))).toBeNull();
    expect(stayMinutesOf(item({ endsAt: undefined }))).toBeNull();
  });

  it('15분씩 늘고 준다', () => {
    expect(steppedStay(90, 1)).toBe(105);
    expect(steppedStay(90, -1)).toBe(75);
  });

  it('눈금에 안 맞는 값(47분)은 가까운 눈금으로 — 늘리면 60, 줄이면 45', () => {
    expect(steppedStay(47, 1)).toBe(60);
    expect(steppedStay(47, -1)).toBe(45);
  });

  it('🔴 15분 밑으로, 12시간 위로는 안 간다', () => {
    expect(steppedStay(15, -1)).toBe(15);
    expect(steppedStay(720, 1)).toBe(720);
  });

  it('사람 말로 적는다', () => {
    expect(formatStay(45, ko)).toBe('45분');
    expect(formatStay(60, ko)).toBe('1시간');
    expect(formatStay(105, ko)).toBe('1시간 45분');
  });
});

describe('2. 요청 본문', () => {
  beforeEach(() => mockedApi.mockReset());
  const dto = { id: 'it', title: 't', version: 3, days: [] } as unknown as ItineraryDto;

  it('머무는 시간 — POST .../items/{id}/stay {stayMinutes, baseVersion}', async () => {
    mockedApi.mockResolvedValueOnce(dto);
    const result = await changeItineraryStay({ itineraryId: 'it', itemId: 'i1', stayMinutes: 60, baseVersion: 2, accessToken: 't' });
    expect(result.state).toBe('success');
    expect(mockedApi).toHaveBeenCalledWith('/api/v1/itineraries/it/items/i1/stay', expect.objectContaining({ method: 'POST', body: { stayMinutes: 60, baseVersion: 2 } }));
  });

  it('머무는 시간 규칙을 어기면(400 ITINERARY_STAY_INVALID) 사람 말 오류', async () => {
    mockedApi.mockRejectedValueOnce(new ApiClientError('x', 'ITINERARY_STAY_INVALID', 400, []));
    const result = await changeItineraryStay({ itineraryId: 'it', itemId: 'i1', stayMinutes: 20, baseVersion: 2, accessToken: 't' });
    expect(result).toEqual({ state: 'error', message: '머무는 시간은 15분 단위로, 15분 이상이어야 해요.' });
  });

  it('고른 자리에 더하기 — position 을 싣는다. 자리를 안 주면 예전 본문 그대로', async () => {
    mockedApi.mockResolvedValue(dto);
    await addItineraryItem({ itineraryId: 'it', placeId: 'p9', dayIndex: 0, baseVersion: 2, position: 2, accessToken: 't' });
    expect(mockedApi).toHaveBeenLastCalledWith('/api/v1/itineraries/it/items', expect.objectContaining({ body: { placeId: 'p9', dayIndex: 0, baseVersion: 2, position: 2 } }));
    await addItineraryItem({ itineraryId: 'it', placeId: 'p9', dayIndex: 0, baseVersion: 2, accessToken: 't' });
    expect(mockedApi).toHaveBeenLastCalledWith('/api/v1/itineraries/it/items', expect.objectContaining({ body: { placeId: 'p9', dayIndex: 0, baseVersion: 2 } }));
  });

  it('🔴 그날 이미 있는 곳(409 ITINERARY_PLACE_ALREADY_ADDED)은 판 충돌이 아니라 그 사실을 말한다', async () => {
    mockedApi.mockRejectedValueOnce(new ApiClientError('x', 'ITINERARY_PLACE_ALREADY_ADDED', 409, []));
    const result = await addItineraryItem({ itineraryId: 'it', placeId: 'p9', dayIndex: 0, baseVersion: 2, position: 2, accessToken: 't' });
    expect(result).toEqual({ state: 'error', message: '그날 일정에 이미 들어 있는 장소예요.' });
  });

  it('고정 방식 — mode 를 싣는다', async () => {
    mockedApi.mockResolvedValue(dto);
    await setItineraryItemLocked({ itineraryId: 'it', itemId: 'i1', locked: true, mode: 'TIME', baseVersion: 2, accessToken: 't' });
    expect(mockedApi).toHaveBeenLastCalledWith('/api/v1/itineraries/it/items/i1/lock', expect.objectContaining({ body: { locked: true, baseVersion: 2, mode: 'TIME' } }));
  });
});

describe('3. 고정 방식 읽기', () => {
  it('🔴 예전 고정(lockMode 없음)은 순서 고정', () => {
    expect(lockModeOf(item({ locked: true }))).toBe('ORDER');
    expect(lockModeOf(item({ locked: true, lockMode: 'TIME' }))).toBe('TIME');
    expect(lockModeOf(item({ locked: false }))).toBeNull();
  });
});

describe('4. 넣을 자리 고르기', () => {
  it('맨 앞 · 각 장소 다음 — 자리 번호는 1부터', () => {
    const options = insertPositionOptions([item({ id: 'a', title: 'A' }), item({ id: 'b', title: 'B' })], ko);
    expect(options).toEqual([
      { position: 1, label: '맨 앞' },
      { position: 2, label: 'A 다음' },
      { position: 3, label: 'B 다음' },
    ]);
  });
});

describe('5. 새 경고', () => {
  const itinerary = { id: 'it', title: 't', version: 2, days: [{ date: '2026-10-10', items: [item({ id: 'a', title: '광안리' })] }] } as ItineraryDto;
  it('하루 끝을 넘김 · 시각 고정을 못 지킴', () => {
    expect(describeAdjustWarnings(itinerary, [
      { code: 'DAY_END_EXCEEDED', itemId: 'a', placeId: 'p', at: '2026-10-10T18:30:00+09:00' },
      { code: 'TIME_FIX_MISSED', itemId: 'a', placeId: 'p', at: '2026-10-10T13:30:00+09:00' },
      { code: 'OPENING_HOURS_CLOSED', itemId: 'a', placeId: 'p', at: 'x' },
    ], ko)).toEqual([
      '광안리에서 하루 활동 시간을 넘겨요. 다른 곳을 줄이거나 빼 보세요.',
      '광안리은(는) 시각 고정이지만 앞 일정이 길어 제 시각에 갈 수 없어요.',
    ]);
  });
});
