// 긴급 도움·분실물 — S15P21E201-1879 (UI 캔버스 ㉒).
//
// 🔴 이 시험이 지키는 것은 «적힌 사실» 이다. 시안에는 「119 · 영어·중국어·일본어 통역」이 있었지만 근거가 없었고,
//    112 가 직접 통역하는 언어는 영어·중국어뿐이다(2026-09-30 확인, emergencyContacts.ts 머리말). 급할 때 읽는 글이라
//    틀린 한 줄이 가장 비싸다 — 누가 문구를 「더 좋게」 고치다 근거 없는 약속을 되살리지 않게 막는다.
import { fireEvent, render } from '@testing-library/react-native';
import { Linking } from 'react-native';

import Emergency from '../emergency';
import LostItems from '../lost-items';
import { EMERGENCY_LINES, METRO_LOST_FOUND, POLICE_LOST_FOUND_URL } from '@/field/emergencyContacts';

jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn(), back: jest.fn(), replace: jest.fn(), canGoBack: () => true }) }));
jest.mock('@/components/BrandLogoLink', () => ({ BrandLogoLink: () => null }));
// 화면 틀은 안전 영역 값을 요구한다 — 내용만 본다.
jest.mock('@/components/Screen', () => ({ Screen: ({ children }: { children: unknown }) => children }));
let mockLang: 'ko' | 'en' = 'ko';
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string, en: string) => (mockLang === 'ko' ? ko : en), language: mockLang }) }));

describe('긴급 도움', () => {
  beforeEach(() => { jest.spyOn(Linking, 'openURL').mockResolvedValue(true); });
  afterEach(() => jest.restoreAllMocks());

  it('🔴 번호를 누르면 그 번호로 바로 전화한다 — 119 · 112 · 1330', () => {
    const view = render(<Emergency />);
    for (const number of ['119', '112', '1330']) {
      fireEvent.press(view.getByTestId(`emergency-call-${number}`));
      expect(Linking.openURL).toHaveBeenLastCalledWith(`tel:${number}`);
    }
  });

  it('🔴 근거 없는 통역 약속을 하지 않는다 — 112 는 영어·중국어만, 119 는 언어를 못 박지 않는다', () => {
    const [fire, police, hotline] = EMERGENCY_LINES;
    expect(fire.languages.ko).not.toMatch(/일본어|중국어|영어/);
    expect(police.languages.ko).toContain('영어·중국어');
    expect(police.languages.ko).not.toContain('일본어');
    expect(hotline.languages.ko).toContain('24시간');
  });

  it('보여 주는 한국어는 화면 언어와 상관없이 한국어다 — 뜻은 화면 언어로 아래에', () => {
    mockLang = 'en';
    const view = render(<Emergency />);
    expect(view.getByText(/저는 외국인 관광객이에요/)).toBeTruthy();
    expect(view.getByText('Please help me. I am a foreign tourist.')).toBeTruthy();
    mockLang = 'ko';
  });
});

describe('분실물', () => {
  beforeEach(() => { jest.spyOn(Linking, 'openURL').mockResolvedValue(true); });
  afterEach(() => jest.restoreAllMocks());

  it('지하철은 도시철도 유실물센터로 전화, 그 밖은 경찰민원24(옛 LOST112)를 연다', () => {
    const view = render(<LostItems />);
    fireEvent.press(view.getByTestId('lost-call-metro'));
    expect(Linking.openURL).toHaveBeenLastCalledWith(METRO_LOST_FOUND.tel);
    expect(METRO_LOST_FOUND.tel).toBe('tel:051-640-7339');
    fireEvent.press(view.getByTestId('lost-open-police'));
    expect(Linking.openURL).toHaveBeenLastCalledWith(POLICE_LOST_FOUND_URL);
    expect(POLICE_LOST_FOUND_URL).toBe('https://minwon24.police.go.kr');
  });
});
