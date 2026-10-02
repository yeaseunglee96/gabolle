// 긴급 도움 화면의 가까운 병원 지도(S15P21E201-1934).
// 🔴 지키는 것 둘 — ① 이 화면에서는 위치를 «묻지» 않는다(이미 허락한 사람에게만 지도) ② 못 그리면 빈 칸 대신 원래 입구 한 줄.
import { render, waitFor } from '@testing-library/react-native';
import * as Location from 'expo-location';

import { NearbyHelpPreview } from '../NearbyHelpPreview';
import { getNearbyHelp } from '../helpPlacesApi';
import { syncLocationConsent } from '@/personalization/locationConsent';

jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn() }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token', ready: true }) }));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko, language: 'ko' }) }));
jest.mock('@/map/RouteMap', () => ({ RouteMap: () => { const { View: V } = jest.requireActual('react-native'); return <V testID="preview-map" />; } }));
jest.mock('expo-location', () => ({ getForegroundPermissionsAsync: jest.fn(), requestForegroundPermissionsAsync: jest.fn() }));
jest.mock('@/location/currentPosition', () => ({ readCurrentPosition: jest.fn(async () => ({ coords: { latitude: 35.1532, longitude: 129.1186 } })) }));
jest.mock('@/personalization/locationConsent', () => ({ syncLocationConsent: jest.fn() }));
jest.mock('../helpPlacesApi', () => ({ getNearbyHelp: jest.fn() }));

const place = (name: string, distanceMeters: number, openNow: boolean | null) => ({ name, nameEn: null, type: '의원', address: null, phone: null, lat: 35.15, lng: 129.12, distanceMeters, emergency: false, todayOpen: null, todayClose: null, openNow });

describe('NearbyHelpPreview', () => {
  beforeEach(() => jest.clearAllMocks());

  it('🔴 위치 동의가 없으면 묻지 않고 입구 한 줄만 — 지도 칸을 비워 두지 않는다', async () => {
    (syncLocationConsent as jest.Mock).mockResolvedValue(false);
    const view = render(<NearbyHelpPreview testID="nearby" />);
    await waitFor(() => expect(syncLocationConsent).toHaveBeenCalled());
    expect(view.getByText('가까운 병원·약국·경찰 지도')).toBeTruthy();
    expect(view.queryByTestId('preview-map')).toBeNull();
    expect(Location.requestForegroundPermissionsAsync).not.toHaveBeenCalled();
  });

  it('🔴 기기 권한이 없어도 묻지 않는다', async () => {
    (syncLocationConsent as jest.Mock).mockResolvedValue(true);
    (Location.getForegroundPermissionsAsync as jest.Mock).mockResolvedValue({ granted: false, canAskAgain: true });
    const view = render(<NearbyHelpPreview />);
    await waitFor(() => expect(Location.getForegroundPermissionsAsync).toHaveBeenCalled());
    expect(view.queryByTestId('preview-map')).toBeNull();
    expect(Location.requestForegroundPermissionsAsync).not.toHaveBeenCalled();
  });

  it('이미 허락했으면 들어오자마자 지도와 가장 가까운 병원', async () => {
    (syncLocationConsent as jest.Mock).mockResolvedValue(true);
    (Location.getForegroundPermissionsAsync as jest.Mock).mockResolvedValue({ granted: true, canAskAgain: true });
    (getNearbyHelp as jest.Mock).mockResolvedValue({ kind: 'HOSPITAL', places: [place('강대식내과의원', 420, true), place('광안연세이비인후과', 610, true)], source: 'HIRA', basedOn: '2026-06' });
    const view = render(<NearbyHelpPreview />);
    await waitFor(() => expect(view.getByTestId('preview-map')).toBeTruthy());
    expect(view.getByText('강대식내과의원')).toBeTruthy();
    expect(view.getByText('420m · 진료 중')).toBeTruthy();
    expect(getNearbyHelp).toHaveBeenCalledWith('hospital', { latitude: 35.1532, longitude: 129.1186 }, 'token', 3);
  });
});

