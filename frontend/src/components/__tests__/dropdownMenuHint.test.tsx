// 공용 ⋯ 메뉴 — 설명 줄 · 바깥/Escape 로 닫힘. S15P21E201-1593.
//
// 🔴 여행 화면의 ⋯ 메뉴가 이 부품으로 옮겨 왔다. 전에는 직접 그린 판이라 닫는 길이 「⋯ 다시 누르기」뿐이었고,
//    메뉴를 연 채 「동행 초대」를 누르면 초대 창 위에 남았다. 이 부품은 창(Modal)이라 바깥을 누르거나
//    Escape(웹에서 onRequestClose 로 온다)로 닫힌다 — 그 두 길을 지킨다.
import { Modal } from 'react-native';
import { fireEvent, render, screen } from '@testing-library/react-native';
import { SafeAreaProvider, initialWindowMetrics } from 'react-native-safe-area-context';

import { DropdownMenu } from '@/components/DropdownMenu';

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <SafeAreaProvider initialMetrics={initialWindowMetrics ?? { frame: { x: 0, y: 0, width: 1280, height: 900 }, insets: { top: 0, left: 0, right: 0, bottom: 0 } }}>{children}</SafeAreaProvider>
);
const items = [
  { key: 'rename', label: '이름 바꾸기', onPress: jest.fn() },
  { key: 'edit', label: '일정 편집', hint: '순서·고정·제외·다시 계산', onPress: jest.fn() },
];

describe('공용 ⋯ 메뉴', () => {
  it('설명 줄이 있으면 이름 아래에 그린다 — 없으면 안 그린다', () => {
    render(<DropdownMenu visible anchor={null} items={items} onClose={jest.fn()} />, { wrapper });
    expect(screen.getByText('일정 편집')).toBeTruthy();
    expect(screen.getByText('순서·고정·제외·다시 계산')).toBeTruthy();
  });

  it('🔴 Escape(onRequestClose)와 바깥 누르기로 닫힌다', () => {
    const onClose = jest.fn();
    render(<DropdownMenu visible anchor={null} items={items} onClose={onClose} />, { wrapper });

    screen.UNSAFE_getByType(Modal).props.onRequestClose();
    expect(onClose).toHaveBeenCalledTimes(1);

    fireEvent.press(screen.getByLabelText('메뉴 닫기'));
    expect(onClose).toHaveBeenCalledTimes(2);
  });

  it('항목을 누르면 메뉴를 닫고 그 일을 한다', () => {
    const onClose = jest.fn();
    render(<DropdownMenu visible anchor={null} items={items} onClose={onClose} />, { wrapper });
    fireEvent.press(screen.getByText('이름 바꾸기'));
    expect(onClose).toHaveBeenCalled();
    expect(items[0].onPress).toHaveBeenCalled();
  });
});
