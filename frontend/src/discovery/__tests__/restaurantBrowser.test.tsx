// 로컬 탐색 「맛집」·「카페」 갈래의 본문 (S15P21E201-2051, 카페·내 위치로 기본 구는 S15P21E201-2059) — 구 칩 → 동 칩 → 목록의 흐름.
//
// 🔴 이 화면의 결함은 타입 검사도 단위 시험도 안 잡는다(frontend/CLAUDE.md). 그래서 «눌렀을 때 무엇이 나오고 무엇이 사라지는가»를
//    시험으로 박는다 — 특히 구를 바꾼 뒤 옛 구의 목록이 남지 않는 것, 동을 바꾼 첫 그림에 옛 동의 카드가 비치지 않는 것,
//    그리고 내 위치로 미리 고른 구가 사용자가 누른 것을 덮어쓰지 않는 것.
import type { ComponentProps } from 'react';
import { ScrollView, Text } from 'react-native';
import { act, fireEvent, render, screen } from '@testing-library/react-native';

import { gutter } from '@/design/tokens';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { RestaurantBrowser, RestaurantSourceBadges } from '@/discovery/RestaurantBrowser';
import { getLocalAreas, getLocalList, type LocalAreas, type RestaurantGu, type RestaurantItem } from '@/discovery/localRestaurants';

jest.mock('@/discovery/localRestaurants', () => ({
  ...jest.requireActual('@/discovery/localRestaurants'),
  getLocalAreas: jest.fn(),
  getLocalList: jest.fn(),
}));

const mockedAreas = getLocalAreas as jest.MockedFunction<typeof getLocalAreas>;
const mockedList = getLocalList as jest.MockedFunction<typeof getLocalList>;

const AREAS: RestaurantGu[] = [
  { gu: '해운대구', placeCount: 3, dongs: [{ dong: '우동', placeCount: 2 }, { dong: '중동', placeCount: 1 }] },
  { gu: '중구', placeCount: 1, dongs: [{ dong: '남포동', placeCount: 1 }] },
];

const answer = (nearestGu: string | null = null): LocalAreas => ({ areas: AREAS, nearestGu });

function place(placeId: string, nameKo: string, gu: string, dong: string, category = 'FOOD'): RestaurantItem {
  return { placeId, nameKo, nameEn: null, category, address: `${gu} 시험로 1`, lat: 35.1, lng: 129.1, gu, dong, certifiedSources: ['MODEL_RESTAURANT'] };
}

const BY_AREA: Record<string, RestaurantItem[]> = {
  '해운대구/우동': [place('a', '가식당', '해운대구', '우동'), place('b', '나식당', '해운대구', '우동')],
  '해운대구/중동': [place('c', '다식당', '해운대구', '중동')],
  '해운대구/*': [place('a', '가식당', '해운대구', '우동'), place('b', '나식당', '해운대구', '우동'), place('c', '다식당', '해운대구', '중동')],
  '중구/남포동': [place('d', '라식당', '중구', '남포동')],
  '중구/*': [place('d', '라식당', '중구', '남포동')],
};

const HAEUNDAE = { latitude: 35.1631, longitude: 129.1636 };

type Props = Partial<ComponentProps<typeof RestaurantBrowser>>;

function tree(props: Props = {}) {
  return (
    <OnboardingPreferencesProvider>
      <RestaurantBrowser wide={false} renderRows={(items) => <>{items.map((item) => <Text key={item.placeId}>{`카드:${item.nameKo}`}</Text>)}</>} {...props} />
    </OnboardingPreferencesProvider>
  );
}

function mount(props: Props = {}) {
  return render(tree(props));
}

beforeEach(() => {
  mockedAreas.mockReset();
  mockedList.mockReset();
  mockedAreas.mockResolvedValue(answer());
  mockedList.mockImplementation(async (_kind, area) => ({ items: BY_AREA[`${area.gu}/${area.dong ?? '*'}`] ?? [], hasNext: false }));
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
    // 맛집이 기본이다 — 서버에도 맛집으로 묻는다.
    expect(mockedAreas.mock.calls[0][0]).toBe('RESTAURANT');
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
    expect(mockedList.mock.calls[0][0]).toBe('RESTAURANT');
    expect(mockedList.mock.calls[0][1]).toEqual({ gu: '해운대구', dong: '우동' });
    // 어느 곳의 몇 곳인지 한 줄.
    expect(screen.getByText('해운대구 · 우동 · 2곳')).toBeTruthy();
    expect(screen.getByText('✓ 우동')).toBeTruthy();
  });

  it('「전체」를 고르면 동 없이 구 전체를 부른다', async () => {
    mount();
    fireEvent.press(await screen.findByText('해운대구'));
    fireEvent.press(await screen.findByText('전체'));

    expect(await screen.findByText('카드:다식당')).toBeTruthy();
    expect(mockedList.mock.calls[0][1]).toEqual({ gu: '해운대구', dong: null });
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
    mockedAreas.mockResolvedValue({ areas: [], nearestGu: null });
    mount();

    expect(await screen.findByText('이곳에는 맛집이 없어요.')).toBeTruthy();
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

    expect(await screen.findByText('이곳에는 맛집이 없어요.')).toBeTruthy();
  });
});

