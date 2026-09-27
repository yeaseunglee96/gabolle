// 여러 장 사진 넘기기 — S15P21E201-1787(QA).
//
// 전에는 피드 목록 커버가 첫 장만 그리고 점은 장수만 알렸다. 넘기면 점이 따라가야 «몇 장 중 몇 번째» 가 된다.
import { fireEvent, render } from '@testing-library/react-native';
import { ScrollView } from 'react-native';

import { PhotoCarousel, photoIndexAt } from '@/social/PhotoCarousel';

jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko }) }));

const urls = ['https://example.test/1.jpg', 'https://example.test/2.jpg', 'https://example.test/3.jpg'];

describe('지금 보이는 사진 번호', () => {
  it('넘긴 거리로 번호를 구한다', () => {
    expect(photoIndexAt(0, 300, 3)).toBe(0);
    expect(photoIndexAt(300, 300, 3)).toBe(1);
    expect(photoIndexAt(590, 300, 3)).toBe(2);
  });

  it('끝을 넘겨 튕겨도 범위 안에 둔다', () => {
    expect(photoIndexAt(-40, 300, 3)).toBe(0);
    expect(photoIndexAt(1000, 300, 3)).toBe(2);
  });

  it('폭을 아직 모르면 첫 장이다', () => {
    expect(photoIndexAt(300, 0, 3)).toBe(0);
  });
});

describe('사진 줄', () => {
  function layout(view: ReturnType<typeof render>, width: number) {
    fireEvent(view.UNSAFE_getByType(ScrollView).parent!, 'layout', { nativeEvent: { layout: { width, height: 300, x: 0, y: 0 } } });
  }

  it('여러 장이면 넘기면서 점이 따라간다 — 몇 장 중 몇 번째', () => {
    const view = render(<PhotoCarousel urls={urls} />);
    layout(view, 300);
    expect(view.getByLabelText('사진 1/3')).toBeTruthy();

    fireEvent.scroll(view.UNSAFE_getByType(ScrollView), { nativeEvent: { contentOffset: { x: 300, y: 0 }, contentSize: { width: 900, height: 300 }, layoutMeasurement: { width: 300, height: 300 } } });

    expect(view.getByLabelText('사진 2/3')).toBeTruthy();
  });

  it('한 장이면 넘길 것도 점도 없다', () => {
    const view = render(<PhotoCarousel urls={urls.slice(0, 1)} />);
    expect(view.UNSAFE_queryByType(ScrollView)).toBeNull();
    expect(view.queryByLabelText('사진 1/1')).toBeNull();
  });

  it('사진을 누르면 부르는 쪽 일을 한다 — 목록에서는 상세로 간다', () => {
    const onPress = jest.fn();
    const view = render(<PhotoCarousel urls={urls} onPressPhoto={onPress} pressLabel="기록 자세히 보기" />);
    fireEvent.press(view.getAllByLabelText('기록 자세히 보기')[1]);
    expect(onPress).toHaveBeenCalledTimes(1);
  });
});
