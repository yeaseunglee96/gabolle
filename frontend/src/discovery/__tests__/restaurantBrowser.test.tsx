// 로컬 탐색 「맛집」 갈래의 본문 (S15P21E201-2051) — 구 칩 → 동 칩 → 목록의 흐름.
//
// 🔴 이 화면의 결함은 타입 검사도 단위 시험도 안 잡는다(frontend/CLAUDE.md). 그래서 «눌렀을 때 무엇이 나오고 무엇이 사라지는가»를
//    시험으로 박는다 — 특히 구를 바꾼 뒤 옛 구의 목록이 남지 않는 것, 동을 바꾼 첫 그림에 옛 동의 카드가 비치지 않는 것.
import { Text } from 'react-native';
import { fireEvent, render, screen } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { RestaurantBrowser, RestaurantSourceBadges } from '@/discovery/RestaurantBrowser';
import { getRestaurantAreas, getRestaurants, type RestaurantGu, type RestaurantItem } from '@/discovery/localRestaurants';

jest.mock('@/discovery/localRestaurants', () => ({
  ...jest.requireActual('@/discovery/localRestaurants'),
  getRestaurantAreas: jest.fn(),
  getRestaurants: jest.fn(),
}));

const mockedAreas = getRestaurantAreas as jest.MockedFunction<typeof getRestaurantAreas>;
const mockedList = getRestaurants as jest.MockedFunction<typeof getRestaurants>;

const AREAS: RestaurantGu[] = [
  { gu: '해운대구', placeCount: 3, dongs: [{ dong: '우동', placeCount: 2 }, { dong: '중동', placeCount: 1 }] },
  { gu: '중구', placeCount: 1, dongs: [{ dong: '남포동', placeCount: 1 }] },
];

function restaurant(placeId: string, nameKo: string, gu: string, dong: string): RestaurantItem {
  return { placeId, nameKo, nameEn: null, category: 'FOOD', address: `${gu} 시험로 1`, lat: 35.1, lng: 129.1, gu, dong, certifiedSources: ['MODEL_RESTAURANT'] };
}

const BY_AREA: Record<string, RestaurantItem[]> = {
  '해운대구/우동': [restaurant('a', '가식당', '해운대구', '우동'), restaurant('b', '나식당', '해운대구', '우동')],
  '해운대구/중동': [restaurant('c', '다식당', '해운대구', '중동')],
  '해운대구/*': [restaurant('a', '가식당', '해운대구', '우동'), restaurant('b', '나식당', '해운대구', '우동'), restaurant('c', '다식당', '해운대구', '중동')],
  '중구/남포동': [restaurant('d', '라식당', '중구', '남포동')],
};

function mount() {
  return render(
    <OnboardingPreferencesProvider>
      <RestaurantBrowser wide={false} renderRows={(items) => <>{items.map((item) => <Text key={item.placeId}>{`카드:${item.nameKo}`}</Text>)}</>} />
    </OnboardingPreferencesProvider>,
  );
}

beforeEach(() => {
  mockedAreas.mockReset();
  mockedList.mockReset();
  mockedAreas.mockResolvedValue(AREAS);
  mockedList.mockImplementation(async (area) => ({ items: BY_AREA[`${area.gu}/${area.dong ?? '*'}`] ?? [], hasNext: false }));
});