describe('카페 — 맛집과 같은 틀, 다른 문구와 다른 종류(kind)', () => {
  it('🔴 카페로 서버에 묻고(구·동 목록도, 목록도), 카페 문구로 말한다', async () => {
    mount({ kind: 'CAFE' });
    fireEvent.press(await screen.findByText('해운대구'));

    expect(await screen.findByText('동을 골라 주세요. 「전체」를 누르면 이 구의 카페가 모두 나와요.')).toBeTruthy();
    fireEvent.press(screen.getByText('우동'));
    expect(await screen.findByText('카드:가식당')).toBeTruthy();

    expect(mockedAreas.mock.calls[0][0]).toBe('CAFE');
    expect(mockedList.mock.calls[0][0]).toBe('CAFE');
    expect(mockedList.mock.calls[0][1]).toEqual({ gu: '해운대구', dong: '우동' });
    // 맛집 문구는 어디에도 없다.
    expect(screen.queryByText(/맛집/)).toBeNull();
  });

  it('카페가 하나도 없으면 «카페가 없다»고 말한다 — 맛집이라고 말하지 않는다', async () => {
    mockedAreas.mockResolvedValue({ areas: [], nearestGu: null });
    mount({ kind: 'CAFE' });

    expect(await screen.findByText('이곳에는 카페가 없어요.')).toBeTruthy();
  });

  it('카페 목록을 못 받으면 카페라고 말한다', async () => {
    mockedList.mockRejectedValueOnce('이유를 못 읽는 실패');
    mount({ kind: 'CAFE' });
    fireEvent.press(await screen.findByText('해운대구'));
    fireEvent.press(await screen.findByText('우동'));

    expect(await screen.findByText('카페를 불러오지 못했어요.')).toBeTruthy();
  });
});

