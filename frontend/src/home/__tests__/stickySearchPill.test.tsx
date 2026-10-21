// 따라오는 검색 알약(S15P21E201-1931) — 언제 뜨는가. 큰 검색창이 아직 보이는데 알약까지 뜨면 같은 검색창이 둘이다.
import { fireEvent, render } from '@testing-library/react-native';

import { SearchPillButton, shouldShowStickySearch } from '../StickySearchPill';
import { collapseProgress, searchHandleNow, setSearchHandle } from '../stickySearchStore';

jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko, language: 'ko' }) }));

describe('shouldShowStickySearch', () => {
  it('큰 검색창 아래 끝을 지나야 뜬다', () => {
    expect(shouldShowStickySearch(0, 420)).toBe(false);
    expect(shouldShowStickySearch(420, 420)).toBe(false);
    expect(shouldShowStickySearch(421, 420)).toBe(true);
  });
  it('검색창 자리를 아직 못 쟀으면 띄우지 않는다', () => {
    expect(shouldShowStickySearch(900, null)).toBe(false);
    expect(shouldShowStickySearch(900, 0)).toBe(false);
  });
});

describe('collapseProgress — 큰 검색창이 줄어드는 정도', () => {
  it('검색창 아래 끝 앞 140 에서 0→1, 자리를 못 쟀으면 0', () => {
    expect(collapseProgress(0, 600)).toBe(0);
    expect(collapseProgress(460, 600)).toBe(0);
    expect(collapseProgress(530, 600)).toBeCloseTo(0.5);
    expect(collapseProgress(600, 600)).toBe(1);
    expect(collapseProgress(900, 600)).toBe(1);
    expect(collapseProgress(900, null)).toBe(0);
  });
});

describe('SearchPillButton', () => {
  it('누르면 펼치기를 부른다', () => {
    const onPress = jest.fn();
    const view = render(<SearchPillButton onPress={onPress} />);
    fireEvent.press(view.getByTestId('sticky-search'));
    expect(onPress).toHaveBeenCalledTimes(1);
  });

  it('🔴 큰 검색창에서 고른 값을 쓴다 — ㉖ 시안 「날짜 · 성인 2」', () => {
    const view = render(<SearchPillButton onPress={() => {}} labels={{ origin: null, dates: '10.3(토) – 10.5(월) · 3일', people: '성인 2' }} />);
    expect(view.getByText('어디서 출발')).toBeTruthy();
    expect(view.getByText('10.3(토) – 10.5(월) · 3일')).toBeTruthy();
    expect(view.getByText('성인 2')).toBeTruthy();
  });

  it('안 골랐으면 빈 칸 안내', () => {
    const view = render(<SearchPillButton onPress={() => {}} />);
    expect(view.getByText('날짜 추가')).toBeTruthy();
    expect(view.getByText('인원')).toBeTruthy();
  });
});

describe('setSearchHandle — 알약 글을 따로 적어도 나머지는 그대로', () => {
  it('labels 를 빼고 적으면 전에 적은 글이 남는다', () => {
    const open = () => {};
    setSearchHandle({ active: true, collapsed: false, open, labels: { origin: '부산역', dates: null, people: '성인 2' } });
    setSearchHandle({ active: true, collapsed: true, open });
    expect(searchHandleNow()).toEqual({ active: true, collapsed: true, open, labels: { origin: '부산역', dates: null, people: '성인 2' }, expanded: false });
    setSearchHandle({ active: false, collapsed: false, open: null, labels: null });
  });
});
