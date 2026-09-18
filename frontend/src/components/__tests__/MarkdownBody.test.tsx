import { render } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { MarkdownBody } from '../MarkdownBody';

const mount = (source: string) =>
  render(<OnboardingPreferencesProvider><MarkdownBody source={source} /></OnboardingPreferencesProvider>);

describe('마크다운 글 그리기', () => {
  it('굵게·기울임이 글자로 남는다 — 표시 기호는 사라진다', () => {
    const view = mount('**해운대**는 *넓어요*');
    expect(view.getByText('해운대')).toBeTruthy();
    expect(view.getByText('넓어요')).toBeTruthy();
    expect(view.queryByText('**해운대**')).toBeNull();
  });

  it('목록은 글머리와 함께 그린다 — 낭독기가 읽을 수 있게 글자로 둔다', () => {
    const view = mount('- 첫째\n- 둘째');
    expect(view.getByText('첫째')).toBeTruthy();
    expect(view.getAllByText('•')).toHaveLength(2);
  });

  it('번호 목록은 번호를 그린다', () => {
    const view = mount('1. 하나\n2. 둘');
    expect(view.getByText('1.')).toBeTruthy();
    expect(view.getByText('2.')).toBeTruthy();
  });

  it('🔴 글에 HTML 을 써도 태그가 살아나지 않는다 — 글자 그대로 남는다', () => {
    const view = mount('<script>alert(1)</script> 안녕');
    const drawn = JSON.stringify(view.toJSON());
    // 글자로 남는다 — 그려지는 것이 되지 않는다
    expect(drawn).toContain('alert(1)');
    expect(drawn).toContain('안녕');
  });

  it('마크다운을 안 쓴 기존 글이 그대로 보인다', () => {
    const view = mount('오늘 해운대에 다녀왔어요.');
    expect(view.getByText('오늘 해운대에 다녀왔어요.')).toBeTruthy();
  });

  it('빈 글이면 아무것도 안 그린다', () => {
    const view = mount('   ');
    expect(view.toJSON()).toBeNull();
  });
});
