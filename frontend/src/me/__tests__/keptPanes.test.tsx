// 마이페이지 「기록 | 설정」 두 판 — S15P21E201-1603.
//
// 🔴 이 시험이 지키는 것: 전환할 때 판을 새로 만들지 않는다. 전에는 누를 때마다 보이던 판을 지우고 다른 판을 새로
//    만들어서, 폰 성능 흉내(CPU 6배)에서 누른 뒤 첫 프레임이 0.6~1.1초 뒤에 왔다 — 0.32초 애니메이션이 통째로 묻혔다.
import { useEffect } from 'react';
import { StyleSheet, Text } from 'react-native';
import { act, render, screen } from '@testing-library/react-native';

import { KeptPanes } from '@/me/KeptPanes';

const mounts = { records: 0, settings: 0 };
function Counted({ name }: { name: 'records' | 'settings' }) {
  useEffect(() => { mounts[name] += 1; }, [name]);
  return <Text>{name}</Text>;
}
const panes = { records: <Counted name="records" />, settings: <Counted name="settings" /> };
const hidden = (name: string) => StyleSheet.flatten(screen.getByTestId(`pane-${name}`, { includeHiddenElements: true }).props.style)?.display === 'none';

beforeEach(() => { mounts.records = 0; mounts.settings = 0; });

describe('「기록 | 설정」 두 판', () => {
  it('🔴 몇 번을 오가도 판은 한 번씩만 만든다', () => {
    const view = render(<KeptPanes active="records" panes={panes} />);
    view.rerender(<KeptPanes active="settings" panes={panes} />);
    view.rerender(<KeptPanes active="records" panes={panes} />);
    view.rerender(<KeptPanes active="settings" panes={panes} />);
    expect(mounts).toEqual({ records: 1, settings: 1 });
  });

  it('보이는 판은 하나 — 다른 판은 자리도 안 차지하고 낭독기에도 안 읽힌다', () => {
    const view = render(<KeptPanes active="records" panes={panes} />);
    expect(hidden('records')).toBe(false);
    expect(hidden('settings')).toBe(true);
    view.rerender(<KeptPanes active="settings" panes={panes} />);
    expect(hidden('records')).toBe(true);
    expect(hidden('settings')).toBe(false);
  });

  it('🔴 안 연 판은 첫 화면에서는 만들지 않고, 첫 화면 뒤 잠시 후 미리 만든다 — 첫 누름도 새로 만들지 않게', () => {
    jest.useFakeTimers();
    try {
      const view = render(<KeptPanes active="records" panes={panes} />);
      expect(mounts).toEqual({ records: 1, settings: 0 });
      act(() => { jest.advanceTimersByTime(300); });
      expect(mounts).toEqual({ records: 1, settings: 1 });
      view.rerender(<KeptPanes active="settings" panes={panes} />);
      expect(mounts).toEqual({ records: 1, settings: 1 });
      // 🔴 진짜 시계로 돌리기 전에 치운다 — 가짜 시계에 걸린 일이 남으면 뒷정리가 멈춘다(!1605 CI).
      view.unmount();
    } finally {
      jest.useRealTimers();
    }
  });

  it('🔴 미리 만들기 전에 화면이 사라지면 타이머도 같이 치운다 — 남은 타이머가 시험 작업 칸을 붙잡지 않게', () => {
    jest.useFakeTimers();
    try {
      const view = render(<KeptPanes active="records" panes={panes} />);
      view.unmount();
      expect(jest.getTimerCount()).toBe(0);
    } finally {
      jest.useRealTimers();
    }
  });
});

// 소스를 읽는 시험은 이 저장소의 방식대로 require 로 부른다 — 앱 tsconfig 에 node 타입이 없다.
declare const require: (id: string) => any;
declare const __dirname: string;

describe('마이페이지 「기록 | 설정」', () => {
  it('🔴 누른 탭은 작은 부품이 쥔다 — 화면 전체가 쥐면 누를 때마다 화면 전체가 다시 그려진다', () => {
    const { readFileSync } = require('fs');
    const { join } = require('path');
    const source = readFileSync(join(__dirname, '..', '..', '..', 'app', '(tabs)', 'me.tsx'), 'utf8') as string;
    const tabs = source.indexOf('function RecordsSettingsTabs(');
    expect(tabs).toBeGreaterThan(-1);
    // 탭 상태는 그 부품 안에만 있다
    expect(source.indexOf("useState<'records' | 'settings'>")).toBeGreaterThan(tabs);
    expect(source).toContain('<KeptPanes active={meTab} panes={panes} />');
  });
});
