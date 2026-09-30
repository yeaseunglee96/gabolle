// 웹 장소 상세에서 대표 사진 말고는 볼 수 없던 것 — S15P21E201-1900.
// 앱은 옆으로 밀어 넘기지만 웹(마우스)은 밀 수 없어, 두 장 이상인 장소도 첫 장만 보였다.
import { act, fireEvent, render, screen } from '@testing-library/react-native';
import { Platform } from 'react-native';

import { PlacePhotoGallery } from '@/components/PlacePhotoGallery';

jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko }) }));

const urls = ['https://a/1.jpg', 'https://a/2.jpg', 'https://a/3.jpg'];
const original = Platform.OS;
afterEach(() => { Object.defineProperty(Platform, 'OS', { get: () => original, configurable: true }); });
const asWeb = () => Object.defineProperty(Platform, 'OS', { get: () => 'web', configurable: true });

describe('웹에서는 화살표로 사진을 넘긴다', () => {
  it('첫 장에서는 「다음」만 있고, 누르면 2/3 이 되며 「이전」이 생긴다', () => {
    asWeb();
    const seen: number[] = [];
    render(<PlacePhotoGallery urls={urls} onIndexChange={(i) => seen.push(i)} />);
    expect(screen.queryByTestId('place-photo-prev')).toBeNull();
    act(() => { fireEvent.press(screen.getByTestId('place-photo-next')); });
    expect(screen.getByTestId('place-photo-counter').props.children).toBe('2/3');
    expect(screen.getByTestId('place-photo-prev')).toBeTruthy();
    expect(seen).toEqual([1]); // 화면이 지금 사진의 출처를 바꿔 달 수 있게 몇 번째인지 올린다
  });

  it('마지막 장에서는 「다음」이 없다', () => {
    asWeb();
    render(<PlacePhotoGallery urls={urls} />);
    act(() => { fireEvent.press(screen.getByTestId('place-photo-next')); });
    act(() => { fireEvent.press(screen.getByTestId('place-photo-next')); });
    expect(screen.getByTestId('place-photo-counter').props.children).toBe('3/3');
    expect(screen.queryByTestId('place-photo-next')).toBeNull();
  });
});

it('앱에서는 화살표를 그리지 않는다 — 밀어서 넘긴다', () => {
  Object.defineProperty(Platform, 'OS', { get: () => 'android', configurable: true });
  render(<PlacePhotoGallery urls={urls} />);
  expect(screen.queryByTestId('place-photo-next')).toBeNull();
});
