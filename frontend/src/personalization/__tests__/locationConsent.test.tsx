// 위치 이용 동의 — S15P21E201-1691 (사용자 결정: 위치를 처음 쓸 때 한 번 묻고, PRECISE_LOCATION 동의 하나로 다섯 곳을 덮는다).
//
// 🔴 이 시험이 지키는 것:
//    1. 세 상태를 가른다 — 물은 적 없음(null) · 동의 · 거절. 거절도 계정에 남긴다.
//    2. 로그인했으면 계정의 기록이 정본이다. 기기에만 있는 답은 계정에 올린다.
//    3. 동의 문: 처음 쓸 때(ensure)는 물은 적 없을 때만, 사람이 「내 위치로」를 누를 때(request)는 거절했어도 묻는다.
//    4. 서버로 보내는 좌표는 약 100m(소수 셋째 자리)로 줄인다 — 주소창에 실려 접속 기록에 남는다.
//    5. 위치를 읽는 곳은 모두 동의 문을 지난다 — 새로 읽는 곳이 생기면 이 시험이 먼저 빨개진다.
import { Pressable, Text as RNText } from 'react-native';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

import { apiRequest } from '@/api/client';
import { getMyConsents, updateMyConsents } from '@/auth/authApi';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

jest.mock('@/auth/authApi', () => ({ getMyConsents: jest.fn(), updateMyConsents: jest.fn(async () => ({})) }));
jest.mock('@/api/client', () => ({ ...jest.requireActual('@/api/client'), apiRequest: jest.fn() }));

import {
  __resetLocationConsentForTests, coarseCoordinate, loadLocationConsent, readLocationConsent, setLocationConsent, syncLocationConsent,
} from '@/personalization/locationConsent';
import { useLocationGate } from '@/personalization/useLocationGate';
import { getNearbyPlaces } from '@/discovery/localExplore';
import { loadNearbyBusArrivals } from '@/field/busArrivals';
import { NowCard } from '@/plan/NowCard';

// 소스를 읽는 시험은 이 저장소의 방식대로 require 로 부른다 — 앱 tsconfig 에 node 타입이 없다.
declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync, readdirSync, statSync } = require('fs');
const { join, relative } = require('path');

jest.setTimeout(20000);

const mockedGet = getMyConsents as jest.Mock;
const mockedPatch = updateMyConsents as jest.Mock;
const mockedApi = apiRequest as jest.Mock;
const consents = (status?: 'GRANTED' | 'REVOKED') => ({
  behaviorPersonalizationEnabled: false,
  consents: status ? [{ consentType: 'PRECISE_LOCATION', status, policyVersion: '2026-09', decidedAt: '2026-09-25T00:00:00Z' }] : [],
});

beforeEach(async () => {
  __resetLocationConsentForTests();
  await AsyncStorage.clear();
  mockedGet.mockReset();
  mockedPatch.mockReset();
  mockedPatch.mockResolvedValue({});
  mockedApi.mockReset();
});

describe('1·2. 동의 값', () => {
  it('🔴 세 상태 — 기록 없음은 null(물은 적 없음), GRANTED 는 동의, REVOKED 는 거절', () => {
    expect(readLocationConsent(consents())).toBeNull();
    expect(readLocationConsent(consents('GRANTED'))).toBe(true);
    expect(readLocationConsent(consents('REVOKED'))).toBe(false);
  });

  it('🔴 거절도 계정에 남긴다 — PRECISE_LOCATION: false', async () => {
    await setLocationConsent(false, 'tok');
    expect(mockedPatch).toHaveBeenCalledWith('tok', { PRECISE_LOCATION: false });
    expect(await loadLocationConsent()).toBe(false);
  });

  it('로그인 안 했으면 기기에만 남긴다', async () => {
    await setLocationConsent(true, null);
    expect(mockedPatch).not.toHaveBeenCalled();
    expect(await loadLocationConsent()).toBe(true);
  });

  it('🔴 계정의 기록이 정본이다 — 기기가 거절이어도 계정이 동의면 동의', async () => {
    await setLocationConsent(false, null);
    mockedGet.mockResolvedValue(consents('GRANTED'));
    expect(await syncLocationConsent('tok')).toBe(true);
    expect(await loadLocationConsent()).toBe(true);
  });

  it('계정에 기록이 없고 이 기기에만 답이 있으면 그 답을 계정에 올린다', async () => {
    await setLocationConsent(true, null);
    mockedGet.mockResolvedValue(consents());
    expect(await syncLocationConsent('tok')).toBe(true);
    expect(mockedPatch).toHaveBeenCalledWith('tok', { PRECISE_LOCATION: true });
  });

  it('계정에 못 물어보면 기기의 답 — 모른다고 동의로 치지 않는다', async () => {
    mockedGet.mockRejectedValue(new Error('offline'));
    expect(await syncLocationConsent('tok')).toBeNull();
  });
});

