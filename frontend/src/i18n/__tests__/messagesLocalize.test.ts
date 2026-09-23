// 일본어·중국어 화면에 서버 연결 오류가 영어로 나오던 것 — S15P21E201-1521.
//
// 🔴 api/client.ts 는 연결 실패·5xx 문장을 API 언어로 고르는데, 일본어·중국어 화면의 API 언어는
//    `en` 이다. 그래서 문장이 영어로 올라왔고, localizeMessage 는 한국어 원문만 열쇠로 찾아서
//    영어를 그대로 흘려보냈다.
import type { LanguageCode } from '@/i18n/languages';
import { localizeMessage } from '@/i18n/messages';
import { pickLanguage } from '@/i18n/pick';

const txFor = (language: LanguageCode) => (ko: string, en: string) => pickLanguage(language, { ko, en });

const UNAVAILABLE_KO = '서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.';
const UNAVAILABLE_EN = 'The server is unavailable. Please try again shortly.';
const SERVER_KO = '서버가 잠시 응답하지 못했어요. 잠시 후 다시 시도해 주세요. (HTTP 503)';
const SERVER_EN = 'The server could not handle this just now. Please try again shortly. (HTTP 503)';

describe('영어로 던져진 오류 문장', () => {
  it('🔴 일본어·중국어 화면에서 영어로 남지 않는다', () => {
    expect(localizeMessage(txFor('ja'), UNAVAILABLE_EN)).toBe('サーバーに接続できません。しばらくしてからもう一度お試しください。');
    expect(localizeMessage(txFor('zh-Hans'), UNAVAILABLE_EN)).toBe('无法连接服务器。请稍后再试。');
    expect(localizeMessage(txFor('zh-Hant'), UNAVAILABLE_EN)).toBe('無法連線伺服器。請稍後再試。');
  });

  it('🔴 5xx 문장은 상태 번호를 지킨 채 번역된다', () => {
    expect(localizeMessage(txFor('ja'), SERVER_EN)).toBe('サーバーが一時的に応答できませんでした。しばらくしてからもう一度お試しください。(HTTP 503)');
    expect(localizeMessage(txFor('zh-Hans'), SERVER_KO)).toBe('服务器暂时无法响应。请稍后再试。(HTTP 503)');
  });

  it('영어·한국어 화면은 전과 같다', () => {
    expect(localizeMessage(txFor('en'), UNAVAILABLE_EN)).toBe(UNAVAILABLE_EN);
    expect(localizeMessage(txFor('en'), UNAVAILABLE_KO)).toBe(UNAVAILABLE_EN);
    expect(localizeMessage(txFor('en'), SERVER_KO)).toBe(SERVER_EN);
    expect(localizeMessage(txFor('ko'), UNAVAILABLE_KO)).toBe(UNAVAILABLE_KO);
    expect(localizeMessage(txFor('ko'), SERVER_KO)).toBe(SERVER_KO);
  });

  it('표에 없는 문장(서버가 보낸 것 등)은 그대로 둔다', () => {
    expect(localizeMessage(txFor('ja'), 'Something the server said (code 42)')).toBe('Something the server said (code 42)');
    expect(localizeMessage(txFor('ja'), '')).toBe('');
  });
});
