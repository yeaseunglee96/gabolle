// 여행표 앞면의 단추 — 단추 안에 단추가 없다(S15P21E201-1666).
//
// 🔴 이 시험이 지키는 것: 앞면 전체가 「여행표 상세 보기」 단추였고, 그 안에 「내 일정 보기」 단추가 또 있었다.
//    화면 읽기 프로그램(시각장애인용 음성 안내)은 단추 하나를 한 덩어리로 읽어서 안쪽 「내 일정 보기」를 따로 못 눌렀고,
//    웹에서는 단추 안에 단추가 들어갔다. 앞면은 손가락으로 누르면 그대로 뒤집히고(S15P21E201-1562 의 약속),
//    뒤집는 단추는 맨 아래 안내 줄이 맡는다.
import { act, fireEvent, render } from '@testing-library/react-native';

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
  validText: '이 승차권은 10.3 여행에만 쓸 수 있어요', firstStop: null, lastStop: null, conditions: []
};

const details: TripPassDetail[] = [
  { key: '출발지', value: '부산역' },
  { key: '첫 일정', value: '09:30 · 해운대 바다 산책' },
];

type Node = { type?: unknown; parent?: Node | null; props?: { accessibilityRole?: string; accessible?: boolean; pointerEvents?: string } };

/** 화면 읽기 프로그램이 단추로 잡는 것인가 — 실제로 그려지는 칸(호스트)이고, 역할이 단추이고, 스스로 감추지 않았다.
 *  Pressable 같은 부품 자신도 같은 props 를 들고 있어서, 호스트만 세야 한 단추를 두 번 세지 않는다. */
const isButton = (node: Node) => typeof node.type === 'string' && node.props?.accessibilityRole === 'button' && node.props?.accessible !== false;

function buttonAncestor(node: Node): Node | null {
  for (let current = node.parent ?? null; current; current = current.parent ?? null) if (isButton(current)) return current;
  return null;
}

function printed(onOpenItinerary?: () => void) {
  const view = render(<TripPass data={data} details={details} tx={tx} onOpenItinerary={onOpenItinerary} />);
  // 인쇄가 끝나야 뒤집기가 열린다.
  act(() => { jest.runAllTimers(); });
  return view;
}

describe('여행표 앞면의 단추', () => {
  beforeEach(() => { jest.useFakeTimers(); });
  afterEach(() => { jest.useRealTimers(); });

  it('🔴 단추 안에 단추가 없다', () => {
    const view = printed(jest.fn());

    const nested = view.UNSAFE_root.findAll((node) => isButton(node as Node) && buttonAncestor(node as Node) !== null, { deep: true });
    expect(nested.map((node) => String(node.props.accessibilityLabel ?? node.type))).toEqual([]);
  });

  it('「내 일정 보기」는 따로 잡히는 단추다 — 누르면 일정으로 간다', () => {
    const open = jest.fn();
    const view = printed(open);

    fireEvent.press(view.getByRole('button', { name: /내 일정 보기/ }));
    expect(open).toHaveBeenCalledTimes(1);
  });

  it('뒤집는 단추는 맨 아래 안내 줄이다 — 누르면 뒷면이 열린다', () => {
    const view = printed(jest.fn());
    const backFace = () => view.getByLabelText('앞면으로 돌리기', { includeHiddenElements: true }).parent as Node;
    const facePointer = () => { for (let n: Node | null = backFace(); n; n = n.parent ?? null) if (n.props?.pointerEvents) return n.props.pointerEvents; return undefined; };

    expect(facePointer()).toBe('none');
    fireEvent.press(view.getByRole('button', { name: '여행표 상세 보기' }));
    expect(facePointer()).toBe('auto');
  });
});

// ── 안 보이는 면 (S15P21E201-1674) ─────────────────────────────────────────────
//
// 🔴 뒷면은 눌리지만 않게(pointerEvents) 막혀 있었다. 키보드 탭과 화면 읽기 프로그램은 안 보이는 면의 단추에도 섰다 —
//    앞면을 보고 있는데 「앞면으로 돌리기」가 읽히고, 뒤집은 뒤에는 안 보이는 「내 일정 보기」에 탭이 섰다.

type Tabbable = { parent?: Tabbable | null; props?: { tabIndex?: number } };
/** 이름표나 글자로 찾아(숨긴 면까지) 위로 올라가며 처음 만나는 tabIndex — 없으면 기본(0, 탭이 선다). */
function tabIndexOf(view: ReturnType<typeof printed>, name: string | RegExp): number {
  const hidden = { includeHiddenElements: true };
  let node = (view.queryByLabelText(name, hidden) ?? view.getByText(name, hidden)) as unknown as Tabbable | null;
  for (; node; node = node.parent ?? null) if (typeof node.props?.tabIndex === 'number') return node.props.tabIndex;
  return 0;
}

describe('여행표 — 안 보이는 면', () => {
  beforeEach(() => { jest.useFakeTimers(); });
  afterEach(() => { jest.useRealTimers(); });

  it('🔴 앞면일 때 뒷면 단추는 화면 읽기에서도 키보드 탭에서도 빠진다', () => {
    const view = printed(jest.fn());

    expect(view.queryByRole('button', { name: '앞면으로 돌리기' })).toBeNull();
    expect(view.queryByRole('button', { name: /일정 보기 →/ })).toBeNull();
    expect(tabIndexOf(view, '앞면으로 돌리기')).toBe(-1);
    expect(tabIndexOf(view, /일정 보기 →/)).toBe(-1);
    // 보이는 앞면은 그대로다
    expect(view.getByRole('button', { name: /내 일정 보기/ })).toBeTruthy();
    expect(tabIndexOf(view, /내 일정 보기/)).toBe(0);
  });

  it('🔴 뒤집으면 거꾸로 — 앞면 단추가 빠지고 뒷면 단추가 들어온다', () => {
    const view = printed(jest.fn());
    fireEvent.press(view.getByRole('button', { name: '여행표 상세 보기' }));

    expect(view.getByRole('button', { name: '앞면으로 돌리기' })).toBeTruthy();
    expect(tabIndexOf(view, '앞면으로 돌리기')).toBe(0);
    expect(view.queryByRole('button', { name: /내 일정 보기/ })).toBeNull();
    expect(view.queryByRole('button', { name: '여행표 상세 보기' })).toBeNull();
    expect(tabIndexOf(view, /내 일정 보기/)).toBe(-1);
    expect(tabIndexOf(view, '여행표 상세 보기')).toBe(-1);
  });
});
