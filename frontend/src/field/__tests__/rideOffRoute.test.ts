// 탑승 안내 — 노선에서 멀면 「지금 여기」를 정하지 않는다(S15P21E201-1903, 실기기: 강서구에서 연 1003번 안내가 부산역을 「지금 여기」로 찍었다).
import { rideProgress, type RideLeg } from '@/field/ride';

const leg: RideLeg = {
  kind: 'bus', line: '1003', board: '부산역', alightName: '동백섬입구', alight: { latitude: 35.1580, longitude: 129.1530 },
  stops: [
    { name: '부산역', lat: 35.1152, lng: 129.0403 },
    { name: '초량역', lat: 35.1210, lng: 129.0430 },
    { name: '대우마리나', lat: 35.1570, lng: 129.1500 },
    { name: '동백섬입구', lat: 35.1580, lng: 129.1530 },
  ],
} as RideLeg;

describe('탑승 중 지금 정류장', () => {
  it('🔴 노선에서 몇 km 떨어져 있으면 지금 정류장도 남은 정류장도 정하지 않는다 — 내리라고 울리지 않는다', () => {
    const p = rideProgress(leg, { latitude: 35.0950, longitude: 128.8550 }); // 강서구 명지
    expect(p.currentIndex).toBeNull();
    expect(p.remainingStops).toBeNull();
    expect(p.alertNow).toBe(false);
  });
  it('정류장 가까이 있으면 그 정류장이 지금 자리다', () => {
    const p = rideProgress(leg, { latitude: 35.1153, longitude: 129.0404 });
    expect(p.currentIndex).toBe(0);
    expect(p.remainingStops).toBe(3);
  });
});
