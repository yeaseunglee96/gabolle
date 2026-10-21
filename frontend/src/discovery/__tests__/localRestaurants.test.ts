// 로컬 탐색 「맛집」·「카페」 갈래의 자료 층 (S15P21E201-2051, 카페·내 위치·현지인 추천은 S15P21E201-2059) — 서버 두 끝점을 부르는 모양, 이름 읽는 법, 출처 배지.
import { apiRequest } from '@/api/client';
import {
  areaLabel,
  getLocalAreas,
  getLocalList,
  isLocalCafe,
  isLocalRestaurant,
  LOCAL_CAFE_KEY,
  LOCAL_RESTAURANT_KEY,
  localKindOf,
  placeCafeAfterRestaurant,
  RESTAURANT_SOURCES,
  sourceBadgeLabels,
  toRestaurantItem,
} from '@/discovery/localRestaurants';

jest.mock('@/api/client', () => ({ ...jest.requireActual('@/api/client'), apiRequest: jest.fn() }));

const mockedApiRequest = apiRequest as jest.MockedFunction<typeof apiRequest>;

const dto = (overrides: Record<string, unknown> = {}) => ({
  placeId: 'p1', nameKo: '국제밀면본점', nameEn: null, category: 'FOOD', address: '연제구 중앙대로1235번길 23-6', lat: 35.19, lng: 129.07, matchedField: null,
  gu: '연제구', dong: '거제동', certifiedSources: ['BUSAN_FOOD_INFO', 'MODEL_RESTAURANT'], ...overrides,
});

beforeEach(() => mockedApiRequest.mockReset());

describe('맛집·카페 갈래 열쇠', () => {
  it('서버가 주는 낱말과 같다 — /facets 의 탐색 갈래 끝에 이 열쇠로 온다', () => {
    expect(LOCAL_RESTAURANT_KEY).toBe('LOCAL_RESTAURANT');
    expect(LOCAL_CAFE_KEY).toBe('LOCAL_CAFE');
    expect(isLocalRestaurant({ featureKey: 'LOCAL_RESTAURANT' })).toBe(true);
    expect(isLocalRestaurant({ featureKey: 'FESTIVAL' })).toBe(false);
    expect(isLocalRestaurant(null)).toBe(false);
    expect(isLocalRestaurant(undefined)).toBe(false);
    expect(isLocalCafe({ featureKey: 'LOCAL_CAFE' })).toBe(true);
    expect(isLocalCafe({ featureKey: 'LOCAL_RESTAURANT' })).toBe(false);
  });

  it('갈래 열쇠가 어느 종류(kind)인지 가른다 — 둘 다 아니면 일반 갈래다', () => {
    expect(localKindOf({ featureKey: 'LOCAL_RESTAURANT' })).toBe('RESTAURANT');
    expect(localKindOf({ featureKey: 'LOCAL_CAFE' })).toBe('CAFE');
    expect(localKindOf({ featureKey: 'WALK' })).toBeNull();
    expect(localKindOf({ featureKey: 'LOCAL_PLACES' })).toBeNull();
    expect(localKindOf(null)).toBeNull();
    expect(localKindOf(undefined)).toBeNull();
  });
});

