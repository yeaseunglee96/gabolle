// 사진 공용 부품(S15P21E201-1975) — 오래 쓰면 메모리가 차 사진이 회색으로 남던 것.
// expo-image 로 그리고(화면 크기로 줄여 메모리에 올림·캐시), 실패하면 한 번 다시, 그래도 실패면 빈 판.
import { act, render, screen } from '@testing-library/react-native';
import { AppImage, contentFitFor } from '../AppImage';

jest.mock('expo-image', () => {
  const { View } = require('react-native');
  const Image = (props: Record<string, unknown>) => <View {...props} testID="expo-image" />;
  return { Image };
});

describe('AppImage', () => {
  it('expo-image 로 그리고 메모리·디스크 캐시와 재활용 키를 준다', () => {
    render(<AppImage source={{ uri: 'https://x/a.jpg' }} resizeMode="cover" style={{ width: 10, height: 10 }} />);
    const img = screen.getByTestId('expo-image');
    expect(img.props.cachePolicy).toBe('memory-disk');
    expect(img.props.recyclingKey).toBe('https://x/a.jpg');
    expect(img.props.contentFit).toBe('cover');
  });

  it('resizeMode 를 contentFit 으로 옮긴다', () => {
    expect(contentFitFor('contain')).toBe('contain');
    expect(contentFitFor('stretch')).toBe('fill');
    expect(contentFitFor('center')).toBe('scale-down');
    expect(contentFitFor(undefined)).toBe('cover');
  });

  it('한 번 실패하면 다시 불러 보고, 두 번 실패하면 이름 있는 빈 판을 그린다', () => {
    const onError = jest.fn();
    render(<AppImage source={{ uri: 'https://x/b.jpg' }} fallbackLabel="감천문화마을" onError={onError} style={{ width: 10, height: 10 }} />);
    act(() => { (screen.getByTestId('expo-image').props.onError as () => void)(); });
    expect(screen.getByTestId('expo-image')).toBeTruthy();
    act(() => { (screen.getByTestId('expo-image').props.onError as () => void)(); });
    expect(screen.queryByTestId('expo-image')).toBeNull();
    expect(screen.getByText('감천문화마을')).toBeTruthy();
    expect(onError).toHaveBeenCalledTimes(1);
  });

  it('retries=0 이면 첫 실패에 바로 빈 판(부르는 쪽이 따로 재시도할 때)', () => {
    render(<AppImage source={{ uri: 'https://x/c.jpg' }} retries={0} style={{ width: 10, height: 10 }} />);
    act(() => { (screen.getByTestId('expo-image').props.onError as () => void)(); });
    expect(screen.queryByTestId('expo-image')).toBeNull();
    expect(screen.getByTestId('app-image-fallback')).toBeTruthy();
  });
});
