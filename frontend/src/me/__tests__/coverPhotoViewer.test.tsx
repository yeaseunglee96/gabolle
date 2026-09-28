// 프로필 커버를 눌러 크게 본다 — S15P21E201-1802.
//
// 🔴 이 앱에는 사진을 크게 보는 길이 한 곳도 없었다. PhotoGrid 에 `onPressPhoto` 자리는
//    있는데 그것을 넘기는 화면이 하나도 없었다. 커버도 그냥 <Image> 라 눌러도 아무 일이
//    없었는데, 크게 생긴 사진은 사람이 누른다.
import type { ReactNode } from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import { SafeAreaProvider, initialWindowMetrics } from 'react-native-safe-area-context';

import { ProfileCard } from '@/me/ProfileCard';

jest.mock('@/layout/useLayout', () => ({
  useLayout: () => ({ isLandscape: false, kind: 'phone', desktop: false, width: 390, height: 844 }),
}));

const wrapper = ({ children }: { children: ReactNode }) => (
  <SafeAreaProvider initialMetrics={initialWindowMetrics ?? { frame: { x: 0, y: 0, width: 390, height: 844 }, insets: { top: 47, left: 0, right: 0, bottom: 34 } }}>{children}</SafeAreaProvider>
);
const tx = (ko: string) => ko;
const card = (coverUri: string | null) => (
  <ProfileCard name="여행자" email="a@b.c" avatarUri={null} coverUri={coverUri} counts={[]} actions={null} tx={tx as never} />
);

describe('프로필 커버 사진', () => {
  it('올린 커버가 있으면 눌러서 크게 본다', () => {
    render(card('https://example.test/cover.jpg'), { wrapper });
    fireEvent.press(screen.getByLabelText('배경 사진 크게 보기'));
    // 창이 열리면 사진과 닫기 단추가 생긴다.
    expect(screen.getAllByLabelText('사진 닫기').length).toBeGreaterThan(0);
  });

  it('크게 본 창을 닫을 수 있다', () => {
    render(card('https://example.test/cover.jpg'), { wrapper });
    fireEvent.press(screen.getByLabelText('배경 사진 크게 보기'));
    fireEvent.press(screen.getAllByLabelText('사진 닫기')[0]);
    expect(screen.queryByLabelText('사진 닫기')).toBeNull();
  });

  it('🔴 기본 부산 사진은 누를 수 없다 — 크게 볼 「내 사진」이 아니다', () => {
    render(card(null), { wrapper });
    expect(screen.queryByLabelText('배경 사진 크게 보기')).toBeNull();
  });

  it('창을 안 열었으면 사진 창이 없다 — 닫힌 채로도 그려지면 뒤가 가려진다', () => {
    render(card('https://example.test/cover.jpg'), { wrapper });
    expect(screen.queryByLabelText('사진 닫기')).toBeNull();
  });
});
