// 장소 제목 바로 아래 📍 줄에 무엇을 적나 — S15P21E201-1759.
//
// 🔴 기록의 지역 칸(story.region)은 장소를 고르면 「장소 이름 · 구」로 저장된다(예: 「황령산 · 남구」).
//    장소 제목이 이미 「황령산」을 크게 적으므로 그 아래 📍 줄에 그대로 쓰면 이름이 두 번 나온다.
//    Play 35 실기기에서 모든 기록이 그랬고, 구가 없는 「모모스」는 같은 글자가 두 줄로 섰다.
//    저장된 값은 건드리지 않는다 — 보여 줄 때 앞의 장소 이름만 뗀다.

const SEPARATOR = ' · ';

/** 장소 이름 뒤에 남는 지역. 남는 것이 없으면 null — 그 줄을 그리지 않는다. */
export function regionBesidePlace(region: string | null | undefined, placeName: string): string | null {
  const text = region?.trim();
  if (!text) return null;
  const name = placeName.trim();
  if (text === name) return null;
  if (text.startsWith(name + SEPARATOR)) {
    const rest = text.slice(name.length + SEPARATOR.length).trim();
    return rest || null;
  }
  return text;
}