describe('서버 부르기 — 구·동 목록', () => {
  it('맛집(기본)은 예전 요청과 똑같다 — kind 도 좌표도 안 붙는다', async () => {
    mockedApiRequest.mockResolvedValue({ areas: [{ gu: '해운대구', placeCount: 85, dongs: [{ dong: '우동', placeCount: 14 }] }, { gu: '중구', placeCount: 57, dongs: [] }], generatedAt: 'x' });

    const result = await getLocalAreas('RESTAURANT');

    expect(mockedApiRequest).toHaveBeenCalledWith('/api/v1/places/restaurants/areas', { signal: undefined });
    expect(result.areas.map((area) => area.gu)).toEqual(['해운대구', '중구']);
    expect(result.areas[0].dongs[0]).toEqual({ dong: '우동', placeCount: 14 });
    // 좌표를 안 줬으니 가장 가까운 구도 없다.
    expect(result.nearestGu).toBeNull();
  });

  it('카페는 kind=CAFE 를 붙인다', async () => {
    mockedApiRequest.mockResolvedValue({ areas: [], generatedAt: 'x' });

    await getLocalAreas('CAFE');

    expect(mockedApiRequest.mock.calls[0][0]).toBe('/api/v1/places/restaurants/areas?kind=CAFE');
  });

  it('🔴 좌표는 약 100m(소수 셋째 자리)로 줄여 보낸다 — 주소창에 실려 서버 접속 기록에 남는다', async () => {
    mockedApiRequest.mockResolvedValue({ areas: [], nearestGu: '해운대구', generatedAt: 'x' });

    await getLocalAreas('CAFE', { coords: { latitude: 35.16312345, longitude: 129.16367891 } });
    await getLocalAreas('RESTAURANT', { coords: { latitude: 35.16312345, longitude: 129.16367891 } });

    expect(mockedApiRequest.mock.calls[0][0]).toBe('/api/v1/places/restaurants/areas?kind=CAFE&lat=35.163&lng=129.164');
    // 맛집은 kind 를 안 붙인다 — 좌표만 더해진다.
    expect(mockedApiRequest.mock.calls[1][0]).toBe('/api/v1/places/restaurants/areas?lat=35.163&lng=129.164');
    // 줄이지 않은 자리(소수 여덟 자리)는 어디에도 실리지 않는다.
    expect(mockedApiRequest.mock.calls.map((call) => call[0]).join('')).not.toContain('35.1631');
  });

  it('가장 가까운 구를 그대로 받고, 비었거나 글자가 아니면 null 이다 — 지어내지 않는다', async () => {
    mockedApiRequest.mockResolvedValueOnce({ areas: [], nearestGu: '해운대구' });
    expect((await getLocalAreas('CAFE', { coords: { latitude: 35.1, longitude: 129.1 } })).nearestGu).toBe('해운대구');

    for (const bad of [null, undefined, '', '   ', 7, {}]) {
      mockedApiRequest.mockResolvedValueOnce({ areas: [], nearestGu: bad });
      expect((await getLocalAreas('CAFE', { coords: { latitude: 35.1, longitude: 129.1 } })).nearestGu).toBeNull();
    }
  });

  it('🔴 서버가 areas 를 안 주면 빈 목록이다 — 깨지지 않는다', async () => {
    mockedApiRequest.mockResolvedValue({});
    expect(await getLocalAreas('RESTAURANT')).toEqual({ areas: [], nearestGu: null });
  });
});

