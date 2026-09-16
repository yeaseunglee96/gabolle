// S15P21E201-1128 — 화면 밖으로 밀려난 화면이 내비게이션을 조종하던 것.
//
// 여행 만들기 마지막 화면(confirm)이 생성 화면 밑에 남은 채로 router.replace 를 불러,
// 일정이 완성된 순간 사용자를 1단계 빈 화면으로 끌어내렸다. 이 시험이 지키는 것은
// 하나다 — **보고 있지 않으면 보내지 않는다.**
import { renderHook } from '@testing-library/react-native';

const mockReplace = jest.fn();
const mockState = { focused: true };

jest.mock('expo-router', () => ({
  useRouter: () => ({ replace: mockReplace }),
  // 실제 useFocusEffect 는 화면을 보고 있을 때만 콜백을 돌린다. 그것만 흉내낸다.
  useFocusEffect: (callback: () => void | (() => void)) => {
    const { useEffect } = require('react');
    useEffect(() => {
      if (!mockState.focused) return undefined;
      return callback();
    }, [callback]);
  },
}));

import { useFocusedRedirect } from '../useFocusedRedirect';

beforeEach(() => {
  mockReplace.mockClear();
  mockState.focused = true;
});

describe('useFocusedRedirect', () => {
  it('보고 있고 조건이 참이면 보낸다', () => {
    renderHook(() => useFocusedRedirect(true, '/plan/basic'));
    expect(mockReplace).toHaveBeenCalledWith('/plan/basic');
  });

  it('조건이 거짓이면 아무 데도 안 보낸다', () => {
    renderHook(() => useFocusedRedirect(false, '/plan/basic'));
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('🔴 보고 있지 않으면 조건이 참이어도 안 보낸다 — 1128 이 난 자리다', () => {
    mockState.focused = false;
    renderHook(() => useFocusedRedirect(true, '/plan/basic'));
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('밑에 남은 채로 조건이 참이 되어도 안 보낸다', () => {
    // 생성 화면으로 넘어간 뒤(focused=false) 일정이 완성되며 입력이 비워지는 상황.
    const { rerender } = renderHook<void, { should: boolean }>(({ should }) => useFocusedRedirect(should, '/plan/basic'), {
      initialProps: { should: false },
    });
    mockState.focused = false;
    rerender({ should: true });
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('다시 그 화면으로 돌아오면 그때 판정한다', () => {
    mockState.focused = false;
    const { rerender } = renderHook<void, { should: boolean }>(({ should }) => useFocusedRedirect(should, '/plan/basic'), {
      initialProps: { should: true },
    });
    expect(mockReplace).not.toHaveBeenCalled();

    mockState.focused = true;
    rerender({ should: true });
    expect(mockReplace).toHaveBeenCalledWith('/plan/basic');
  });
});
