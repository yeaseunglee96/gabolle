import { render } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { PlaceVisual } from '../PlaceVisual';

// — 출처 표기는 관광공사 공공누리 제1유형의 이용 조건이다.
// 주석이나 사람 기억이 아니라 여기서 지킨다 — 사진을 그리는 코드는 앞으로도 바뀔 텐데
// 그때 표기가 조용히 빠지면 아무도 모른 채 라이선스를 어기게 된다.
const mount = (element: React.ReactElement) =>
  render(<OnboardingPreferencesProvider>{element}</OnboardingPreferencesProvider>);

describe('장소 카드 사진과 출처', () => {
  it('서버 사진을 그리면 출처 문구도 같이 그린다', () => {
    const view = mount(
      <PlaceVisual
        name="해운대해수욕장"
        address="부산광역시 해운대구"
        photoUrl="https://example.test/haeundae.jpg"
        photoSource="한국관광공사 관광사진갤러리"
      />,
    );
    expect(view.getByText('사진 제공: 한국관광공사 관광사진갤러리')).toBeTruthy();
  });

  it('🔴 출처가 없으면 서버 사진을 아예 쓰지 않는다 — 표기 없는 사진이 나갈 길을 막는다', () => {
    const view = mount(
      <PlaceVisual name="어떤 장소" address="부산광역시" photoUrl="https://example.test/unknown.jpg" />,
    );
    expect(view.queryByLabelText('어떤 장소 장소 사진')).toBeNull();
    expect(view.getByLabelText('어떤 장소 장소 이미지 준비 중')).toBeTruthy();
  });

  it('서버 사진이 없으면 예전처럼 이름으로 고른 번들 사진을 쓴다', () => {
    const view = mount(<PlaceVisual name="해운대해수욕장" address="부산광역시 해운대구" />);
    expect(view.getByLabelText('해운대해수욕장 장소 사진')).toBeTruthy();
  });
});
