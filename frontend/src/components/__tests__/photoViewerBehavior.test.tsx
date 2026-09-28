import type { ReactNode } from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import { SafeAreaProvider, initialWindowMetrics } from 'react-native-safe-area-context';
import { PhotoViewer } from '@/components/PhotoViewer';

const wrapper = ({ children }: { children: ReactNode }) => (
  <SafeAreaProvider initialMetrics={initialWindowMetrics ?? { frame: { x: 0, y: 0, width: 390, height: 844 }, insets: { top: 47, left: 0, right: 0, bottom: 34 } }}>{children}</SafeAreaProvider>
);
describe('PhotoViewer 실제 동작', () => {
  it('열면 사진과 닫기가 보인다', () => {
    render(<PhotoViewer visible uri="https://x/a.jpg" label="여행 기록 사진" closeLabel="사진 닫기" onClose={() => {}} />, { wrapper });
    expect(screen.getByLabelText('여행 기록 사진')).toBeTruthy();
    expect(screen.getAllByLabelText('사진 닫기').length).toBe(2); // 바탕 + ✕
  });
  it('닫기를 누르면 onClose 가 불린다', () => {
    const onClose = jest.fn();
    render(<PhotoViewer visible uri="https://x/a.jpg" label="사진" closeLabel="사진 닫기" onClose={onClose} />, { wrapper });
    fireEvent.press(screen.getAllByLabelText('사진 닫기')[1]);
    expect(onClose).toHaveBeenCalled();
  });
  it('uri 가 없으면 아무것도 안 그린다', () => {
    render(<PhotoViewer visible uri={null} label="사진" closeLabel="사진 닫기" onClose={() => {}} />, { wrapper });
    expect(screen.queryByLabelText('사진 닫기')).toBeNull();
  });
});
