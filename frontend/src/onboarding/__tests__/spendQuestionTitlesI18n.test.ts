// 소비 성향 질문 — 일본어·중국어 화면에서 영어로 떨어지지 않게 — S15P21E201-1928(웹 10/2).
// 제목이 「평소/이번 여행」 두 가지로 만들어져 번역표에서 빠지기 쉽다. 화면에 나오는 한국어 원문 전부를 본다.
import { SPEND_HEADER, SPEND_QUESTIONS } from '@/onboarding/spendProfile';
import { TRANSLATIONS } from '@/i18n/translations';

const shown: string[] = [];
for (const scope of ['USER', 'TRIP'] as const) {
  shown.push(SPEND_HEADER[scope].titleKo, SPEND_HEADER[scope].bodyKo);
  for (const q of SPEND_QUESTIONS) {
    shown.push(q.titleKo(scope), q.skipLabelKo);
    for (const o of q.options) { shown.push(o.labelKo); if (o.descKo) shown.push(o.descKo); }
  }
}

it.each([...new Set(shown)])('🔴 「%s」 — 일본어·간체·번체 번역이 있다', (ko) => {
  const t = TRANSLATIONS[ko];
  expect(t?.ja && t.zhHans && t.zhHant).toBeTruthy();
});
