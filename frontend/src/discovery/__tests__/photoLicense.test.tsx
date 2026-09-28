// 장소 사진의 라이선스 표기 — S15P21E201-1610.
//
// 🔴 이 시험이 지키는 것: 위키미디어 커먼즈 사진(CC BY·CC BY-SA 등)은 출처와 함께 «라이선스 이름과 링크»를 보여야
//    쓸 수 있다. 서버가 photoLicense 를 주면 출처 줄에 이름을 덧붙이고, 누르면 파일 페이지(없으면 라이선스 주소)가 열린다.
//    지금 사진(관광공사 공공누리)은 photoLicense 가 없다 — 지금처럼 출처만 그린다.
import type { ReactNode } from 'react';
import { Linking } from 'react-native';
import { fireEvent, render, screen } from '@testing-library/react-native';

import { PlaceVisual } from '@/components/PlaceVisual';
import { photoLabels, toPlaceSearchItem, type PlaceSearchItemDto } from '@/discovery/places';
import { festivalCards } from '@/home/useHomeData';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

const tx = (ko: string) => ko;
const CC = { name: 'CC BY-SA 3.0', url: 'https://creativecommons.org/licenses/by-sa/3.0', filePage: 'https://commons.wikimedia.org/wiki/File:Busan_Modern_History_Museum-01.jpg' };
const wrapper = ({ children }: { children: ReactNode }) => <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>;

describe('사진 출처 줄', () => {
  it('공공누리 사진은 지금과 같다 — 출처만, 링크 없음', () => {
    expect(photoLabels({ photoSource: '한국관광공사' }, tx)).toMatchObject({ credit: '사진: 한국관광공사', licenseUrl: null });
  });

  it('🔴 라이선스가 있으면 이름을 덧붙이고, 링크는 파일 페이지 — 없으면 라이선스 주소', () => {
    expect(photoLabels({ photoSource: 'Wikimedia Commons / Busan Museum', photoLicense: CC }, tx))
      .toMatchObject({ credit: '사진: Wikimedia Commons / Busan Museum · CC BY-SA 3.0', licenseUrl: CC.filePage });
    expect(photoLabels({ photoSource: 'x', photoLicense: { name: 'CC BY 4.0', url: 'https://creativecommons.org/licenses/by/4.0', filePage: null } }, tx).licenseUrl)
      .toBe('https://creativecommons.org/licenses/by/4.0');
    // 퍼블릭 도메인 — 라이선스 주소가 없어도 파일 페이지로 간다
    expect(photoLabels({ photoSource: 'x', photoLicense: { name: 'Public domain', url: null, filePage: CC.filePage } }, tx).licenseUrl).toBe(CC.filePage);
  });

  it('🔴 공용 사진 부품: 출처 줄을 누르면 파일 페이지가 열린다', () => {
    const open = jest.spyOn(Linking, 'openURL').mockResolvedValue(true);
    render(<PlaceVisual name="부산근대역사관" address={null} photoUrl="https://img/p.jpg" photoSource="Wikimedia Commons" photoLicense={CC} />, { wrapper });
    fireEvent.press(screen.getByRole('link', { name: '사진: Wikimedia Commons · CC BY-SA 3.0' }));
    expect(open).toHaveBeenCalledWith(CC.filePage);
    open.mockRestore();
  });

  it('🔴 서버 응답을 화면 모양으로 옮길 때 라이선스 칸을 떨어뜨리지 않는다 — 장소 목록 · 홈 축제 카드', () => {
    const dto = { placeId: 'p1', nameKo: '부산근대역사관', nameEn: null, category: 'CULTURE', address: '부산 중구', lat: 35.1, lng: 129.0, matchedField: null, photoUrl: 'u', photoSource: 's', photoLicense: CC } as PlaceSearchItemDto;
    expect(toPlaceSearchItem(dto).photoLicense).toEqual(CC);
    const festival = { placeId: 'f1', title: '축제', nameKo: '광장', address: '부산', startDate: '2026-10-01', endDate: '2026-10-02', overlapDates: [], photoUrl: 'u', photoSource: 's', photoLicense: CC };
    expect(festivalCards([festival])[0].photoLicense).toEqual(CC);
  });
});