describe('구 → 동 → 목록', () => {
  it('처음에는 구 칩만 있고, 구를 고르라고 말한다 — 동 칩도 목록도 아직 없다', async () => {
    mount();

    expect(await screen.findByText('먼저 구를 골라 주세요.')).toBeTruthy();
    expect(screen.getByText('구 고르기')).toBeTruthy();
    expect(screen.getByText('해운대구')).toBeTruthy();
    expect(screen.getByText('중구')).toBeTruthy();
    // 칩마다 맛집 수가 곁든다.
    expect(screen.getByText('3')).toBeTruthy();
    expect(screen.queryByText('동 고르기')).toBeNull();
    expect(mockedList).not.toHaveBeenCalled();
  });

  it('🔴 구를 고르면 그 구의 동 칩 한 줄이 나온다 — 「전체」가 맨 앞이고 아직 목록은 안 부른다', async () => {
    mount();
    fireEvent.press(await screen.findByText('해운대구'));

    expect(await screen.findByText('동 고르기')).toBeTruthy();
    expect(screen.getByText('✓ 해운대구')).toBeTruthy();
    expect(screen.getByText('전체')).toBeTruthy();
    expect(screen.getByText('우동')).toBeTruthy();
    expect(screen.getByText('중동')).toBeTruthy();
    // 다른 구의 동은 안 나온다.
    expect(screen.queryByText('남포동')).toBeNull();
    expect(screen.getByText('동을 골라 주세요. 「전체」를 누르면 이 구의 맛집이 모두 나와요.')).toBeTruthy();
    expect(mockedList).not.toHaveBeenCalled();
  });

  it('🔴 동을 고르면 그 구·동으로 서버를 부르고 기존 카드 격자(renderRows)에 그 동의 맛집을 넘긴다', async () => {
    mount();
    fireEvent.press(await screen.findByText('해운대구'));
    fireEvent.press(await screen.findByText('우동'));

    expect(await screen.findByText('카드:가식당')).toBeTruthy();
    expect(screen.getByText('카드:나식당')).toBeTruthy();
    expect(screen.queryByText('카드:다식당')).toBeNull();
    expect(mockedList).toHaveBeenCalledTimes(1);
    expect(mockedList.mock.calls[0][0]).toEqual({ gu: '해운대구', dong: '우동' });
    // 어느 곳의 몇 곳인지 한 줄.
    expect(screen.getByText('해운대구 · 우동 · 2곳')).toBeTruthy();
    expect(screen.getByText('✓ 우동')).toBeTruthy();
  });

  it('「전체」를 고르면 동 없이 구 전체를 부른다', async () => {
    mount();
    fireEvent.press(await screen.findByText('해운대구'));
    fireEvent.press(await screen.findByText('전체'));

    expect(await screen.findByText('카드:다식당')).toBeTruthy();
    expect(mockedList.mock.calls[0][0]).toEqual({ gu: '해운대구', dong: null });
    expect(screen.getByText('해운대구 · 전체 · 3곳')).toBeTruthy();
  });

  it('🔴 구를 바꾸면 동 선택과 옛 목록이 사라진다 — 새 구의 동을 고를 때까지 옛 구의 카드가 남지 않는다', async () => {
    mount();
    fireEvent.press(await screen.findByText('해운대구'));
    fireEvent.press(await screen.findByText('우동'));
    expect(await screen.findByText('카드:가식당')).toBeTruthy();

    fireEvent.press(screen.getByText('중구'));

    expect(await screen.findByText('남포동')).toBeTruthy();
    expect(screen.queryByText('카드:가식당')).toBeNull();
    expect(screen.queryByText('우동')).toBeNull();
    expect(screen.getByText('동을 골라 주세요. 「전체」를 누르면 이 구의 맛집이 모두 나와요.')).toBeTruthy();
    expect(mockedList).toHaveBeenCalledTimes(1);
  });

  it('🔴 동을 바꾼 뒤 새 목록이 오기 전에는 옛 동의 카드가 비치지 않는다 — 불러오는 중이라고 말한다', async () => {
    let resolveSecond: (value: { items: RestaurantItem[]; hasNext: boolean }) => void = () => {};
    mockedList.mockImplementationOnce(async () => ({ items: BY_AREA['해운대구/우동'], hasNext: false }));
    mockedList.mockImplementationOnce(() => new Promise((resolve) => { resolveSecond = resolve; }));
    mount();
    fireEvent.press(await screen.findByText('해운대구'));
    fireEvent.press(await screen.findByText('우동'));
    expect(await screen.findByText('카드:가식당')).toBeTruthy();

    fireEvent.press(screen.getByText('중동'));

    expect(await screen.findByText('맛집을 불러오고 있어요')).toBeTruthy();
    expect(screen.queryByText('카드:가식당')).toBeNull();
    resolveSecond({ items: BY_AREA['해운대구/중동'], hasNext: false });
    expect(await screen.findByText('카드:다식당')).toBeTruthy();
  });
});

describe('실패와 빈 상태 — 평소에 안 보이는 자리', () => {
  it('구·동 목록을 못 받으면 이유와 「다시 시도」가 나오고, 다시 시도하면 칩이 나온다', async () => {
    mockedAreas.mockRejectedValueOnce(new Error('서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.'));
    mount();

    expect(await screen.findByText('서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.')).toBeTruthy();
    fireEvent.press(screen.getByText('다시 시도'));

    expect(await screen.findByText('먼저 구를 골라 주세요.')).toBeTruthy();
    expect(mockedAreas).toHaveBeenCalledTimes(2);
  });

  it('서버에 구·동이 하나도 없으면 «없다»고 말한다 — 빈 화면으로 두지 않는다', async () => {
    mockedAreas.mockResolvedValue([]);
    mount();

    expect(await screen.findByText('이곳에는 인증 맛집이 없어요.')).toBeTruthy();
  });

  it('목록을 못 받으면 칩은 그대로 두고 그 자리에서 다시 시도한다', async () => {
    mockedList.mockRejectedValueOnce(new Error('서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.'));
    mount();
    fireEvent.press(await screen.findByText('해운대구'));
    fireEvent.press(await screen.findByText('우동'));

    expect(await screen.findByText('서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.')).toBeTruthy();
    expect(screen.getByText('✓ 우동')).toBeTruthy();
    fireEvent.press(screen.getByText('다시 시도'));

    expect(await screen.findByText('카드:가식당')).toBeTruthy();
    expect(mockedList).toHaveBeenCalledTimes(2);
  });

  it('고른 동에 맛집이 없으면 «없다»고 말한다', async () => {
    mockedList.mockResolvedValue({ items: [], hasNext: false });
    mount();
    fireEvent.press(await screen.findByText('해운대구'));
    fireEvent.press(await screen.findByText('우동'));

    expect(await screen.findByText('이곳에는 인증 맛집이 없어요.')).toBeTruthy();
  });
});

describe('출처 배지', () => {
  it('인증한 목록마다 작은 알약 하나 — 고정 순서(부산시 맛집 → 모범음식점 → 택슐랭)', () => {
    render(<OnboardingPreferencesProvider><RestaurantSourceBadges sources={['TAXI_DRIVER_PICK', 'BUSAN_FOOD_INFO']} /></OnboardingPreferencesProvider>);

    expect(screen.getByText('부산시 맛집')).toBeTruthy();
    expect(screen.getByText('택슐랭')).toBeTruthy();
    expect(screen.queryByText('모범음식점')).toBeNull();
  });

  it('모르는 낱말뿐이거나 비었으면 아무것도 안 그린다', () => {
    const view = render(<OnboardingPreferencesProvider><RestaurantSourceBadges sources={['MICHELIN']} /></OnboardingPreferencesProvider>);
    expect(view.toJSON()).toBeNull();
    const empty = render(<OnboardingPreferencesProvider><RestaurantSourceBadges sources={undefined} /></OnboardingPreferencesProvider>);
    expect(empty.toJSON()).toBeNull();
  });
});
