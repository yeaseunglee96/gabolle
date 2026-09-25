// 알림 본문의 여행 이름 따옴표 — S15P21E201-1711 (조율 세션 결정).
//
// 🔴 언어와 상관없이 한국어 꺾쇠로 감쌌다 — 영어판 「「부산 가을 바다 2박 3일」 — Jiwoo added a stop.」
//    영어·중국어판은 “…”, 한국어·일본어판은 「…」(일본어는 「」가 원래 쓰는 부호). 사람이 쓴 여행 이름은 그대로.
import { pickLanguage } from '@/i18n/pick';

import { noticeCopy, type ActivityNotice } from './activityFeed';

const notice = (over: Partial<ActivityNotice>): ActivityNotice => ({ id: 'it1:1', tripId: 't1', itineraryId: 'it1', tripTitle: '부산 가을 바다 2박 3일', operation: 'ADD_ITEM', actorName: 'Jiwoo', isMe: false, at: '2026-09-21T10:00:00Z', warningCodes: [], ...over });
const tx = (language: Parameters<typeof pickLanguage>[0]) => (ko: string, en: string) => pickLanguage(language, { ko, en });

describe('알림 본문의 여행 이름 따옴표', () => {
  it('영어판은 “…”', () => {
    expect(noticeCopy(notice({}), tx('en')).body).toBe('“부산 가을 바다 2박 3일” — Jiwoo added a stop.');
    expect(noticeCopy(notice({ operation: 'CREATE', isMe: true }), tx('en')).body).toBe('“부산 가을 바다 2박 3일” — Take a look and save it.');
  });

  it('한국어판은 「…」 그대로', () => {
    expect(noticeCopy(notice({}), tx('ko')).body).toBe('「부산 가을 바다 2박 3일」 — Jiwoo님이 장소를 더했어요.');
  });

  it('일본어판은 「…」, 중국어판은 “…”', () => {
    expect(noticeCopy(notice({ operation: 'SOMETHING_NEW' as ActivityNotice['operation'] }), tx('ja')).body).toBe('「부산 가을 바다 2박 3일」');
    expect(noticeCopy(notice({ operation: 'SOMETHING_NEW' as ActivityNotice['operation'] }), tx('zh-Hans')).body).toBe('“부산 가을 바다 2박 3일”');
    expect(noticeCopy(notice({ operation: 'SOMETHING_NEW' as ActivityNotice['operation'] }), tx('zh-Hant')).body).toBe('“부산 가을 바다 2박 3일”');
  });

  it('여행 이름에 꺾쇠가 들어 있어도 이름은 건드리지 않는다', () => {
    expect(noticeCopy(notice({ tripTitle: '「엄마」와 부산', operation: 'SOMETHING_NEW' as ActivityNotice['operation'] }), tx('en')).body).toBe('“「엄마」와 부산”');
  });
});
