// 로컬 탐색 「맛집」 갈래의 자료 층 (S15P21E201-2051) — 서버 두 끝점을 부르는 모양, 이름 읽는 법, 출처 배지.
import { apiRequest } from '@/api/client';
import {
  areaLabel,
  getRestaurantAreas,
  getRestaurants,
  isLocalRestaurant,
  LOCAL_RESTAURANT_KEY,
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

describe('맛집 갈래 열쇠', () => {
  it('서버가 주는 낱말과 같다 — /facets 의 탐색 갈래 끝에 이 열쇠로 온다', () => {
    expect(LOCAL_RESTAURANT_KEY).toBe('LOCAL_RESTAURANT');
    expect(isLocalRestaurant({ featureKey: 'LOCAL_RESTAURANT' })).toBe(true);
    expect(isLocalRestaurant({ featureKey: 'FESTIVAL' })).toBe(false);
    expect(isLocalRestaurant(null)).toBe(false);
    expect(isLocalRestaurant(undefined)).toBe(false);
  });
});

describe('서버 부르기', () => {
  it('구·동 목록은 구 → 동 그대로 받는다 — 서버가 준 순서를 바꾸지 않는다', async () => {
    mockedApiRequest.mockResolvedValue({ areas: [{ gu: '해운대구', placeCount: 85, dongs: [{ dong: '우동', placeCount: 14 }] }, { gu: '중구', placeCount: 57, dongs: [] }], generatedAt: 'x' });

    const areas = await getRestaurantAreas();

    expect(mockedApiRequest).toHaveBeenCalledWith('/api/v1/places/restaurants/areas', { signal: undefined });
    expect(areas.map((area) => area.gu)).toEqual(['해운대구', '중구']);
    expect(areas[0].dongs[0]).toEqual({ dong: '우동', placeCount: 14 });
  });

  it('🔴 서버가 areas 를 안 주면 빈 목록이다 — 깨지지 않는다', async () => {
    mockedApiRequest.mockResolvedValue({});
    expect(await getRestaurantAreas()).toEqual([]);
  });

  it('🔴 한글 구·동은 주소창에 퍼센트 부호로 실린다 — 동을 안 고르면 dong 칸이 없다(구 전체)', async () => {
    mockedApiRequest.mockResolvedValue({ items: [], limit: 100, hasNext: false });

    await getRestaurants({ gu: '해운대구', dong: '우동' });
    await getRestaurants({ gu: '해운대구', dong: null });

    expect(mockedApiRequest.mock.calls[0][0]).toBe('/api/v1/places/restaurants?gu=%ED%95%B4%EC%9A%B4%EB%8C%80%EA%B5%AC&dong=%EC%9A%B0%EB%8F%99');
    expect(mockedApiRequest.mock.calls[1][0]).toBe('/api/v1/places/restaurants?gu=%ED%95%B4%EC%9A%B4%EB%8C%80%EA%B5%AC');
  });

  it('목록 한 줄은 기존 장소 카드가 쓰는 칸 그대로에 구·동·인증 목록이 더해진다', async () => {
    mockedApiRequest.mockResolvedValue({ items: [dto({ photoUrl: 'https://example.invalid/a.jpg', photoSource: '출처 : 시험' })], limit: 100, hasNext: true });

    const page = await getRestaurants({ gu: '연제구', dong: '거제동' });

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
  });

  it('구·동·인증 목록이 비어 오면 null 과 빈 목록으로 채운다', () => {
    const item = toRestaurantItem(dto({ gu: undefined, dong: null, certifiedSources: undefined }));
    expect(item.gu).toBeNull();
    expect(item.dong).toBeNull();
    expect(item.certifiedSources).toEqual([]);
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

  it('🔴 서버 순서가 아니라 고정 순서(부산시 맛집 → 모범음식점 → 택슐랭)로 그린다', () => {
    expect(sourceBadgeLabels(['TAXI_DRIVER_PICK', 'MODEL_RESTAURANT', 'BUSAN_FOOD_INFO'], tx)).toEqual([
      '부산시 맛집|Busan Food Guide',
      '모범음식점|Model Restaurant',
      '택슐랭|Taxi drivers’ pick',
    ]);
  });

  it('모르는 낱말은 이름을 못 붙이니 건너뛴다 — 지어내지 않는다', () => {
    expect(sourceBadgeLabels(['MICHELIN', 'MODEL_RESTAURANT'], tx)).toEqual(['모범음식점|Model Restaurant']);
    expect(sourceBadgeLabels([], tx)).toEqual([]);
  });
});
