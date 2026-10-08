// 일정 조정 화면 부품 — S15P21E201-2017.
//
// 🔴 이 시험이 지키는 것:
//    1. 고정 방식 창 — 「시각 고정」을 누르면 TIME, 고정된 곳에만 「고정 풀기」가 있다.
//    2. 머무는 시간 손잡이 — 15분이면 더 못 줄이고, 시각을 모르는 곳에는 손잡이가 없다.
//    3. 「일정 고치기」 입구가 폰·넓은 화면 여행 화면 둘 다에 «늘 보이는 자리»에 있다 — ⋯ 메뉴 안에만 두면 아무도 못 찾았다.
import { fireEvent, render, screen } from '@testing-library/react-native';

import { LockModeSheet } from '@/components/LockModeSheet';
import { StayStepper } from '@/components/StayStepper';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');
const source = (rel: string) => readFileSync(join(__dirname, '..', '..', '..', rel), 'utf8') as string;

jest.setTimeout(20000);

const ko = (korean: string) => korean;

describe('1. 고정 방식 창', () => {
  const mount = (current: 'ORDER' | 'TIME' | null, onPick = jest.fn(), onUnlock = jest.fn()) => render(
    <OnboardingPreferencesProvider>
      <LockModeSheet visible placeName="해운대" current={current} onPick={onPick} onUnlock={onUnlock} onClose={jest.fn()} />
    </OnboardingPreferencesProvider>,
  );

  it('🔴 「시각 고정」을 누르면 TIME 으로 고정한다', async () => {
    const onPick = jest.fn();
    mount(null, onPick);
    fireEvent.press(await screen.findByTestId('lock-mode-TIME', {}, { timeout: 10000 }));
    expect(onPick).toHaveBeenCalledWith('TIME');
  });

  it('고정이 아니면 「고정 풀기」가 없고, 고정이면 있다', async () => {
    const first = mount(null);
    await screen.findByTestId('lock-mode-ORDER', {}, { timeout: 10000 });
    expect(screen.queryByTestId('lock-mode-unlock')).toBeNull();
    first.unmount();
    const onUnlock = jest.fn();
    mount('ORDER', jest.fn(), onUnlock);
    fireEvent.press(await screen.findByTestId('lock-mode-unlock', {}, { timeout: 10000 }));
    expect(onUnlock).toHaveBeenCalled();
  });
});

describe('2. 머무는 시간 손잡이', () => {
  it('🔴 15분이면 줄이기를 못 누른다 — 서버가 거부할 값을 보내지 않는다', () => {
    const onStep = jest.fn();
    render(<StayStepper minutes={15} name="해운대" onStep={onStep} tx={ko} />);
    fireEvent.press(screen.getByTestId('stay-shorten'));
    expect(onStep).not.toHaveBeenCalled();
    fireEvent.press(screen.getByTestId('stay-lengthen'));
    expect(onStep).toHaveBeenCalledWith(1);
    expect(screen.getByText('15분')).toBeTruthy();
  });

  it('시각을 모르는 곳(null)에는 손잡이가 없다', () => {
    render(<StayStepper minutes={null} name="해운대" onStep={jest.fn()} tx={ko} />);
    expect(screen.queryByTestId('stay-stepper')).toBeNull();
  });
});

describe('3. 「일정 고치기」 입구', () => {
  it('🔴 폰 여행 화면 — 일정 바로 위에 입구가 있고 편집 화면(?classic=1)을 연다', () => {
    const mobile = source('src/trip/page/TripPageMobile.tsx');
    expect(mobile).toContain('testID="trip-edit-entry"');
    expect(mobile).toMatch(/testID="trip-edit-open"[^>]*onPress=\{openClassic\}/);
  });

  it('🔴 넓은 화면 — 머리 줄에 「일정 고치기」가 있다', () => {
    const desktop = source('src/trip/page/TripPageDesktop.tsx');
    expect(desktop).toMatch(/testID="trip-edit-open" label=\{tx\('일정 고치기'/);
  });

  it('편집 화면 — 「장소 더하기」가 ⋯ 밖, 늘 보이는 줄에 있다', () => {
    const screenSource = source('app/trips/[id]/itinerary.tsx');
    expect(screenSource).toContain('testID="itinerary-edit-bar"');
    expect(screenSource).toContain('testID="itinerary-add-place"');
  });
});
