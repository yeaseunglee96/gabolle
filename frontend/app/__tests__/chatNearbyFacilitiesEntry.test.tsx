// AI 챗봇의 「근처 편의시설」 입구 (S15P21E201-2052).
// 🔴 지키는 것: 카드는 도구 격자(2×2)의 «다섯째 칸»이 아니라 격자 바로 아래 한 줄이다 — 다섯째 칸이면 2열에서 한 칸이 비어
//    S15P21E201-1396 이 고친 균형이 다시 깨진다. 긴급 도움 카드 → 격자 → 근처 편의시설 → 「현장 도구 전부 보기」 순서다.
import { configure, fireEvent, render, waitFor, within } from '@testing-library/react-native';

import Chat from '../chat';

// 🔴 느린 러너(여러 잡이 한 기계에서 도는 CI)에서는 첫 화면을 그리는 데만 1초가 넘게 걸린다 — 기본 대기 1초로는 부족하다.
configure({ asyncUtilTimeout: 8000 });
jest.setTimeout(30000);

const mockPush = jest.fn();
let mockWidth = 390;

jest.mock('expo-router', () => ({ useRouter: () => ({ push: mockPush, replace: jest.fn(), back: jest.fn(), canGoBack: () => true }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: null }) }));
jest.mock('@/components/Screen', () => ({ Screen: ({ children }: { children: unknown }) => children }));
jest.mock('@/components/DongbaekMascot', () => ({ GabolleMascot: () => null }));
jest.mock('@/field/BusIcon', () => ({ BusIcon: () => null }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ width: mockWidth }) }));
jest.mock('@/plan/PlanProvider', () => ({ usePlan: () => ({ update: jest.fn() }) }));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko, language: 'ko' }) }));

beforeEach(() => {
  jest.clearAllMocks();
  mockWidth = 390;
});

describe('챗봇 첫 화면의 도구 칸 (폰 폭)', () => {
  it('🔴 격자는 그대로 넷이고, 근처 편의시설은 그 바로 아래·「현장 도구 전부 보기」 위의 한 줄이다', () => {
    const view = render(<Chat />);

    const tools = within(view.getByLabelText('여행 도구 바로가기'));
    // 격자의 넷 — 환율·버스·사투리·도움말
    expect(tools.getAllByRole('button')).toHaveLength(4);
    // 줄(링크) 셋의 순서 — 긴급 도움 → 근처 편의시설 → 현장 도구 전부 보기
    const labels = tools.getAllByRole('link').map((node) => String(node.props.accessibilityLabel));
    expect(labels[0]).toMatch(/^긴급 도움/);
    expect(labels[1]).toBe('근처 편의시설, 주차장 · 약국 · 화장실 · 짐보관');
    expect(labels[2]).toBe('현장 도구 전부 보기');
    expect(labels).toHaveLength(3);
  });

  it('카드에 제목과 네 가지 이름이 보인다', () => {
    const view = render(<Chat />);

    expect(view.getByText('근처 편의시설')).toBeTruthy();
    expect(view.getByText('주차장 · 약국 · 화장실 · 짐보관')).toBeTruthy();
  });

  it('누르면 근처 편의시설 화면으로 간다 — AI 를 거치지 않는다', () => {
    const view = render(<Chat />);

    fireEvent.press(view.getByTestId('chat-nearby-facilities'));

    expect(mockPush).toHaveBeenCalledWith('/field/nearby');
  });
});

describe('넓은 화면', () => {
  it('왼쪽 도구 기둥에도 같은 카드가 한 번 있다', () => {
    mockWidth = 1100;

    const view = render(<Chat />);

    expect(view.getAllByTestId('chat-nearby-facilities')).toHaveLength(1);
    fireEvent.press(view.getByTestId('chat-nearby-facilities'));
    expect(mockPush).toHaveBeenCalledWith('/field/nearby');
  });
});

describe('대화가 시작된 뒤', () => {
  it('큰 도구 칸은 접히고, 입력창 위 작은 줄에 «근처 편의시설»이 있다', async () => {
    const view = render(<Chat />);
    expect(view.getByTestId('chat-nearby-facilities')).toBeTruthy();

    fireEvent.changeText(view.getByLabelText('기능이나 궁금한 점 검색'), '안녕');
    fireEvent.press(view.getByLabelText('검색'));

    await waitFor(() => expect(view.getByTestId('chat-nearby-facilities-compact')).toBeTruthy());
    expect(view.queryByTestId('chat-nearby-facilities')).toBeNull();
    fireEvent.press(view.getByTestId('chat-nearby-facilities-compact'));
    expect(mockPush).toHaveBeenCalledWith('/field/nearby');
  });
});
