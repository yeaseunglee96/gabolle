// 서버가 기능을 모를 때 사용자에게 개발자 말을 보이지 않는다 — S15P21E201-1664.
//
// 🔴 이 시험이 지키는 것(사용자 결정): 404·501 이면 「내 여행 목록 API가 아직 준비되지 않았어요」처럼 「API」라는 말이
//    화면에 떴다. 한 문장(「지금은 이 정보를 불러올 수 없어요. 잠시 뒤 다시 시도해 주세요.」)으로 모았다.
//    ① 사용자에게 가는 문구의 목록(번역표·영어 표)에 「API」가 든 문구가 없다 — 다시 들어와도 잡힌다.
//    ② 실제로 404 를 받으면 그 한 문장이 온다(예: 내 여행 목록).
import { ApiClientError } from '@/api/client';
import { UNAVAILABLE_MESSAGE } from '@/api/errorText';
import { MESSAGE_EN } from '@/i18n/messages';
import { TRANSLATIONS } from '@/i18n/translations';

jest.mock('@/api/client', () => ({ ...jest.requireActual('@/api/client'), apiRequest: jest.fn() }));
const { apiRequest } = jest.requireMock('@/api/client') as { apiRequest: jest.Mock };

import { loadTrips } from '@/trip/trips';

describe('사용자에게 보이는 말에 「API」가 없다', () => {
  it('🔴 번역표와 영어 표 어디에도 없다', () => {
    const korean = Object.keys(TRANSLATIONS).filter((ko) => /API/.test(ko));
    const english = Object.entries(MESSAGE_EN).filter(([ko, en]) => /API/.test(ko) || /\bAPI\b/.test(en)).map(([ko]) => ko);
    expect(korean).toEqual([]);
    expect(english).toEqual([]);
  });

  it('🔴 서버가 기능을 모르면(404) 그 한 문장이 온다', async () => {
    apiRequest.mockRejectedValueOnce(new ApiClientError('No static resource', 'NOT_FOUND', 404));
    await expect(loadTrips('token')).resolves.toMatchObject({ state: 'unavailable', message: UNAVAILABLE_MESSAGE });
    expect(UNAVAILABLE_MESSAGE).toBe('지금은 이 정보를 불러올 수 없어요. 잠시 뒤 다시 시도해 주세요.');
    expect(MESSAGE_EN[UNAVAILABLE_MESSAGE]).toBe("We can't load this right now. Please try again in a moment.");
  });
});
