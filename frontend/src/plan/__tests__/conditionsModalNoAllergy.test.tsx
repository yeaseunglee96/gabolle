// 여행 조건 모달을 «실제로 그려» 본다 — S15P21E201-1497.
//
// 🔴 이 시험이 지키는 것 둘.
//    ① 알레르기 질문이 화면에 «없다» (결정은 -1468 의 ㄱ)
//    ② 그런데도 «저장할 수 있다»
//
//    ②가 진짜 위험이다. 저장 가능 조건이 allergyAnswered 를 요구하고 있었는데, 질문을
//    지우면 그 값은 영영 false 라 저장 단추가 영원히 안 눌린다 — 화면은 멀쩡해 보이고
//    사람만 못 나간다. 지우는 변경에서 제일 나기 쉬운 사고다.
import { render, fireEvent, waitFor } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { PlanProvider } from '@/plan/PlanProvider';
import { ConditionsPromptModal } from '@/plan/ConditionsPromptModal';

jest.mock('@/auth/AuthProvider', () => ({
  useAuth: () => ({ user: null, accessToken: null }),
}));

jest.mock('@/plan/travelConditions', () => ({
  ...jest.requireActual('@/plan/travelConditions'),
  saveTravelConditions: jest.fn(async () => ({ synced: true })),
}));

const mount = () => render(
  <OnboardingPreferencesProvider>
    <PlanProvider><ConditionsPromptModal visible reprompt={false} onClose={() => {}} /></PlanProvider>
  </OnboardingPreferencesProvider>,
);

describe('여행 조건 모달', () => {
  it('🔴 알레르기를 묻지 않는다 — 8대 원재료 어느 것도 화면에 없다', async () => {
    const view = mount();
    await waitFor(() => expect(view.queryByText('여행 조건 미리 알려주기')).toBeTruthy());

    // 중첩 <Text> 로 쪼개져도 잡히게 부분 일치로 본다.
    expect(view.queryAllByText(/알레르기/)).toHaveLength(0);
    for (const label of ['땅콩', '견과류', '갑각류', '생선', '달걀', '우유·유제품', '밀', '대두']) {
      expect(view.queryByText(label)).toBeNull();
    }
  });

  it('식단은 그대로 묻는다 — 알레르기만 뺀 것이지 모달을 없앤 것이 아니다', async () => {
    const view = mount();
    await waitFor(() => expect(view.queryByText('여행 조건 미리 알려주기')).toBeTruthy());
    expect(view.queryAllByText(/식단/).length).toBeGreaterThan(0);
  });

  it('🔴 식단에만 답해도 저장할 수 있다 — 알레르기를 기다리다 영영 못 누르면 안 된다', async () => {
    const view = mount();
    await waitFor(() => expect(view.queryByText('여행 조건 미리 알려주기')).toBeTruthy());

    // 「해당 없음」은 식단 줄에도 알레르기 줄에도 있었다. 이제 하나뿐이어야 한다.
    const none = view.getAllByText('해당 없음');
    expect(none).toHaveLength(1);
    fireEvent.press(none[0]);

    // 🔴 글자가 있는지가 아니라 «눌리는지»를 본다. 단추는 disabled={!savable} 이라
    //    글자는 그대로 있고 못 누르는 상태가 정확히 이 사고의 모양이다.
    await waitFor(() => {
      const save = view.getByText('저장하고 시작');
      const button = save.parent;
      expect(button).toBeTruthy();
      expect(view.queryByText('저장 중…')).toBeNull();
    });
    const save = view.getByText('저장하고 시작');
    expect(save).toBeTruthy();
    fireEvent.press(save);
    // 눌려서 저장이 시작되면 문구가 「저장 중…」으로 바뀐다. 안 눌리면 그대로다.
    await waitFor(() => expect(view.queryByText('저장하고 시작')).toBeNull());
  });
});
