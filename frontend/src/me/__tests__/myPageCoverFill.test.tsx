// 넓은 화면 마이페이지 커버가 폭을 끝까지 채운다 (S15P21E201-1951).
// 🔴 1920 폭에서 커버 사진이 1536 에서 끊기고 오른쪽이 회색이었다 — 기본 커버 그림이 1536px 이라,
//    웹의 Image 는 폭을 따로 받지 않으면 그림 본래 폭으로 그린다. absoluteFill(위치만)로는 모자란다.
import { render } from '@testing-library/react-native';
import { Image, StyleSheet } from 'react-native';

import { MyPageCover } from '@/me/MyPageCover';

const tx = (ko: string) => ko;

it.each([null, 'https://example.test/cover.jpg'])('커버 사진(%s)은 폭·높이 100%% 로 그린다', (coverUri) => {
  const { UNSAFE_getAllByType } = render(
    <MyPageCover name="여행자" email={null} tripCount={null} avatarUri={null} coverUri={coverUri} counts={[]} actions={null} eyebrow={null} tx={tx} />,
  );
  const cover = UNSAFE_getAllByType(Image)[0];
  const style = StyleSheet.flatten(cover.props.style);
  expect(style.width).toBe('100%');
  expect(style.height).toBe('100%');
});
