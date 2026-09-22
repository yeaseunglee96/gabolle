// 동백이 호출에만 실제 언어를 싣는다 — S15P21E201-1427.
const mockApiRequest = jest.fn((..._args: unknown[]) => Promise.resolve({ reply: '네', action: null }));
jest.mock('@/api/client', () => ({ ...jest.requireActual('@/api/client'), apiRequest: (...args: unknown[]) => mockApiRequest(...args) }));

import { askAssistant } from '@/assistant/assistantApi';

beforeEach(() => mockApiRequest.mockClear());

it('일본어 사용자는 Accept-Language: ja — 앱 전체 기본값(ko/en)에 눌리지 않는다', async () => {
  await askAssistant('안녕', 'tok', [], 'ja');
  expect(mockApiRequest.mock.calls[0][1] as object).toMatchObject({ headers: { 'Accept-Language': 'ja' } });
});

it.each(['zh-Hans', 'zh-Hant'] as const)('%s 는 그 이름 그대로 — 서버가 zh-Hans/zh-Hant 를 알아듣는다', async (language) => {
  await askAssistant('안녕', 'tok', [], language);
  expect(mockApiRequest.mock.calls[0][1] as object).toMatchObject({ headers: { 'Accept-Language': language } });
});

it('언어를 안 주면 머리를 안 건드린다 — 옛 호출부 그대로', async () => {
  await askAssistant('안녕', 'tok');
  expect((mockApiRequest.mock.calls[0][1] as unknown as { headers?: unknown }).headers).toBeUndefined();
});
