// 방침·약관·삭제 안내가 같은 문의 주소와 같은 기한을 말하는지 — S15P21E201-1646.
//
// 🔴 이 시험이 지키는 것은 문구가 예쁜가가 아니라 «공개 문서끼리, 그리고 약속끼리 어긋나지 않는가»이다.
//    문의 주소가 한 문서에서만 바뀌거나, 확정되지 않은 것(책임자 이름·방침 버전·시행일)이 어느새 지어낸 값으로
//    채워지면 화면은 멀쩡한데 공개 문서가 사실과 달라진다. 눈으로는 안 잡힌다.
import { render } from '@testing-library/react-native';
import type { ReactNode } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

import AccountDeletion from '../../../app/legal/account-deletion';
import { TRANSLATIONS } from '@/i18n/translations';
import { ACCOUNT_DELETION_SECTIONS, PRIVACY_SECTIONS, TERMS_SECTIONS } from '../legalContent';

// 공개 문서가 말해야 하는 값. 바꿀 때는 이 세 줄과 legalContent.ts, 번역 카탈로그, 자동응답(공용 Gmail 설정)을 같이 바꾼다.
const SUPPORT_EMAIL = 'gabolle.support@gmail.com';
const DELETION_RESPONSE_DAYS = 7;
const ACCOUNT_DELETION_URL = 'https://j15e201.p.ssafy.io/legal/account-deletion';

jest.mock('expo-router', () => ({
  useRouter: () => ({ back: jest.fn(), replace: jest.fn(), push: jest.fn(), canGoBack: () => false }),
  usePathname: () => '/legal/account-deletion',
}));

const Providers = ({ children }: { children: ReactNode }) => (
  <SafeAreaProvider initialMetrics={{ frame: { x: 0, y: 0, width: 390, height: 844 }, insets: { top: 47, left: 0, right: 0, bottom: 34 } }}>
    <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>
  </SafeAreaProvider>
);

const all = (sections: typeof PRIVACY_SECTIONS) => JSON.stringify(sections);

describe('문의 주소는 세 문서가 같은 값을 말한다', () => {
  it('개인정보처리방침 5·7절과 이용약관 8절이 공용 주소를 한국어·영어 둘 다에 적는다', () => {
    for (const sections of [PRIVACY_SECTIONS, TERMS_SECTIONS]) {
      const withEmail = sections.filter((s) => s.paragraphs.some(([ko, en]) => ko.includes(SUPPORT_EMAIL) && en.includes(SUPPORT_EMAIL)));
      expect(withEmail.length).toBeGreaterThan(0);
    }
    const privacyTitles = PRIVACY_SECTIONS.filter((s) => s.paragraphs.some(([ko]) => ko.includes(SUPPORT_EMAIL))).map((s) => s.title[0]);
    expect(privacyTitles).toEqual(expect.arrayContaining([expect.stringContaining('5.'), expect.stringContaining('7.')]));
  });

  it('「문의 창구는 팀 확정 후 안내합니다」 같은 옛 빈자리가 더는 남아 있지 않다', () => {
    expect(all(PRIVACY_SECTIONS)).not.toContain('문의 창구는 팀 확정 후');
    expect(all(PRIVACY_SECTIONS)).not.toContain('a contact channel for other requests');
    expect(all(TERMS_SECTIONS)).not.toContain('사업자 정보와 문의처는 팀 확정 후');
  });

  it('🔴 아직 정하지 못한 것(책임자 이름·방침 버전·시행일)은 지어내지 않고 「팀 확정 후」로 남아 있다', () => {
    const section7 = PRIVACY_SECTIONS.find((s) => s.title[0].startsWith('7.'));
    expect(section7?.paragraphs[0][0]).toContain('개인정보 보호책임자 이름, 방침 버전과 시행일은 팀 확정 후');
    expect(section7?.paragraphs[0][1]).toContain('policy version, and effective date will be added after team confirmation');
  });

  it('방침 5절이 삭제 안내 페이지의 주소를 알려 준다', () => {
    expect(all(PRIVACY_SECTIONS)).toContain(ACCOUNT_DELETION_URL);
  });
});