describe('🔴 내 위치로 기본 구 (S15P21E201-2059)', () => {
  it('위치가 있고 서버가 가장 가까운 곳의 구를 주면, 그 구와 「전체」가 미리 골라지고 그 구의 목록이 바로 나온다 — 어디서 왔는지도 말한다', async () => {
    mockedAreas.mockResolvedValue(answer('해운대구'));
    mount({ kind: 'CAFE', coords: HAEUNDAE });

    expect(await screen.findByText('카드:가식당')).toBeTruthy();
    // 구 전체(동 없이)를 불렀다 — 사용자는 아직 아무것도 안 눌렀다.
    expect(mockedList).toHaveBeenCalledTimes(1);
    expect(mockedList.mock.calls[0][0]).toBe('CAFE');
    expect(mockedList.mock.calls[0][1]).toEqual({ gu: '해운대구', dong: null });
    expect(screen.getByText('✓ 해운대구')).toBeTruthy();
    expect(screen.getByText('✓ 전체')).toBeTruthy();
    expect(screen.getByText('카드:다식당')).toBeTruthy();
    expect(screen.getByText('해운대구 · 전체 · 3곳')).toBeTruthy();
    expect(screen.getByText('내 위치와 가까운 구(해운대구)를 골랐어요. 다른 구를 눌러도 돼요.')).toBeTruthy();
    // 「먼저 구를 골라 주세요」는 사라진다.
    expect(screen.queryByText('먼저 구를 골라 주세요.')).toBeNull();
    // 서버에는 좌표를 그대로 넘긴다(약 100m 로 줄이는 것은 서버를 부르는 쪽 — getLocalAreas — 의 몫이다).
    expect(mockedAreas.mock.calls[0][1]).toMatchObject({ coords: HAEUNDAE });
  });

  it('🔴 사용자가 다른 구를 누르면 그대로 따른다 — 위치 답이 다시 와도 덮어쓰지 않는다', async () => {
    mockedAreas.mockResolvedValue(answer('해운대구'));
    const view = mount({ coords: HAEUNDAE });
    expect(await screen.findByText('카드:가식당')).toBeTruthy();

    fireEvent.press(screen.getByText('중구'));
    expect(await screen.findByText('남포동')).toBeTruthy();
    expect(screen.queryByText('카드:가식당')).toBeNull();
    // 고른 것이 위치 때문이 아니니 안내 한 줄도 사라진다.
    expect(screen.queryByText(/내 위치와 가까운 구/)).toBeNull();

    // 위치가 조금 움직여 서버를 다시 불렀다(좌표가 달라짐) — 답은 여전히 해운대구지만 사용자가 고른 중구가 그대로다.
    view.rerender(tree({ coords: { latitude: 35.2, longitude: 129.2 } }));
    await act(async () => { await Promise.resolve(); });
    expect(mockedAreas).toHaveBeenCalledTimes(2);
    expect(screen.getByText('✓ 중구')).toBeTruthy();
    expect(screen.queryByText('카드:가식당')).toBeNull();
  });

  it('🔴 서버가 가장 가까운 구를 안 주면(부산 밖·5km 밖) 예전 그대로 «먼저 구를 골라 주세요»다', async () => {
    mockedAreas.mockResolvedValue(answer(null));
    mount({ coords: { latitude: 37.5665, longitude: 126.978 } });

    expect(await screen.findByText('먼저 구를 골라 주세요.')).toBeTruthy();
    expect(screen.queryByText('동 고르기')).toBeNull();
    expect(screen.queryByText(/내 위치와 가까운 구/)).toBeNull();
    expect(mockedList).not.toHaveBeenCalled();
  });

  it('목록에 없는 구를 주면 따르지 않는다 — 서버의 구 목록과 가장 가까운 구가 어긋나도 지어내지 않는다', async () => {
    mockedAreas.mockResolvedValue(answer('없는구'));
    mount({ coords: HAEUNDAE });

    expect(await screen.findByText('먼저 구를 골라 주세요.')).toBeTruthy();
    expect(mockedList).not.toHaveBeenCalled();
  });

  it('위치가 없으면(거부·실패) 위치를 서버에 보내지 않고 예전 그대로다 — 가짜 서버가 구를 줘도 위치의 답으로 읽지 않는다', async () => {
    mockedAreas.mockResolvedValue(answer('해운대구'));
    mount({ coords: null });

    expect(await screen.findByText('먼저 구를 골라 주세요.')).toBeTruthy();
    // 좌표를 안 줬으니 서버도 구를 못 정한다 — 줬다면 옛 답이거나 다른 요청의 것이다.
    expect(mockedAreas.mock.calls[0][1]).toMatchObject({ coords: null });
    expect(screen.queryByText('동 고르기')).toBeNull();
    expect(mockedList).not.toHaveBeenCalled();
  });

  it('🔴 위치가 칩을 그린 뒤에 오면 칩은 그대로 두고(다시 돌지 않는다) 「찾는 중」을 말한 뒤 구를 미리 고른다', async () => {
    const view = mount();
    expect(await screen.findByText('먼저 구를 골라 주세요.')).toBeTruthy();

    let resolveNearest: (value: LocalAreas) => void = () => {};
    mockedAreas.mockImplementationOnce(() => new Promise((resolve) => { resolveNearest = resolve; }));
    view.rerender(tree({ coords: HAEUNDAE }));

    // 두 번째 요청이 오는 동안 칩은 그대로 있고(불러오는 중으로 돌아가지 않는다), 위치를 쓰는 중이라고 말한다.
    expect(await screen.findByText('내 위치로 가까운 구를 찾고 있어요')).toBeTruthy();
    expect(screen.getByText('해운대구')).toBeTruthy();
    expect(screen.queryByText('먼저 구를 골라 주세요.')).toBeNull();

    resolveNearest(answer('해운대구'));
    expect(await screen.findByText('카드:가식당')).toBeTruthy();
    expect(screen.queryByText('내 위치로 가까운 구를 찾고 있어요')).toBeNull();
    expect(mockedAreas).toHaveBeenCalledTimes(2);
    expect(mockedAreas.mock.calls[0][1]).toMatchObject({ coords: null });
    expect(mockedAreas.mock.calls[1][1]).toMatchObject({ coords: HAEUNDAE });
  });

  it('🔴 사용자가 구를 먼저 눌렀으면 늦게 온 위치의 답이 그것을 덮어쓰지 않는다', async () => {
    const view = mount();
    fireEvent.press(await screen.findByText('중구'));
    expect(await screen.findByText('남포동')).toBeTruthy();

    mockedAreas.mockResolvedValueOnce(answer('해운대구'));
    view.rerender(tree({ coords: HAEUNDAE }));
    await act(async () => { await Promise.resolve(); });
    expect(mockedAreas).toHaveBeenCalledTimes(2);

    expect(screen.getByText('✓ 중구')).toBeTruthy();
    expect(screen.queryByText('✓ 해운대구')).toBeNull();
    expect(mockedList).not.toHaveBeenCalled();
  });

  it('위치를 묻는 요청이 실패해도 이미 그린 칩은 그대로 두고 «찾는 중»을 끝낸다', async () => {
    const view = mount();
    expect(await screen.findByText('먼저 구를 골라 주세요.')).toBeTruthy();

    mockedAreas.mockRejectedValueOnce(new Error('서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.'));
    view.rerender(tree({ coords: HAEUNDAE }));

    expect(await screen.findByText('먼저 구를 골라 주세요.')).toBeTruthy();
    expect(screen.getByText('해운대구')).toBeTruthy();
    expect(screen.queryByText('내 위치로 가까운 구를 찾고 있어요')).toBeNull();
    // 칩 대신 오류 화면으로 바꾸지 않는다.
    expect(screen.queryByText('서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.')).toBeNull();
  });
});

