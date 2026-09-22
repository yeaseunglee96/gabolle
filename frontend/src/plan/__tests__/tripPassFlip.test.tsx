// 여행표 뒤집기 — 시안 ②.
//
// 🔴 이 시험이 지키는 것은 「예쁘게 도는가」가 아니라 **빈 뒷면이 안 나오는가**이다.
//    만드는 중에는 적을 것이 없다. 그때도 뒤집히면 사용자는 하얀 뒷면을 보고 고장으로 읽고,
//    다시 앞으로 돌리는 방법도 모른 채 멈춘다.
import { StyleSheet } from 'react-native';
import { act, render } from '@testing-library/react-native';

import { TripPass } from '@/plan/TripPass';
import type { TripPassData, TripPassDetail } from '@/plan/tripPassData';

const tx = (ko: string) => ko;

const data: TripPassData = {
  code: 'GB-ABC123',
  stampDate: '20 · SEP · 2026',
  fromLabel: '부산역',
  toLabel: '광안리',
  startTime: '09:30',
  endTime: '17:30',
  dateRange: '10.3(금) – 10.5(일)',
  mode: '대중교통',
  owner: '진미리',
  fields: [{ key: '방문지', value: '9곳' }],
  url: 'https://example.test/trips/1',
  validText: '이 승차권은 10.3 여행에만 쓸 수 있어요',
};

const details: TripPassDetail[] = [
  { key: '출발지', value: '부산역' },
  { key: '첫 일정', value: '09:30 · 해운대 바다 산책' },
];

describe('여행표 뒤집기', () => {
  it('🔴 적을 것이 없으면 뒤집기가 아예 없다 — 빈 뒷면을 보여 주지 않는다', () => {
    const view = render(<TripPass data={data} tx={tx} />);

    expect(view.queryByLabelText('여행표 상세 보기')).toBeNull();
    expect(view.queryByText('일정 보기 →')).toBeNull();
  });

  it('🔴 빈 목록도 「없음」이다 — 줄이 하나도 없으면 뒤집을 이유가 없다', () => {
    const view = render(<TripPass data={data} details={[]} tx={tx} />);

    expect(view.queryByLabelText('여행표 상세 보기')).toBeNull();
  });

  it('적을 것이 있어도 **다 나오기 전에는** 못 뒤집는다 — 프린터 안에서 돌아가면 반쪽만 보인다', () => {
    // 출력 애니메이션이 끝나야 뒤집기가 열린다. 막 그린 직후에는 아직이다.
    const view = render(<TripPass data={data} details={details} tx={tx} />);

    expect(view.queryByLabelText('여행표 상세 보기')).toBeNull();
  });

  it('앞면은 언제나 그린다 — 뒤집기가 없어도 티켓은 보여야 한다', () => {
    const view = render(<TripPass data={data} tx={tx} />);

    expect(view.getByText('부산역')).toBeTruthy();
    expect(view.getByText('광안리')).toBeTruthy();
  });
});

// ── 힌트 줄도 「종이」다 (S15P21E201-1460) ─────────────────────────────────────
//
// 🔴 팀원 실기 리포트(빌드 29) — 「승차권 맨 밑에 글자가 승차권 밖으로 튀어나가있음」.
//    흰 종이 바탕은 머리(sheet)·스터브(stub)가 «각자» 칠한다. 마지막 절취선 뒤의 힌트
//    줄만 안 칠해서, 그 글자가 페이지 바탕 위에 떴다. 배치는 멀쩡하므로 자동 검사도
//    스냅샷도 안 잡고 «눈으로만» 보인다. 그래서 색을 직접 잰다.

/** 이 글자가 실제로 깔고 앉은 바탕색. 감싸개가 몇 겹이든 위로 올라가 처음 칠해진 곳을 찾는다. */
function backdropOf(node: unknown): string | undefined {
  let current = node as { parent?: unknown; props?: { style?: unknown } } | null;
  for (let depth = 0; depth < 12 && current; depth += 1) {
    const flat = StyleSheet.flatten(current.props?.style as never) as { backgroundColor?: string } | undefined;
    if (flat?.backgroundColor) return flat.backgroundColor;
    current = current.parent as typeof current;
  }
  return undefined;
}

describe('여행표 맨 아래 힌트', () => {
  it('🔴 힌트 줄이 종이 위에 있다 — 안 칠하면 승차권 밖으로 밀려난 것처럼 보인다', () => {
    jest.useFakeTimers();
    try {
      const view = render(<TripPass data={data} details={details} tx={tx} />);
      // 인쇄가 끝나야 힌트가 나온다 — 위 시험들이 말하는 그대로다.
      act(() => { jest.runAllTimers(); });

      const hint = backdropOf(view.getByText('눌러서 여행표 상세 보기 ↻'));
      // 스터브(유효기간 줄)가 바로 위 칸이다. 같은 종이면 같은 색이어야 한다.
      const stub = backdropOf(view.getByText(data.validText));

      expect(stub).toBeDefined();
      expect(hint).toBe(stub);
    } finally {
      jest.useRealTimers();
    }
  });
});
