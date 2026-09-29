// 지도의 경사·그늘 겹 — 「불러오는 중」이 영원히 남던 것.
//
// 🔴 이 시험이 지키는 것: 셋을 가른다 — 받는 중 · 자료가 있는 여섯 지역 밖(받을 파일이 없다) · 못 받음.
//    전에는 셋 다 「불러오는 중…」이었고, 지역 밖이면 영원히 그랬다. 그리고 그린 선이 없으면
//    「빨간 길은 …」 같은 범례를 그린 것처럼 내지 않는다.
import { act, renderHook, waitFor } from '@testing-library/react-native';

import { layerNote } from '@/map/MobilityLayerToggle';
import { clearMobilityLayerCache, useMobilityLayer, type MobilityLayerFile } from '@/map/mobilityLayers';

jest.mock('@/api/client', () => ({ API_BASE_URL: 'https://example.test' }));

const tx = (ko: string) => ko;
const inHaeundae = [{ id: 's1', number: 1, name: '고재', latitude: 35.1653, longitude: 129.1586 }];
const inSeoul = [{ id: 's1', number: 1, name: '서울역', latitude: 37.5547, longitude: 126.9707 }];
const near = [129.1586, 35.1653, 129.1590, 35.1656];
const file = (segs: MobilityLayerFile['segs']): MobilityLayerFile => ({
  v: 1, area: 'HAEUNDAE', steepPermille: 83, slopeBasis: '경사 중앙값 · 30m 이상 길 · 고도 자료로 잰 추정치', shadowBasis: '건물 그림자 · 2026-07-15 · 9–18시 평균', segs,
});
const respond = (body: MobilityLayerFile) => Promise.resolve({ ok: true, json: () => Promise.resolve(body) });

let fetchMock: jest.Mock;
beforeEach(() => {
  clearMobilityLayerCache();
  fetchMock = jest.fn();
  (globalThis as { fetch: unknown }).fetch = fetchMock;
});