describe('고른 칩이 보이게 줄을 옮긴다', () => {
  it('🔴 내 위치로 미리 고른 구가 줄 오른쪽 밖에 있어도(카페는 해운대구가 9곳이라 오른쪽 끝이다) 그 칩이 보이게 줄을 옮긴다', async () => {
    mockedAreas.mockResolvedValue(answer('중구'));
    mount({ coords: HAEUNDAE });
    await screen.findByText('✓ 중구');

    // 구 칩 줄은 첫째 ScrollView 다. 칩이 놓인 자리를 알려 주면(layout) 그 칩의 왼쪽 가장자리가 보이도록 줄을 옮긴다.
    const guRail = screen.UNSAFE_getAllByType(ScrollView)[0];
    const scrollTo = (guRail.instance as unknown as { scrollTo: jest.Mock }).scrollTo;
    scrollTo.mockClear();
    fireEvent(screen.getByText('✓ 중구'), 'layout', { nativeEvent: { layout: { x: 520, y: 0, width: 90, height: 44 } } });

    expect(scrollTo).toHaveBeenCalledWith({ x: 520 - gutter, animated: false });
  });

  it('고르지 않은 칩이 놓여도 줄을 옮기지 않는다 — 보고 있는 자리를 흔들지 않는다', async () => {
    mockedAreas.mockResolvedValue(answer('중구'));
    mount({ coords: HAEUNDAE });
    await screen.findByText('✓ 중구');
    const guRail = screen.UNSAFE_getAllByType(ScrollView)[0];
    const scrollTo = (guRail.instance as unknown as { scrollTo: jest.Mock }).scrollTo;
    scrollTo.mockClear();

    fireEvent(screen.getByText('해운대구'), 'layout', { nativeEvent: { layout: { x: 40, y: 0, width: 90, height: 44 } } });

    expect(scrollTo).not.toHaveBeenCalled();
  });
});

describe('출처 배지', () => {
  it('소개·인증한 목록마다 작은 알약 하나 — 고정 순서(부산시 맛집 → … → 관광공사 → 레드테이블 → 현지인 추천)', () => {
    render(<OnboardingPreferencesProvider><RestaurantSourceBadges sources={['LOCAL_SURVEY', 'TAXI_DRIVER_PICK', 'BUSAN_FOOD_INFO', 'KTO_CAFE', 'REDTABLE_FEATURED']} /></OnboardingPreferencesProvider>);

    expect(screen.getByText('부산시 맛집')).toBeTruthy();
    expect(screen.getByText('택슐랭')).toBeTruthy();
    expect(screen.getByText('관광공사 추천')).toBeTruthy();
    expect(screen.getByText('레드테이블')).toBeTruthy();
    expect(screen.getByText('현지인 추천')).toBeTruthy();
    expect(screen.queryByText('모범음식점')).toBeNull();
    // 레드테이블에 방송·수상 표시가 없으면 방송이라고 말하지 않는다.
    expect(screen.queryByText(/방송/)).toBeNull();
  });

  it('🔴 레드테이블이 방송·수상 이력을 적어 둔 곳은 「레드테이블 · 방송·수상」으로 그린다', () => {
    render(<OnboardingPreferencesProvider><RestaurantSourceBadges sources={['KTO_CAFE', 'REDTABLE_FEATURED']} broadcastOrAward /></OnboardingPreferencesProvider>);

    expect(screen.getByText('레드테이블 · 방송·수상')).toBeTruthy();
    expect(screen.getByText('관광공사 추천')).toBeTruthy();
    expect(screen.queryByText('레드테이블')).toBeNull();
  });

  it('모르는 낱말뿐이거나 비었으면 아무것도 안 그린다', () => {
    const view = render(<OnboardingPreferencesProvider><RestaurantSourceBadges sources={['MICHELIN']} /></OnboardingPreferencesProvider>);
    expect(view.toJSON()).toBeNull();
    const empty = render(<OnboardingPreferencesProvider><RestaurantSourceBadges sources={undefined} /></OnboardingPreferencesProvider>);
    expect(empty.toJSON()).toBeNull();
  });
});
