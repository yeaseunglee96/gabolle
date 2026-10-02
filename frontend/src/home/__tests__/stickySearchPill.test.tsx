// 따라오는 검색 알약(S15P21E201-1931) — 언제 뜨는가. 큰 검색창이 아직 보이는데 알약까지 뜨면 같은 검색창이 둘이다.
import { fireEvent, render } from '@testing-library/react-native';

import { shouldShowStickySearch, StickySearchPill } from '../StickySearchPill';

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

describe('StickySearchPill', () => {
  it('누르면 펼치기를 부른다', () => {
    const onPress = jest.fn();
    const view = render(<StickySearchPill visible reduceMotion onPress={onPress} />);
    fireEvent.press(view.getByTestId('sticky-search'));
    expect(onPress).toHaveBeenCalledTimes(1);
  });
});
