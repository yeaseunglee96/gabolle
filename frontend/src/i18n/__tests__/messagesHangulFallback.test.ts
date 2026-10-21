// 서버가 보낸 한국어 오류 문장 — 한국어가 아닌 화면에서 그대로 나오지 않게 — S15P21E201-1922(웹 10/2).
import { localizeMessage } from '@/i18n/messages';

const ko = (k: string) => k;
const en = (_k: string, e: string) => e;

it('🔴 서버의 「인증 정보가 올바르지 않습니다.」는 영어 화면에서 영어로', () => {
  expect(localizeMessage(en, '인증 정보가 올바르지 않습니다.')).toMatch(/^[\x20-\x7E]+$/);
});

it('🔴 표에 없는 한국어 문장도 한국어가 아닌 화면에는 한글이 남지 않는다', () => {
  expect(localizeMessage(en, '처음 보는 서버 문장입니다.')).not.toMatch(/[가-힣]/);
});

it('한국어 화면은 원문 그대로, 한글이 없는 문장도 그대로', () => {
  expect(localizeMessage(ko, '처음 보는 서버 문장입니다.')).toBe('처음 보는 서버 문장입니다.');
  expect(localizeMessage(en, 'Plain English from server')).toBe('Plain English from server');
});
