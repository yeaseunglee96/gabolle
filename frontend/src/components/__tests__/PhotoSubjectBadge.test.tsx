import { render } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { PhotoSubjectBadge } from '../PhotoSubjectBadge';
import { PlaceVisual } from '../PlaceVisual';

// — 사진이 무엇을 찍은 것인가를 화면이 말하는지 잰다.

const mount = (element: React.ReactElement) =>
  render(<OnboardingPreferencesProvider>{element}</OnboardingPreferencesProvider>);

describe('사진이 무엇을 찍은 것인지 말하는 표', () => {
  it('그 장소가 든 건물을 찍은 사진이면 말한다', () => {
    const view = mount(<PhotoSubjectBadge photoSubject="VENUE" />);
    expect(view.getByText('행사장 사진')).toBeTruthy();
  });

  it('🔴 그 장소를 직접 찍은 사진에는 아무 말도 안 한다', () => {
    const view = mount(<PhotoSubjectBadge photoSubject="SELF" />);
    expect(view.queryByText('행사장 사진')).toBeNull();
  });

  it('🔴 서버가 그 칸을 안 줘도 안 깨진다 — 배포 순서를 안 탄다', () => {
    expect(mount(<PhotoSubjectBadge />).queryByText('행사장 사진')).toBeNull();
    expect(mount(<PhotoSubjectBadge photoSubject={null} />).queryByText('행사장 사진')).toBeNull();
  });
});

describe('장소 카드가 그 표를 실제로 얹는가', () => {
  // 부품만 맞고 부르는 쪽이 칸을 안 넘기면 화면에는 아무것도 안 나온다.
  // 그게 정확히 이 티켓의 결함이었다 — 도우미는 처음부터 있었는데 부르는 쪽이 칸을
  // 안 넘겨서 늘 빈 값이 나왔다. 그래서 부품이 아니라 카드에서 잰다.
  it('행사장 사진이면 카드에도 표가 뜬다', () => {
    const view = mount(
      <PlaceVisual
        name="광안리해수욕장"
        address="부산광역시 수영구"
        photoUrl="https://example.test/gwangalli.jpg"
        photoSource="한국관광공사 관광사진갤러리"
        photoSubject="VENUE"
      />,
    );
    expect(view.getByText('행사장 사진')).toBeTruthy();
    // 출처 표기는 그대로 있어야 한다 — 뱃지를 더하면서 그걸 밀어내면 라이선스를 어긴다.
    expect(view.getByText('사진: 한국관광공사 관광사진갤러리')).toBeTruthy();
  });

  it('그 장소를 찍은 사진이면 카드에 표가 없다', () => {
    const view = mount(
      <PlaceVisual
        name="광안리해수욕장"
        address="부산광역시 수영구"
        photoUrl="https://example.test/gwangalli.jpg"
        photoSource="한국관광공사 관광사진갤러리"
        photoSubject="SELF"
      />,
    );
    expect(view.queryByText('행사장 사진')).toBeNull();
    expect(view.getByText('사진: 한국관광공사 관광사진갤러리')).toBeTruthy();
  });
});