describe('3. 동의 문', () => {
  let gate: ReturnType<typeof useLocationGate>;
  function Host() {
    gate = useLocationGate(null);
    return <>{gate.sheet}</>;
  }
  const mount = () => render(<OnboardingPreferencesProvider><Host /></OnboardingPreferencesProvider>);

  it('🔴 처음 쓸 때(물은 적 없음) 묻고, 동의하면 참 — 답을 적는다', async () => {
    mount();
    let answer: Promise<boolean> = Promise.resolve(false);
    await act(async () => { answer = gate.ensure(); });
    expect(await screen.findByText('위치를 써도 될까요?')).toBeTruthy();
    // 🔴 지도·피드는 보내지 않는다 — 「보내는 곳」과 나눠 적는다.
    expect(screen.getByText('피드의 가까운 순 · 지도의 내 위치 — 이 기기 안에서만 쓰고 보내지 않아요.')).toBeTruthy();
    expect(screen.queryByText(/지도 — 가까운 장소와 버스를 찾을 때 위치를 보내요/)).toBeNull();
    fireEvent.press(screen.getByText('동의하기'));
    expect(await answer).toBe(true);
    expect(await loadLocationConsent()).toBe(true);
  });

  it('🔴 거절한 사람에게 처음 쓸 때는 조르지 않는다 — 창 없이 거짓', async () => {
    await setLocationConsent(false, null);
    mount();
    let result: boolean | undefined;
    await act(async () => { result = await gate.ensure(); });
    expect(result).toBe(false);
    expect(screen.queryByText('위치를 써도 될까요?')).toBeNull();
  });

  it('🔴 「내 위치로」를 누르면(request) 거절했어도 다시 묻는다', async () => {
    await setLocationConsent(false, null);
    mount();
    let answer: Promise<boolean> = Promise.resolve(false);
    await act(async () => { answer = gate.request(); });
    expect(await screen.findByText('위치를 써도 될까요?')).toBeTruthy();
    fireEvent.press(screen.getByText('위치 없이 쓰기'));
    expect(await answer).toBe(false);
  });

  it('동의한 사람에게는 묻지 않는다', async () => {
    await setLocationConsent(true, null);
    mount();
    let result: boolean | undefined;
    await act(async () => { result = await gate.request(); });
    expect(result).toBe(true);
    expect(screen.queryByText('위치를 써도 될까요?')).toBeNull();
  });
});

describe('4. 서버로 가는 좌표는 약 100m', () => {
  it('소수 셋째 자리로 반올림', () => {
    expect(coarseCoordinate(35.158712)).toBe(35.159);
    expect(coarseCoordinate(129.160349)).toBe(129.16);
  });

  it('🔴 둘러보기 「내 주변」 요청 주소에 정확한 좌표가 실리지 않는다', async () => {
    mockedApi.mockResolvedValue({ items: [], effectiveRadiusM: 1000, radiusExpanded: false });
    await getNearbyPlaces({ lat: 35.158712, lng: 129.160349 }).catch(() => undefined);
    const path = String(mockedApi.mock.calls[0][0]);
    expect(path).toContain('lat=35.159');
    expect(path).toContain('lng=129.16');
    expect(path).not.toContain('35.158712');
  });

  it('🔴 주변 버스 요청 주소에도', async () => {
    mockedApi.mockResolvedValue({ stops: [] });
    await loadNearbyBusArrivals({ latitude: 35.158712, longitude: 129.160349 }, 'tok').catch(() => undefined);
    const path = String(mockedApi.mock.calls[0][0]);
    expect(path).toContain('lat=35.159');
    expect(path).not.toContain('129.160349');
  });
});

