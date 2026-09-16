// 자리 수가 정해진 입력칸에서 구분자를 앱이 넣는다 — S15P21E201-1087.
//
// 토스 UI/UX 가이드 벤치마킹(2026-09-16). 하루 여행 시간은 `09:00`, 축제 조회는 `YYYY-MM-DD`
// 인데, 두 칸 다 QWERTY 자판이 떴고 구분자도 사용자가 쳤다. 글자 다섯 개를 넣는 데 자판
// 전환이 두 번(숫자 자판 → 기호 자판) 필요했다.
//
// 🔴 여기 있는 것은 **순수 함수**다. 화면이 아니라 이 파일이 시험 대상이 되게 하려는 것이다 —
//    입력 보조는 "지우다가 갇힌다" 같은 것이 조용히 생기는 자리라, 화면을 띄우지 않고도
//    되돌아가며 확인할 수 있어야 한다.
//
// 🔴 구분자를 "붙였다 떼는" 상태를 따로 들고 있지 않다. 들어온 값에서 **숫자만 뽑아 다시
//    끼우는** 방식이라 몇 번을 넣어도 결과가 같고(멱등), 백스페이스도 저절로 풀린다.
//    앞선 값을 받아 "방금 구분자를 지웠나" 를 따지는 코드가 흔한데, 그 분기는 여기서
//    필요 없다 — 구분자로 끝나는 값을 애초에 만들지 않기 때문이다(아래 참고).

/** 숫자만 남긴다. 붙여넣기로 들어온 공백·기호도 여기서 걷힌다. */
function digitsOf(value: string): string {
  return value.replace(/\D/g, '');
}

/**
 * `HH:MM` 로 만든다. `0900` → `09:00`.
 *
 * 🔴 두 자리까지는 콜론을 **안 붙인다.** 붙이면 `09:` 가 되는데, 거기서 백스페이스를 누르면
 * 콜론이 지워지고 앱이 다시 붙여서 제자리걸음이 된다 — 지우다 갇힌다. 콜론은 세 번째 숫자가
 * 들어온 뒤에만 나타난다.
 */
export function maskTimeInput(value: string): string {
  const digits = digitsOf(value).slice(0, 4);
  if (digits.length <= 2) return digits;
  return `${digits.slice(0, 2)}:${digits.slice(2)}`;
}

/**
 * `YYYY-MM-DD` 로 만든다. `20260916` → `2026-09-16`.
 *
 * 콜론과 같은 이유로, 하이픈도 그 칸의 첫 숫자가 들어온 뒤에만 나타난다.
 */
export function maskDateInput(value: string): string {
  const digits = digitsOf(value).slice(0, 8);
  if (digits.length <= 4) return digits;
  if (digits.length <= 6) return `${digits.slice(0, 4)}-${digits.slice(4)}`;
  return `${digits.slice(0, 4)}-${digits.slice(4, 6)}-${digits.slice(6)}`;
}