describe('공개 문서의 값이 서로 어긋나지 않는다', () => {
  const everything = () => all(PRIVACY_SECTIONS) + all(TERMS_SECTIONS) + all(ACCOUNT_DELETION_SECTIONS);

  it('🔴 문서 안의 모든 이메일 주소는 공용 주소 하나다 — 한 곳만 바뀌면 삭제 요청이 엉뚱한 곳으로 간다', () => {
    const emails = everything().match(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g) ?? [];
    expect(emails.length).toBeGreaterThan(0);
    expect(new Set(emails)).toEqual(new Set([SUPPORT_EMAIL]));
  });

  it('🔴 처리 기한은 한 값이다 — 자동응답이 약속한 7일과 같다', () => {
    const days = [...all(ACCOUNT_DELETION_SECTIONS).matchAll(/(\d+)일 이내|within (\d+) days/g)].map((m) => Number(m[1] ?? m[2]));
    expect(days.length).toBeGreaterThan(0);
    expect(new Set(days)).toEqual(new Set([DELETION_RESPONSE_DAYS]));
  });

  it('일본어·중국어 번역이 카탈로그에 있다 — 없으면 그 화면에서 이 줄만 영어로 뜬다', () => {
    const sources = [
      ...ACCOUNT_DELETION_SECTIONS.flatMap((s) => [s.title[0], ...s.paragraphs.map((p) => p[0])]),
      ...PRIVACY_SECTIONS.filter((s) => /^(5|7)\./.test(s.title[0])).map((s) => s.paragraphs[0][0]),
      ...TERMS_SECTIONS.filter((s) => s.title[0].startsWith('8.')).map((s) => s.paragraphs[0][0]),
    ];
    for (const source of sources) {
      const entry = TRANSLATIONS[source];
      expect([source.slice(0, 30), Boolean(entry?.ja), Boolean(entry?.zhHans), Boolean(entry?.zhHant)]).toEqual([source.slice(0, 30), true, true, true]);
    }
  });

  it('옛 문구의 번역 줄이 카탈로그에 남아 있지 않다', () => {
    expect(Object.keys(TRANSLATIONS).filter((k) => k.includes('문의 창구는 팀 확정 후'))).toEqual([]);
  });
});

describe('계정 삭제 안내 내용', () => {
  it('모든 문단이 한국어·영어 짝이 있고 비어 있지 않다', () => {
    for (const section of ACCOUNT_DELETION_SECTIONS) {
      expect(section.title[0].length).toBeGreaterThan(0);
      expect(section.title[1].length).toBeGreaterThan(0);
      for (const [ko, en] of section.paragraphs) {
        expect(ko.length).toBeGreaterThan(0);
        expect(en.length).toBeGreaterThan(0);
      }
    }
  });

  it('앱 안 삭제 절차(DELETE 입력)와 이메일 요청, 처리 기한을 한국어·영어 둘 다 말한다', () => {
    const text = all(ACCOUNT_DELETION_SECTIONS);
    expect(text).toContain('DELETE');
    expect(text).toContain(SUPPORT_EMAIL);
    expect(text).toContain(`${DELETION_RESPONSE_DAYS}일 이내`);
    expect(text).toContain(`within ${DELETION_RESPONSE_DAYS} days`);
  });

  it('🔴 확인하지 못한 것을 약속하지 않는다 — 백업본·「즉시 완전 파기」 같은 말이 없다', () => {
    const text = all(ACCOUNT_DELETION_SECTIONS);
    expect(text).not.toMatch(/백업/);
    expect(text).not.toMatch(/backup/i);
    expect(text).not.toContain('완전히 파기');
  });
});

describe('계정 삭제 안내 화면', () => {
  it('로그인 없이 그려지고 주소·DELETE·「탈퇴한 사용자」가 보이며 초안 배너는 없다', () => {
    const view = render(
      <Providers>
        <AccountDeletion />
      </Providers>,
    );

    expect(view.getByText('계정 삭제 안내')).toBeTruthy();
    expect(view.getAllByText(new RegExp(SUPPORT_EMAIL.replace('.', '\\.'))).length).toBeGreaterThan(0);
    expect(view.getAllByText(/DELETE/).length).toBeGreaterThan(0);
    expect(view.getAllByText(/탈퇴한 사용자/).length).toBeGreaterThan(0);
    expect(view.queryByText('초안 · 팀 확정 예정')).toBeNull();
  });

  it('다른 법률 문서(개인정보 처리방침)에는 초안 배너가 그대로 있다 — 이 변경이 공용 화면의 기본을 바꾸지 않는다', () => {
    const Privacy = require('../../../app/legal/privacy').default;
    const view = render(
      <Providers>
        <Privacy />
      </Providers>,
    );

    expect(view.getByText('초안 · 팀 확정 예정')).toBeTruthy();
  });
});