describe('겹의 형편', () => {
  it('🔴 받는 동안은 「받는 중」, 받으면 「받았다」', async () => {
    let finish!: (value: unknown) => void;
    fetchMock.mockReturnValue(new Promise((resolve) => { finish = resolve; }));
    const { result } = renderHook(() => useMobilityLayer('slope', inHaeundae));
    expect(result.current.status).toBe('loading');
    await act(async () => { finish(await respond(file([[120, 10, near]]))); });
    await waitFor(() => expect(result.current.status).toBe('ready'));
    expect(result.current.lines).toHaveLength(1);
  });

  it('🔴 여섯 지역 밖이면 받지 않고 곧바로 「지역 밖」 — 영원히 「불러오는 중」이 아니다', () => {
    const { result } = renderHook(() => useMobilityLayer('shade', inSeoul));
    expect(result.current.status).toBe('outside');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('🔴 받을 파일을 하나도 못 받으면 「못 받음」 — 끄고 다시 켜면 다시 묻는다', async () => {
    fetchMock.mockResolvedValueOnce({ ok: false, json: async () => null });
    const { result, rerender } = renderHook(({ kind }: { kind: 'slope' | null }) => useMobilityLayer(kind, inHaeundae), { initialProps: { kind: 'slope' as 'slope' | null } });
    await waitFor(() => expect(result.current.status).toBe('failed'));
    fetchMock.mockImplementation(() => respond(file([[120, 10, near]])));
    rerender({ kind: null });
    expect(result.current.status).toBe('off');
    rerender({ kind: 'slope' });
    await waitFor(() => expect(result.current.status).toBe('ready'));
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('네트워크가 끊겨도 「못 받음」', async () => {
    fetchMock.mockRejectedValue(new TypeError('Network request failed'));
    const { result } = renderHook(() => useMobilityLayer('shade', inHaeundae));
    await waitFor(() => expect(result.current.status).toBe('failed'));
  });
});

describe('겹의 한 줄 풀이', () => {
  const base = { basis: null, partial: false, drawn: false };
  it('🔴 형편마다 경사·그늘 둘 다 말이 따로 있다', () => {
    expect(layerNote('slope', { ...base, status: 'loading' }, tx)).toBe('경사 자료를 불러오는 중…');
    expect(layerNote('shade', { ...base, status: 'loading' }, tx)).toBe('그늘 자료를 불러오는 중…');
    expect(layerNote('slope', { ...base, status: 'outside' }, tx)).toContain('경사 자료는 해운대·광안리·남포·서면·영도·송정만');
    expect(layerNote('shade', { ...base, status: 'outside' }, tx)).toContain('그늘 자료는 해운대·광안리·남포·서면·영도·송정만');
    expect(layerNote('slope', { ...base, status: 'failed' }, tx)).toContain('경사 자료를 불러오지 못했어요');
    expect(layerNote('shade', { ...base, status: 'failed' }, tx)).toContain('그늘 자료를 불러오지 못했어요');
    expect(layerNote('slope', { ...base, status: 'off' }, tx)).toBeNull();
  });

  it('🔴 그린 선이 없으면 빨간 길·파란 길 범례를 내지 않는다', () => {
    for (const status of ['loading', 'outside', 'failed'] as const) {
      expect(layerNote('slope', { ...base, status }, tx)).not.toContain('빨간 길');
      expect(layerNote('shade', { ...base, status }, tx)).not.toContain('파란 길');
    }
    expect(layerNote('slope', { ...base, status: 'ready' }, tx)).toContain('가파른 길이 없어요');
    expect(layerNote('slope', { ...base, status: 'ready' }, tx)).not.toContain('빨간 길');
    expect(layerNote('shade', { ...base, status: 'ready' }, tx)).toContain('그늘 자료가 있는 길이 없어요');
  });

  it('그린 선이 있으면 범례와 기준을, 일부만 받았으면 그렇다고', () => {
    const drawn = { ...base, status: 'ready' as const, drawn: true, basis: '기준' };
    expect(layerNote('slope', drawn, tx)).toMatch(/^빨간 길: .* · 기준$/);
    expect(layerNote('shade', { ...drawn, partial: true }, tx)).toMatch(/^파란 길이 짙을수록 그늘이 많아요 · 기준 · 일부 지역 자료는 못 불러왔어요$/);
  });

  // 경사를 휠체어 문제로만 말하던 것 — 휠체어를 안 고른 사람에게도 「휠체어 경사로 기준보다 가팔라요」라고 했다.
  it('🔴 경사 범례는 휠체어를 말하지 않는다 — 휠체어·유아차 여행(stepFree)에만 한 마디 붙인다', () => {
    const drawn = { ...base, status: 'ready' as const, drawn: true };
    for (const state of [drawn, { ...base, status: 'ready' as const }]) {
      expect(layerNote('slope', state, tx)).not.toContain('휠체어');
    }
    expect(layerNote('slope', drawn, tx)).toBe('빨간 길: 경사 8.33% 넘는 가파른 길');
    expect(layerNote('slope', drawn, tx, true)).toBe('빨간 길: 경사 8.33% 넘는 가파른 길 · 휠체어·유아차로는 피하는 게 좋아요');
    // 가파른 길이 없으면 피하라는 말도 없다.
    expect(layerNote('slope', { ...base, status: 'ready' }, tx, true)).not.toContain('휠체어');
    // 그늘 겹은 stepFree 와 상관없다.
    expect(layerNote('shade', drawn, tx, true)).not.toContain('휠체어');
  });

  it('영어 화면도 휠체어 기준으로 말하지 않는다', () => {
    const en = (_ko: string, english: string) => english;
    const drawn = { ...base, status: 'ready' as const, drawn: true };
    expect(layerNote('slope', drawn, en)).toBe('Red: paths steeper than 8.33%');
    expect(layerNote('slope', drawn, en)).not.toMatch(/wheelchair/i);
    expect(layerNote('slope', drawn, en, true)).toBe('Red: paths steeper than 8.33% · best avoided with a wheelchair or stroller');
  });
});
