// 앱 지도 +/− 단추 — 가로 화면에서 오른쪽 내비 막대에 덮이지 않는다 — S15P21E201-1909(실기기 10/1).
import { StyleSheet } from 'react-native';
import { SafeAreaInsetsContext } from 'react-native-safe-area-context';
import { act, create, type ReactTestRenderer } from 'react-test-renderer';

import { RouteMap } from '@/map/RouteMap';

jest.mock('react-native-webview', () => ({ WebView: () => null }));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko }) }));

const stops = [{ id: 'a', number: 1, name: 'A', latitude: 35.1, longitude: 129.0 }];

function zoomRight(tree: ReactTestRenderer) {
  const zoomIn = tree.root.findAll((node) => node.props.accessibilityLabel === '지도 확대' && typeof node.type !== 'string')[0];
  let parent = zoomIn.parent;
  while (parent && StyleSheet.flatten(parent.props.style)?.right === undefined) parent = parent.parent;
  return StyleSheet.flatten(parent!.props.style).right as number;
}

beforeAll(() => { process.env.EXPO_PUBLIC_KAKAO_MAP_JS_KEY = 'test-key'; });

it('🔴 오른쪽 안전 영역(가로 화면 내비 막대)만큼 단추를 안쪽으로 들인다', () => {
  let plain!: ReactTestRenderer; let landscape!: ReactTestRenderer;
  act(() => { plain = create(<RouteMap stops={stops} selectedId="a" onSelect={() => {}} routes={[]} height={400} />); });
  act(() => { landscape = create(<SafeAreaInsetsContext.Provider value={{ top: 0, bottom: 0, left: 0, right: 48 }}><RouteMap stops={stops} selectedId="a" onSelect={() => {}} routes={[]} height={400} /></SafeAreaInsetsContext.Provider>); });
  expect(zoomRight(landscape) - zoomRight(plain)).toBe(48);
});
