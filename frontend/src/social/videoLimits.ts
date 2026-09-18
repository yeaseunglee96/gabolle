// 동영상 상한 — 웹과 기기가 같은 값을 쓴다.
//
// 🔴 두 벌로 두면 한쪽만 고쳐지고, 그 차이는 기기에서만 드러난다.

/**
 * 실제로 서버에 보내는 바이트의 상한 — **서버가 정한 값과 같다.**
 *
 * 서버 설정에 층이 적혀 있다: `앱 < 3MB < 4MB(multipart) < 4.5MB < 5MB(nginx)`.
 * 앱이 맨 안쪽이라, 여기서 막으면 사람은 올라가기를 기다린 끝에 실패를 보는 대신
 * 그 자리에서 이유를 듣는다.
 */
export const MAX_VIDEO_UPLOAD_BYTES = 3 * 1024 * 1024;

/**
 * 고르는 단계에서 바로 막는 길이.
 *
 * 🔴 크기는 대략 **화질 × 길이**다. 길이를 안 막으면 3분짜리를 골라 놓고 **몇 분을
 *    기다린 뒤에** 거절당한다. 줄이는 일은 기기에서 도는 실제 연산이라 공짜가 아니다.
 *
 * 3MB 를 30초로 나누면 초당 800kbps 쯤인데, 그게 볼 만한 화질의 바닥이다. 이 숫자는
 * 그 계산에서 나온 것이지 시안에 있던 값이 아니다 — 바꾸려면 여기 한 곳만 고친다.
 */
export const MAX_VIDEO_SECONDS = 30;

/** 사람에게 보여 줄 상한 — 문구와 실제 상한이 어긋나지 않게 여기서 한 번만 만든다. */
export const MAX_VIDEO_UPLOAD_LABEL = `${Math.round(MAX_VIDEO_UPLOAD_BYTES / (1024 * 1024))}MB`;
export const MAX_VIDEO_SECONDS_LABEL = `${MAX_VIDEO_SECONDS}초`;
