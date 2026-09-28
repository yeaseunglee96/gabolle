// 여러 장 사진 넘기기 — S15P21E201-1787(QA).
//
// 전에는 피드 목록 커버가 첫 장만 그리고 점은 장수만 알렸다. 넘기면 점이 따라가야 «몇 장 중 몇 번째» 가 된다.
import { fireEvent, render } from '@testing-library/react-native';
import { ScrollView } from 'react-native';

import { Image, StyleSheet } from 'react-native';

import { FRAME_RATIO, PhotoCarousel, photoFrameRatio, photoIndexAt } from '@/social/PhotoCarousel';

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

describe('첫 사진 비율로 맞추는 틀(상세)', () => {
  it('가로로 긴 사진은 그 비율이다 — 정사각 틀에 넣어 위아래가 비지 않는다', () => {
    expect(photoFrameRatio(1600, 1000)).toBeCloseTo(1.6);
  });

  it('너무 길거나 높으면 1.91:1 ~ 4:5 에서 자른다', () => {
    expect(photoFrameRatio(4000, 1000)).toBe(FRAME_RATIO.max);
    expect(photoFrameRatio(1000, 3000)).toBe(FRAME_RATIO.min);
  });

  it('크기를 모르면 정하지 않는다', () => {
    expect(photoFrameRatio(0, 0)).toBeNull();
  });

  it('첫 사진을 재면 틀이 그 비율이 된다', async () => {
    const getSize = jest.spyOn(Image, 'getSize').mockImplementation((_uri, ok) => { ok(1600, 1000); return Promise.resolve({ width: 1600, height: 1000 }) as never; });
    const view = render(<PhotoCarousel urls={urls} fitFirstPhoto style={{ aspectRatio: 1 }} />);
    const frame = view.UNSAFE_getByType(ScrollView).parent!;
    expect(getSize).toHaveBeenCalledWith(urls[0], expect.any(Function), expect.any(Function));
    expect(StyleSheet.flatten(frame.props.style).aspectRatio).toBeCloseTo(1.6);
    getSize.mockRestore();
  });

  it('맞추라고 안 하면 재지 않는다 — 피드 목록 커버는 크기가 정해져 있다', () => {
    const getSize = jest.spyOn(Image, 'getSize');
    render(<PhotoCarousel urls={urls} style={{ height: 300 }} />);
    expect(getSize).not.toHaveBeenCalled();
    getSize.mockRestore();
  });
});
