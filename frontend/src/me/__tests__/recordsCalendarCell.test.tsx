// 마이페이지 기록 달력의 칸 — S15P21E201-1779(고지혁 QA).
//
// 🔴 이 시험이 지키는 것: 날짜가 비좁고, 오늘·고른 날 강조가 겹치고, 강조(사진·점·개수)가 숫자 위에 얹혔다.
//    강조는 숫자 뒤 동그라미 하나 — 고른 날이면 채움, 오늘이면 테두리, 둘 다면 채움만. 기록 표시는 숫자 아래 줄.
import { fireEvent, render, screen } from '@testing-library/react-native';

import { RecordsBrowser } from '@/me/RecordsBrowser';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { calendarCellMarks, todayKey } from '@/me/recordsBrowse';
import type { StoryDto } from '@/social/stories';

const now = new Date();
const pad = (n: number) => String(n).padStart(2, '0');
// 오프셋 없는 ISO — 기기 시간대로 읽혀 CI(UTC)에서도 오늘로 묶인다(recordsBrowse.test.ts 와 같은 규칙).
const localIso = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T10:00:00`;
const story = (id: string): StoryDto => ({
  id, author: { id: 'u', displayName: '나' }, body: '', images: [], visibility: 'PUBLIC',
  publishAt: localIso, createdAt: localIso, updatedAt: localIso, mine: true, published: true,
} as unknown as StoryDto);

describe('달력 칸 표시', () => {
  it('🔴 오늘을 고르면 강조는 채움 하나 — 테두리를 겹쳐 그리지 않는다', () => {
    expect(calendarCellMarks({ selected: true, today: true, count: 0, hasCover: false }).circle).toBe('filled');
    expect(calendarCellMarks({ selected: false, today: true, count: 0, hasCover: false }).circle).toBe('ring');
    expect(calendarCellMarks({ selected: false, today: false, count: 0, hasCover: false }).circle).toBe('none');
  });

  it('기록 표시 — 사진이 있으면 작은 사진, 없으면 점, 둘 이상이면 개수', () => {
    expect(calendarCellMarks({ selected: false, today: false, count: 1, hasCover: true })).toMatchObject({ indicator: 'photo', countLabel: null });
    expect(calendarCellMarks({ selected: false, today: false, count: 2, hasCover: false })).toMatchObject({ indicator: 'dot', countLabel: '2' });
  });

  it('🔴 화면에서도 오늘 칸은 테두리였다가, 누르면 채움 하나만 남는다', () => {
    // 기록 카드가 장소 이름의 언어를 읽는다(S15P21E201-1860) — 언어 공급자 안에 그린다.
    render(<OnboardingPreferencesProvider><RecordsBrowser stories={[story('a'), story('b')]} tx={(ko) => ko} locale="ko-KR" onOpen={jest.fn()} /></OnboardingPreferencesProvider>);
    fireEvent.press(screen.getByText('달력'));
    const key = todayKey();
    expect(screen.getByTestId(`records-day-${key}-ring`)).toBeTruthy();
    fireEvent.press(screen.getByTestId(`records-day-${key}`));
    expect(screen.getByTestId(`records-day-${key}-filled`)).toBeTruthy();
    expect(screen.queryByTestId(`records-day-${key}-ring`)).toBeNull();
    expect(screen.getAllByText('2').length).toBeGreaterThanOrEqual(1);
  });
});