describe('서버 부르기 — 목록', () => {
  it('🔴 한글 구·동은 주소창에 퍼센트 부호로 실린다 — 동을 안 고르면 dong 칸이 없다(구 전체)', async () => {
    mockedApiRequest.mockResolvedValue({ items: [], limit: 100, hasNext: false });

    await getLocalList('RESTAURANT', { gu: '해운대구', dong: '우동' });
    await getLocalList('RESTAURANT', { gu: '해운대구', dong: null });

    expect(mockedApiRequest.mock.calls[0][0]).toBe('/api/v1/places/restaurants?gu=%ED%95%B4%EC%9A%B4%EB%8C%80%EA%B5%AC&dong=%EC%9A%B0%EB%8F%99');
    expect(mockedApiRequest.mock.calls[1][0]).toBe('/api/v1/places/restaurants?gu=%ED%95%B4%EC%9A%B4%EB%8C%80%EA%B5%AC');
  });

  it('카페는 kind=CAFE 가 구·동보다 앞에 붙는다', async () => {
    mockedApiRequest.mockResolvedValue({ items: [], limit: 100, hasNext: false });

    await getLocalList('CAFE', { gu: '해운대구', dong: '우동' });
    await getLocalList('CAFE', { gu: '해운대구', dong: null });

    expect(mockedApiRequest.mock.calls[0][0]).toBe('/api/v1/places/restaurants?kind=CAFE&gu=%ED%95%B4%EC%9A%B4%EB%8C%80%EA%B5%AC&dong=%EC%9A%B0%EB%8F%99');
    expect(mockedApiRequest.mock.calls[1][0]).toBe('/api/v1/places/restaurants?kind=CAFE&gu=%ED%95%B4%EC%9A%B4%EB%8C%80%EA%B5%AC');
  });

  it('목록 한 줄은 기존 장소 카드가 쓰는 칸 그대로에 구·동·소개한 목록이 더해진다', async () => {
    mockedApiRequest.mockResolvedValue({ items: [dto({ photoUrl: 'https://example.invalid/a.jpg', photoSource: '출처 : 시험' })], limit: 100, hasNext: true });

    const page = await getLocalList('RESTAURANT', { gu: '연제구', dong: '거제동' });

    expect(page.hasNext).toBe(true);
    const item = page.items[0];
    expect(item.placeId).toBe('p1');
    expect(item.nameKo).toBe('국제밀면본점');
    expect(item.photoUrl).toBe('https://example.invalid/a.jpg');
    expect(item.photoSource).toBe('출처 : 시험');
    expect(item.gu).toBe('연제구');
    expect(item.dong).toBe('거제동');
    expect(item.certifiedSources).toEqual(['BUSAN_FOOD_INFO', 'MODEL_RESTAURANT']);
    // 이름 검색에서만 쓰는 칸은 카드로 가져오지 않는다 — 기존 변환과 같은 규칙.
    expect('matchedField' in item).toBe(false);
    // 방송·수상 칸은 서버가 줄 때만 있다.
    expect('broadcastOrAward' in item).toBe(false);
  });

  it('구·동·소개한 목록이 비어 오면 null 과 빈 목록으로 채운다', () => {
    const item = toRestaurantItem(dto({ gu: undefined, dong: null, certifiedSources: undefined }));
    expect(item.gu).toBeNull();
    expect(item.dong).toBeNull();
    expect(item.certifiedSources).toEqual([]);
  });

  it('🔴 레드테이블의 방송·수상 표시는 서버가 참을 줄 때만 참이다 — 다른 값은 없는 것으로 읽는다', () => {
    expect(toRestaurantItem(dto({ broadcastOrAward: true })).broadcastOrAward).toBe(true);
    for (const other of [false, null, undefined, 'true', 1]) {
      expect(toRestaurantItem(dto({ broadcastOrAward: other })).broadcastOrAward).toBeUndefined();
    }
  });
});

describe('구·동 이름', () => {
  it('한국어 화면은 이름 그대로다', () => {
    expect(areaLabel('해운대구', 'ko')).toBe('해운대구');
  });

  it('🔴 그 밖의 화면은 한국어 이름을 지우지 않고 읽는 법을 곁들인다 — 길을 묻거나 택시를 탈 때 쓰는 말이다', () => {
    expect(areaLabel('해운대구', 'en')).toBe('해운대구 (Haeundaegu)');
    expect(areaLabel('우동', 'ja')).toBe('우동 (Udong)');
    expect(areaLabel('우동', 'zh-Hans')).toBe('우동 (Udong)');
    expect(areaLabel('우동', 'zh-Hant')).toBe('우동 (Udong)');
  });

  it('읽는 법을 만들 수 없는 글자(한글이 없음)는 그대로 둔다', () => {
    expect(areaLabel('Haeundae', 'en')).toBe('Haeundae');
  });
});

