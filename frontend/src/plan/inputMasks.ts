// 자리 수가 정해진 입력칸에서 구분자를 앱이 넣는다 — S15P21E201-1087.

/** 숫자만 남긴다. 붙여넣기로 들어온 공백·기호도 여기서 걷힌다. */
function digitsOf(value: string): string {
  return value.replace(/\D/g, '');
}

/** `HH:MM` 로 만든다. `0900` → `09:00`. */
export function maskTimeInput(value: string): string {
  const digits = digitsOf(value).slice(0, 4);
  if (digits.length <= 2) return digits;
  return `${digits.slice(0, 2)}:${digits.slice(2)}`;
}

/** 콜론과 같은 이유로, 하이픈도 그 칸의 첫 숫자가 들어온 뒤에만 나타난다. */
export function maskDateInput(value: string): string {
  const digits = digitsOf(value).slice(0, 8);
  if (digits.length <= 4) return digits;
  if (digits.length <= 6) return `${digits.slice(0, 4)}-${digits.slice(4)}`;
  return `${digits.slice(0, 4)}-${digits.slice(4, 6)}-${digits.slice(6)}`;
}
