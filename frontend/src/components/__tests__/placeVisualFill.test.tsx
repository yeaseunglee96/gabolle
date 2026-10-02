// 칸을 채우라는 사진 틀은 기본 4:3 비율을 버린다 (S15P21E201-1952).
//
// 데스크톱 일정 카드는 정사각 칸에 사진을 「폭 100% · 높이 100%」로 넣는다. 그런데 틀의 기본값 4:3 비율이
// 남아 있으면 네이티브는 높이에 맞춰 폭을 4/3 배로 늘리고, 칸이 가운데 정렬이라 양쪽이 잘린다.
// 갤럭시 탭 앱에서 출처 띠가 「진: 한국관광공사」로 앞 글자가 잘려 보였다(웹은 비율보다 높이를 따라 멀쩡했다).
// 출처는 이용 조건이라 잘리면 안 된다.
import { StyleSheet } from 'react-native';
import { render } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { PlaceVisual } from '../PlaceVisual';

const frameOf = (style?: object) => {
  const view = render(
    <OnboardingPreferencesProvider>
      <PlaceVisual name="해운대해수욕장" address={null} photoUrl="https://example.test/a.jpg" photoSource="한국관광공사 관광사진갤러리" style={style} />
    </OnboardingPreferencesProvider>,
  );
  return StyleSheet.flatten(view.getByTestId('place-visual-frame').props.style);
};

describe('사진 틀 비율', () => {
  it('아무 것도 안 주면 지금처럼 4:3', () => {
    expect(frameOf().aspectRatio).toBeCloseTo(4 / 3);
  });

  it('🔴 높이를 주면(칸 채우기) 4:3 을 버린다 — 높이와 비율이 겹치면 폭이 칸보다 넓어져 잘린다', () => {
    expect(frameOf({ width: '100%', height: '100%' }).aspectRatio).toBeUndefined();
  });

  it('비율을 직접 주면 그 비율을 쓴다', () => {
    expect(frameOf({ aspectRatio: 1 }).aspectRatio).toBe(1);
  });
});