describe('출처 배지', () => {
  const tx = (ko: string, en: string) => `${ko}|${en}`;

  it('🔴 서버 순서가 아니라 고정 순서(부산시 맛집 → 모범음식점 → 택슐랭 → 관광공사 → 레드테이블 → 현지인 추천)로 그린다', () => {
    expect(sourceBadgeLabels(['LOCAL_SURVEY', 'TAXI_DRIVER_PICK', 'REDTABLE_FEATURED', 'MODEL_RESTAURANT', 'KTO_CAFE', 'BUSAN_FOOD_INFO'], tx)).toEqual([
      '부산시 맛집|Busan Food Guide',
      '모범음식점|Model Restaurant',
      '택슐랭|Taxi drivers’ pick',
      '관광공사 추천|Korea Tourism pick',
      '레드테이블|Red Table',
      '현지인 추천|Local pick',
    ]);
    // 서버가 주는 낱말과 배지 순서의 기준이 같다.
    expect([...RESTAURANT_SOURCES]).toEqual(['BUSAN_FOOD_INFO', 'MODEL_RESTAURANT', 'TAXI_DRIVER_PICK', 'KTO_CAFE', 'REDTABLE_FEATURED', 'LOCAL_SURVEY']);
  });

  it('🔴 레드테이블이 방송·수상 이력을 적어 둔 곳만 「레드테이블 · 방송·수상」이다 — 아니면 그냥 「레드테이블」', () => {
    expect(sourceBadgeLabels(['REDTABLE_FEATURED'], tx, { broadcastOrAward: true })).toEqual(['레드테이블 · 방송·수상|Red Table · TV & awards']);
    expect(sourceBadgeLabels(['REDTABLE_FEATURED'], tx, { broadcastOrAward: false })).toEqual(['레드테이블|Red Table']);
    expect(sourceBadgeLabels(['REDTABLE_FEATURED'], tx)).toEqual(['레드테이블|Red Table']);
    // 방송·수상 표시는 레드테이블 배지에만 영향을 준다 — 다른 출처를 방송으로 부풀리지 않는다.
    expect(sourceBadgeLabels(['KTO_CAFE'], tx, { broadcastOrAward: true })).toEqual(['관광공사 추천|Korea Tourism pick']);
  });

  it('모르는 낱말은 이름을 못 붙이니 건너뛴다 — 지어내지 않는다', () => {
    expect(sourceBadgeLabels(['MICHELIN', 'MODEL_RESTAURANT'], tx)).toEqual(['모범음식점|Model Restaurant']);
    expect(sourceBadgeLabels([], tx)).toEqual([]);
  });
});

describe('카페는 맛집 바로 뒤', () => {
  const key = (featureKey: string) => ({ featureKey });
  const order = (list: Array<{ featureKey: string }>) => list.map((entry) => entry.featureKey);

  it('🔴 글자순으로 온 응답(LOCAL_CAFE 가 앞)도 맛집 → 카페 순서로 바로잡는다 — 다른 갈래의 자리는 그대로다', () => {
    const fromServer = [key('FESTIVAL'), key('WALK'), key('LOCAL_CAFE'), key('LOCAL_RESTAURANT')];
    expect(order(placeCafeAfterRestaurant(fromServer))).toEqual(['FESTIVAL', 'WALK', 'LOCAL_RESTAURANT', 'LOCAL_CAFE']);
    // 받은 배열을 고치지 않는다.
    expect(order(fromServer)).toEqual(['FESTIVAL', 'WALK', 'LOCAL_CAFE', 'LOCAL_RESTAURANT']);
  });

  it('맛집이 앞쪽에 있고 카페가 멀리 있어도 맛집 바로 뒤로 온다', () => {
    const spread = [key('LOCAL_RESTAURANT'), key('WALK'), key('NATURE'), key('LOCAL_CAFE')];
    expect(order(placeCafeAfterRestaurant(spread))).toEqual(['LOCAL_RESTAURANT', 'LOCAL_CAFE', 'WALK', 'NATURE']);
  });

  it('이미 그 순서이거나, 둘 중 하나가 없으면 받은 배열 그대로다', () => {
    const already = [key('WALK'), key('LOCAL_RESTAURANT'), key('LOCAL_CAFE')];
    expect(placeCafeAfterRestaurant(already)).toBe(already);
    const onlyCafe = [key('WALK'), key('LOCAL_CAFE')];
    expect(placeCafeAfterRestaurant(onlyCafe)).toBe(onlyCafe);
    const onlyRestaurant = [key('LOCAL_RESTAURANT'), key('WALK')];
    expect(placeCafeAfterRestaurant(onlyRestaurant)).toBe(onlyRestaurant);
    expect(placeCafeAfterRestaurant([])).toEqual([]);
  });
});
