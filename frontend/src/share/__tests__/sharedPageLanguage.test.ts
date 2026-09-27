// 공유 링크 화면의 첫 언어 — S15P21E201-1777(고지혁 QA: 영어를 고른 사람에게도 한국어로 열렸다).
import { sharedPageInitialLanguage } from '@/share/sharedPageLanguage';

const base = { web: true, hydrated: true, hasEnteredApp: false, current: 'ko' as const };

describe('공유 링크 화면의 첫 언어', () => {
  it('🔴 처음 온 웹 방문자는 브라우저 언어를 따른다 — 영어 브라우저면 영어', () => {
    expect(sharedPageInitialLanguage({ ...base, browserLanguages: ['en-US', 'ko'] })).toBe('en');
    expect(sharedPageInitialLanguage({ ...base, browserLanguages: ['ja-JP'] })).toBe('ja');
    expect(sharedPageInitialLanguage({ ...base, browserLanguages: ['zh-TW'] })).toBe('zh-Hant');
  });

  it('모르는 브라우저 언어는 한국어가 아니라 영어로', () => {
    expect(sharedPageInitialLanguage({ ...base, browserLanguages: ['fr-FR'] })).toBe('en');
  });

  it('한국어 브라우저면 그대로 둔다', () => {
    expect(sharedPageInitialLanguage({ ...base, browserLanguages: ['ko-KR'] })).toBeNull();
  });

  it('🔴 앱에 들어온 적이 있으면 그 사람이 고른 언어를 건드리지 않는다', () => {
    expect(sharedPageInitialLanguage({ ...base, hasEnteredApp: true, browserLanguages: ['en-US'] })).toBeNull();
  });

  it('저장소를 다 읽기 전·앱(웹 아님)·브라우저 언어 없음이면 아무것도 안 한다', () => {
    expect(sharedPageInitialLanguage({ ...base, hydrated: false, browserLanguages: ['en-US'] })).toBeNull();
    expect(sharedPageInitialLanguage({ ...base, web: false, browserLanguages: ['en-US'] })).toBeNull();
    expect(sharedPageInitialLanguage({ ...base, browserLanguages: undefined })).toBeNull();
  });
});
