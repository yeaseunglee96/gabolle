// 여행 기간 달력 — 고른 기간이 한 띠로 이어진다(S15P21E201-1720, 사용자 요청 · 조율 세션 결정).
//
// 🔴 전에는 시작·끝 칸을 칸 전체 검정 + 네 모서리 둥글게, 사이 칸을 모서리 없는 회색으로 그렸다.
//    시작·끝이 각자 닫힌 알약이라 띠가 끊기고, 칸이 넓은 넓은 화면에서는 가로로 긴 알약이 됐다(달력이 생긴 때부터).
//    - 띠는 칸 폭을 채운다. 시작 칸은 가운데부터 오른쪽만, 끝 칸은 왼쪽부터 가운데까지만.
//    - 시작·끝은 고정 크기 동그라미(넓은 화면에서도 안 늘어남). 하루짜리는 동그라미만.
//    - 줄마다 알약으로 닫는다: 줄 처음(일요일·앞이 빈칸)은 띠 왼쪽을, 줄 끝(토요일·뒤가 빈칸)은 오른쪽을 둥글게.
import { render, screen } from '@testing-library/react-native';
import { StyleSheet } from 'react-native';

import { MonthGrid } from '@/home/PlanStartBar';
import { EMPTY_START_BAR } from '@/home/startBarValue';
import { color } from '@/design/tokens';

const tx = (ko: string) => ko;
// 2026년 10월 — 1일이 목요일. 16(금) 17(토) | 18(일) 19(월) 20(화) 21 22(목)
const grid = (startDate: string, endDate: string) =>
  render(<MonthGrid year={2026} month={9} value={{ ...EMPTY_START_BAR, startDate, endDate }} today="2026-09-26" onPick={jest.fn()} tx={tx} />);
const band = (day: string) => StyleSheet.flatten(screen.getByTestId(`range-band-2026-10-${day}`).props.style);
const hasBand = (day: string) => screen.queryByTestId(`range-band-2026-10-${day}`) !== null;
const dot = (day: string) => StyleSheet.flatten(screen.getByTestId(`range-dot-2026-10-${day}`).props.style);
const hasDot = (day: string) => screen.queryByTestId(`range-dot-2026-10-${day}`) !== null;

describe('여행 기간 달력 — 한 띠로 이어진 알약', () => {
  it('🔴 이어진 기간 18→20: 시작은 가운데부터 오른쪽, 사이는 칸 가득, 끝은 가운데까지 — 틈이 없다', () => {
    grid('2026-10-18', '2026-10-20');
    expect(band('18')).toMatchObject({ left: '50%', right: 0 });
    expect(band('19')).toMatchObject({ left: 0, right: 0 });
    expect(band('20')).toMatchObject({ left: 0, right: '50%' });
    for (const day of ['18', '19', '20']) expect(band(day).backgroundColor).toBe(color.surface.tint);
    // 사이 칸은 모서리가 없다 — 이어진다
    expect(band('19').borderTopLeftRadius).toBeUndefined();
    expect(band('19').borderTopRightRadius).toBeUndefined();
    expect(hasBand('17')).toBe(false);
    expect(hasBand('21')).toBe(false);
  });

  it('🔴 시작·끝은 고정 크기 동그라미 — 칸이 넓어도 가로로 안 늘어난다. 띠와 같은 높이', () => {
    grid('2026-10-18', '2026-10-20');
    for (const day of ['18', '20']) {
      expect(dot(day)).toMatchObject({ width: 34, height: 34, top: 3, backgroundColor: color.brand.navy });
    }
    // 칸(40) 안에서 띠도 위아래 3 씩 — 동그라미와 같은 높이 34
    expect(band('19')).toMatchObject({ top: 3, bottom: 3 });
    expect(hasDot('19')).toBe(false);
  });

  it('하루짜리는 동그라미 하나 — 띠가 없다', () => {
    grid('2026-10-22', '2026-10-22');
    expect(hasDot('22')).toBe(true);
    expect(hasBand('22')).toBe(false);
  });

  it('🔴 주를 넘는 16→20: 줄 끝(토 17)은 띠 오른쪽을, 다음 줄 처음(일 18)은 왼쪽을 둥글게 닫는다', () => {
    grid('2026-10-16', '2026-10-20');
    expect(band('16')).toMatchObject({ left: '50%' });
    expect(band('17')).toMatchObject({ borderTopRightRadius: 999, borderBottomRightRadius: 999 });
    expect(band('17').borderTopLeftRadius).toBeUndefined();
    expect(band('18')).toMatchObject({ borderTopLeftRadius: 999, borderBottomLeftRadius: 999 });
    expect(band('18').borderTopRightRadius).toBeUndefined();
    expect(band('19').borderTopLeftRadius).toBeUndefined();
  });

  it('달이 바뀌는 기간: 10월의 1일(앞이 빈칸)은 띠 왼쪽을 둥글게 연다', () => {
    grid('2026-09-29', '2026-10-02');
    expect(band('01')).toMatchObject({ left: 0, borderTopLeftRadius: 999 });
    expect(band('02')).toMatchObject({ right: '50%' });
  });

  it('끝을 아직 안 골랐으면 시작 동그라미만 — 띠가 없다', () => {
    grid('2026-10-18', '');
    expect(hasDot('18')).toBe(true);
    expect(hasBand('18')).toBe(false);
  });
});
