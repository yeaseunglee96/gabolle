// 범위 토글 — 알약 하나가 옮겨 다니는가.
//
// 🔴 「고른 쪽에 배경을 켠다」로 되돌아가기 쉬운 자리다. 그렇게 해도 화면은 그럴듯해 보이고
//    타입도 안 잡는다. 그래서 「주황 바탕이 화면에 하나뿐」을 시험으로 박는다 — 둘이 되면
//    켜고 끄기로 돌아간 것이다.
import { fireEvent, render, screen } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { color } from '@/design/tokens';
import { ScopeSwitch } from '@/discovery/ScopeSwitch';

const OPTIONS = [
  { value: 'nearby', label: '내 근처' },
  { value: 'all', label: '부산 전체' },
] as const;

type Scope = (typeof OPTIONS)[number]['value'];

function mount(value: Scope, onChange: (next: Scope) => void = () => {}) {
  return render(
    <OnboardingPreferencesProvider>
      <ScopeSwitch options={OPTIONS} value={value} onChange={onChange} />
    </OnboardingPreferencesProvider>,
  );
}

/**
 * 주황 바탕을 가진 자리가 몇 개인가. 켜고 끄기로 돌아가면 이 수가 늘거나 0이 된다.
 *
 * 🔴 실제로 그려지는 것만 센다. 부품 껍데기까지 세면 알약 하나가 셋으로 잡힌다 —
 *    Animated.View 가 속 View 를 감싸면서 같은 style 이 여러 겹에 걸리기 때문이다.
 */
function orangeCount(view: ReturnType<typeof mount>) {
  return view.UNSAFE_root.findAll((node) => {
    if (typeof node.type !== 'string') return false;
    const style = node.props?.style;
    const flat = Array.isArray(style) ? Object.assign({}, ...style.flat(Infinity).filter(Boolean)) : style;
    return Boolean(flat && flat.backgroundColor === color.brand.orange);
  }).length;
}

describe('범위 토글', () => {
  it('고른 쪽에 ✓ 가 붙는다', () => {
    mount('all');
    expect(screen.getByText(/✓ 부산 전체/)).toBeTruthy();
    expect(screen.queryByText(/✓ 내 근처/)).toBeNull();
  });

  it('반대쪽을 누르면 그 값을 알린다', () => {
    const calls: Scope[] = [];
    mount('all', (next) => calls.push(next));
    fireEvent.press(screen.getByText(/내 근처/));
    expect(calls).toEqual(['nearby']);
  });

  it('🔴 주황 바탕은 어느 쪽을 골라도 하나뿐이다 — 알약이 옮겨 다닌다', () => {
    // 자리를 재기 전에는 안 그리므로, 재었다고 알려 준 뒤에 센다.
    for (const value of ['nearby', 'all'] as const) {
      const view = mount(value);
      // 역할 이름으로는 못 찾는다(이 시험 도구가 tablist 를 안 다룬다) — 속성으로 집는다.
      const track = view.UNSAFE_root.findAll((node) => node.props?.accessibilityRole === 'tablist')[0];
      fireEvent(track, 'layout', { nativeEvent: { layout: { width: 232, height: 52 } } });
      expect(orangeCount(view)).toBe(1);
    }
  });

  it('세는 방법 자체가 살아 있다 — 재기 전에는 알약이 없다', () => {
    // 이 줄이 없으면 orangeCount 가 늘 1을 돌려줘도 위 시험이 통과한다.
    expect(orangeCount(mount('all'))).toBe(0);
  });
});
