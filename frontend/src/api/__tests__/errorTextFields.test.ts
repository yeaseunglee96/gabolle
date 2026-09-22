// 서버 원문·칸 이름·오류 코드가 화면에 그대로 새던 것 — S15P21E201-1342.
//
// 🔴 2026-09-21 실기(SM-G973N, versionCode 25). 탭바로 여행 만들기에 들어가 필수 질문을
//    답하고 「지금 이대로 만들기」를 눌렀더니 이렇게 떴다.
//
//      서버가 이 칸을 받지 못했어요 — originLat: 출발지 좌표가 없다.
//      목록에서 출발지를 골라 주세요 (TRIP_VALIDATION_FAILED)
//
//    한 줄에 셋이 섞여 있다 — 우리 머리말, 서버 원문, 내부 칸 이름과 오류 코드.
//    `originLat` 과 `TRIP_VALIDATION_FAILED` 는 사람에게 할 말이 아니고, 서버 원문은
//    우리가 언어를 못 고른다(일본어 사용자에게도 한국어로 나간다).
import { ApiClientError } from '@/api/client';
import { readableApiError } from '@/api/errorText';

function rejected(fields: string[], code = 'TRIP_VALIDATION_FAILED') {
  return new ApiClientError('입력한 조건 중 서버가 받지 못한 것이 있어요.', code, 400, fields);
}

describe('서버가 짚은 칸을 우리 문장으로 바꾼다', () => {
  it('🔴 출발지 — 칸 이름도 오류 코드도 안 보인다', () => {
    const text = readableApiError(rejected(['originLat: 출발지 좌표가 없다. 목록에서 출발지를 골라 주세요']), true);

    expect(text).toBe('출발지를 아직 안 골랐어요. 홈에서 출발지를 고르면 일정을 만들 수 있어요.');
    expect(text).not.toContain('originLat');
    expect(text).not.toContain('TRIP_VALIDATION_FAILED');
  });

  it('🔴 영어로 물으면 영어로 답한다 — 서버 원문을 쓰면 못 하는 일이다', () => {
    const text = readableApiError(rejected(['originLat: 출발지 좌표가 없다']), false);

    expect(text).toBe('No starting point yet. Pick one on the home screen and we can build your trip.');
    // 서버가 준 한국어가 섞여 나가면 안 된다.
    expect(/[가-힣]/.test(text)).toBe(false);
  });

  it('위도·경도가 함께 와도 한 번만 말한다 — 한 가지 문제다', () => {
    const text = readableApiError(rejected([
      'originLat: 출발지 좌표가 없다',
      'originLng: 출발지 좌표가 없다',
    ]), true);

    expect(text).toBe('출발지를 아직 안 골랐어요. 홈에서 출발지를 고르면 일정을 만들 수 있어요.');
  });

  it('여러 칸이 아는 것이면 나란히 말한다', () => {
    const text = readableApiError(rejected([
      'startDate: 가는 날이 없다',
      'finishDate: 오는 날이 없다',
    ]), true);

    expect(text).toContain('가는 날을 아직 안 정했어요.');
    expect(text).toContain('오는 날을 아직 안 정했어요.');
  });

  it('🔴 모르는 칸이 섞이면 예전처럼 둔다 — 단서까지 지우면 아무도 원인을 못 찾는다', () => {
    const text = readableApiError(rejected([
      'originLat: 출발지 좌표가 없다',
      'wobble: 처음 보는 칸',
    ]), true);

    expect(text).toContain('wobble');
    expect(text).toContain('TRIP_VALIDATION_FAILED');
  });

  it('칸 이름 구분자가 없으면 아는 칸으로 치지 않는다', () => {
    const text = readableApiError(rejected(['출발지가 없어요']), true);

    expect(text).toContain('출발지가 없어요');
    expect(text).toContain('TRIP_VALIDATION_FAILED');
  });

  it('이 시험이 실제로 무언가를 가른다 — 아는 칸과 모르는 칸이 다르게 나온다', () => {
    const known = readableApiError(rejected(['originLat: x']), true);
    const unknown = readableApiError(rejected(['wobble: x']), true);

    expect(known).not.toEqual(unknown);
    expect(known).not.toContain('(');
  });
});
