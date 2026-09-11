// 갤럭시 폴드8 대응. 이 훅이 이 셸에서 제일 중요하다 —
// 폴드 기기는 앱이 켜진 채로 화면비가 접힘(좁고 길쭉) ↔ 펼침(거의 정사각)으로 바뀌는데,
// Figma 에는 폰(390) 과 웹(1440) 만 있고 그 사이 폭에 대한 디자인이 없다.
// useWindowDimensions 는 회전·접힘 때마다 다시 렌더링을 트리거하므로 실시간으로 따라간다.
import { useWindowDimensions } from 'react-native';

export type LayoutKind = 'phone' | 'tablet';

export type Layout = {
  kind: LayoutKind;
  width: number;
  height: number;
  isLandscape: boolean;
};

// 최단변 기준 600dp. 폴드8 을 펼치면 최단변이 이 값을 넘어간다.
const TABLET_MIN_SHORT_SIDE = 600;

export function useLayout(): Layout {
  const { width, height } = useWindowDimensions();

  return {
    kind: Math.min(width, height) >= TABLET_MIN_SHORT_SIDE ? 'tablet' : 'phone',
    width,
    height,
    isLandscape: width > height,
  };
}