describe('지금 카드 — 위치를 안 쓰면', () => {
  it('「위치 추적 중」이라고 하지 않고, 도착은 직접 찍으라고 말한다', () => {
    render(
      <NowCard status="RUNNING" gpsUsable={false} locationOff title="해운대로 이동 중" detail={null} clock="11:00" driftText={null} progress={null}
        showManualArrival onStart={jest.fn()} onPause={jest.fn()} onArrive={jest.fn()} onSkip={jest.fn()} tx={(ko) => ko} />,
    );
    expect(screen.getByText('위치를 쓰지 않아요 · 도착하면 직접 찍어 주세요')).toBeTruthy();
    expect(screen.queryByText('지금 · 위치 추적 중')).toBeNull();
  });
});

describe('5. 위치를 읽는 곳은 모두 동의 문을 지난다', () => {
  const root = join(__dirname, '..', '..', '..');
  // readCurrentPosition — 시간 제한을 둔 위치 읽기 도우미(S15P21E201-1824). 도우미 자신은 묻지 않으므로, 그것을 부르는 파일이 동의 문을 지나야 한다.
  const READS = /requestForegroundPermissionsAsync|getCurrentPositionAsync|watchPositionAsync|navigator\.geolocation|readCurrentPosition\(/;
  const HELPERS = new Set(['src/location/currentPosition.ts']);
  const GATED = /useLocationGate|syncLocationConsent/;
  // 이 훅은 스스로 묻지 않는다 — 부르는 쪽(폰 여행 화면)이 동의가 있을 때만 켠다. 그 쪽은 아래 목록에서 따로 본다.
  const CALLER_GATED: Record<string, string> = { 'src/trip/page/useLiveLocation.ts': 'src/trip/page/TripPageMobile.tsx' };

  function walk(dir: string, out: string[] = []): string[] {
    for (const name of readdirSync(dir) as string[]) {
      if (name === 'node_modules' || name === '__tests__' || name.startsWith('.')) continue;
      const path = join(dir, name);
      if (statSync(path).isDirectory()) walk(path, out);
      else if (/\.(ts|tsx)$/.test(name) && !/\.test\.tsx?$/.test(name)) out.push(path);
    }
    return out;
  }

  it('🔴 위치를 읽는 파일마다 동의 문이 있다', () => {
    const files = [...walk(join(root, 'app')), ...walk(join(root, 'src'))];
    const readers = files.filter((file) => READS.test(readFileSync(file, 'utf8') as string));
    expect(readers.length).toBeGreaterThan(0);
    const ungated = readers
      .map((file) => relative(root, file).replace(/\\/g, '/'))
      .filter((rel) => {
        if (HELPERS.has(rel)) return false;
        const gateFile = CALLER_GATED[rel] ?? rel;
        return !GATED.test(readFileSync(join(root, gateFile), 'utf8') as string);
      });
    expect(ungated).toEqual([]);
  });

  it('폰 여행 화면은 동의가 있을 때만 위치를 따라간다', () => {
    const source = readFileSync(join(root, 'src/trip/page/TripPageMobile.tsx'), 'utf8') as string;
    expect(source).toContain("useLiveLocation(progress.status === 'RUNNING' && locationGate.consent === true)");
  });
});

// 안 쓰는 가져오기가 남지 않게(시험 틀이 바뀔 때 흔적).
void Pressable; void RNText; void waitFor;
