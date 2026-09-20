/**
 * 순수 모듈이 던지는 한국어 문장을 화면 언어로 — S15P21E201-1361.
 *
 * `src/social/stories.ts` 같은 순수 모듈은 화면 밖에서 돌아 `tx` 가 없다. 그래서 실패 이유를
 * 한국어 문장 그대로 `message` 에 담아 올리고, 화면은 그것을 `{result.message}` 로 그대로 그렸다.
 * 영어·일본어 화면에서도 그 줄만 한국어였다 — 「서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.」.
 *
 * 고치는 방법: 문장을 던지는 쪽은 그대로 두고(한국어 원문이 곧 열쇠다 — 번역표와 같은 규칙),
 * **그리는 쪽이 이 함수를 거친다.** 영어는 여기 표에서, 일본어·중국어는 번역표에서 온다.
 * 표에 없는 문장(서버가 보낸 것 등)은 그대로 돌려준다.
 */
type Tx = (ko: string, en: string) => string;

export const MESSAGE_EN: Record<string, string> = {
  '서버 응답이 늦어 요청을 마쳤어요. 잠시 후 다시 시도해 주세요.': 'The server took too long to respond. Please try again shortly.',
  '서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.': 'The server is unavailable. Please try again shortly.',
  '요청을 취소했어요.': 'The request was cancelled.',
  '요청이 취소됐어요.': 'The request was cancelled.',
  '요청을 처리하지 못했어요.': 'We could not complete that request.',
  '사진 파일 읽기가 중단됐어요': 'Reading the photo file was interrupted',
  '로그인이 취소되었어요.': 'Sign-in was cancelled.',
  '소셜 로그인을 완료하지 못했어요.': 'Could not complete social sign-in.',
  '소셜 로그인 요청이 거절되었어요.': 'The social sign-in request was rejected.',
  '로그인 응답을 확인할 수 없어요.': 'Could not verify the sign-in response.',
  '로컬 탐색 API가 아직 준비되지 않았어요.': 'The local explore API is not ready yet.',
  '갈래 조회 API가 아직 준비되지 않았어요.': 'The category API is not ready yet.',
  '일정의 장소 위치를 한 곳도 받지 못했어요.': 'None of the places in this itinerary came with a location.',
  '경로 조회 API가 아직 준비되지 않았어요.': 'The route API is not ready yet.',
  '다른 변경이 먼저 반영됐어요. 최신 일정을 불러와 다시 시도해 주세요.': 'Another change was applied first. Reload the latest itinerary and try again.',
  '일정 API가 아직 준비되지 않았어요.': 'The itinerary API is not ready yet.',
  '순서 목록이 이 날짜의 장소와 맞지 않아요. 새로고침 후 다시 시도해 주세요.': 'The order list does not match this day’s places. Refresh and try again.',
  '고정된 장소는 자리를 옮길 수 없어요. 고정을 먼저 풀어 주세요.': 'Locked places cannot be moved. Unlock them first.',
  '되돌릴 변경 사항이 없어요.': 'There is nothing to undo.',
  '남은 일정이 하루 안에 다 들어가지 않아요. 넘치는 방문지를 먼저 확인해 주세요.': 'The remaining stops do not fit in one day. Check the overflowing places first.',
  '일정을 만드는 중 문제가 생겼어요. 잠시 후 다시 시도해 주세요.': 'Something went wrong while building your itinerary. Please try again shortly.',
  '일정 생성 서버가 아직 준비되지 않았어요. 입력한 조건은 그대로 유지됩니다.': 'The itinerary server is not ready yet. Your conditions are kept as entered.',
  '리뷰 API가 아직 준비되지 않았어요.': 'The review API is not ready yet.',
  '공유 일정을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.': 'Could not load the shared itinerary. Please try again shortly.',
  '기록 피드 API가 아직 준비되지 않았어요.': 'The feed API is not ready yet.',
  '동영상이 너무 커요. 더 짧은 영상으로 올려주세요.': 'The video is too large. Please upload a shorter clip.',
  'mp4 동영상만 올릴 수 있어요.': 'Only mp4 videos can be uploaded.',
  '사진이 너무 커요. 3MB 이하로 올려주세요.': 'The photo is too large. Please keep it under 3MB.',
  'JPEG, PNG, WebP 사진만 올릴 수 있어요.': 'Only JPEG, PNG and WebP photos can be uploaded.',
  '자기 자신은 팔로우할 수 없어요.': 'You cannot follow yourself.',
  '웹에서는 동영상을 줄일 수 없어요': 'Videos cannot be compressed on the web',
  '이 여행을 찾을 수 없어요.': 'Could not find this trip.',
  '여행 이름을 처리하지 못했어요.': 'Could not process the trip name.',
  '이름 후보의 형식이 예상과 달라요.': 'The name suggestions came in an unexpected format.',
  '내 여행 목록 API가 아직 준비되지 않았어요.': 'The trips API is not ready yet.',
  '여행 목록을 불러오지 못했어요.': 'Could not load your trips.',
  '여행 목록의 형식이 예상과 달라요.': 'The trip list came in an unexpected format.',
  '일정 목록의 형식이 예상과 달라요.': 'The itinerary list came in an unexpected format.',
  '날씨 API가 아직 준비되지 않았어요.': 'The weather API is not ready yet.',
  '기상청 응답을 받지 못했어요.': 'No response from the weather service.',
  '조건에 맞는 장소를 찾지 못했어요. 날짜·예산·취향 조건을 조금 넓혀서 다시 시도해 주세요.': 'No places matched your conditions. Loosen the dates, budget or preferences a little and try again.',
  '일정을 아직 못 불러왔어요.': 'The itinerary has not loaded yet.',
  '(탈퇴한 사용자)': '(deleted user)',
};

/** 화면이 `message` 를 그릴 때 거친다. 표에 없으면 그대로. */
export function localizeMessage(tx: Tx, text: string | null | undefined): string {
  if (!text) return '';
  const en = MESSAGE_EN[text];
  return en ? tx(text, en) : text;
}
