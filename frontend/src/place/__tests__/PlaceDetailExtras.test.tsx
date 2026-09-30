// 장소 상세 추가 정보 — 공인 표식 알약이 정보 줄보다 먼저 그려진다(S15P21E201-1892).
import { render, screen } from '@testing-library/react-native';

jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko, language: 'ko' }) }));

import { PlaceDetailExtras } from '../PlaceDetailExtras';

const recognition = {
  featureType: 'RECOGNITION', evidenceStatus: 'VERIFIED',
  value: { badges: [
    { kind: 'MODEL_RESTAURANT', since: null, menu: null, source: '부산광역시 모범음식점 현황' },
    { kind: 'TAXI_DRIVER_PICK', since: null, menu: '선지국밥', source: '부산광역시 택슐랭 선정 식당(2025)' },
  ] },
} as never;
const fee = { featureType: 'ADMISSION_FEE', evidenceStatus: 'VERIFIED', value: { raw: '무료' } } as never;

describe('PlaceDetailExtras 공인 표식', () => {
  it('두 알약·추천 메뉴·출처를 그리고, 정보 줄 카드보다 앞에 둔다', () => {
    const view = render(<PlaceDetailExtras features={[fee, recognition]} />);
    expect(screen.getByText('모범음식점')).toBeTruthy();
    expect(screen.getByText('택시기사 추천')).toBeTruthy();
    expect(screen.getByText('추천 메뉴: 선지국밥')).toBeTruthy();
    expect(screen.getByText('부산광역시 모범음식점 현황 · 부산광역시 택슐랭 선정 식당(2025)')).toBeTruthy();
    const ids = JSON.stringify(view.toJSON());
    expect(ids.indexOf('place-recognition')).toBeLessThan(ids.indexOf('place-detail-extras'));
  });

  it('없으면 아무것도 안 그린다', () => {
    render(<PlaceDetailExtras features={[fee]} />);
    expect(screen.queryByTestId('place-recognition')).toBeNull();
    expect(screen.queryByText('모범음식점')).toBeNull();
  });
});
