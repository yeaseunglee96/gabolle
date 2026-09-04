import type { PlanDraft } from '@/plan/PlanProvider';

export type AssistantAction =
  | { kind: 'plan'; reply: string; summary: string[]; patch: Partial<PlanDraft> }
  | { kind: 'phrase'; reply: string; korean: string; pronunciation: string }
  | { kind: 'navigate'; reply: string; label: string; href: '/field/translate' | '/trips' }
  | { kind: 'help'; reply: string };

const AREAS: Array<[string, string]> = [['해운대', 'HAEUNDAE'], ['광안리', 'GWANGALLI'], ['송정', 'SONGJEONG'], ['남포동', 'NAMPO'], ['영도', 'YEONGDO'], ['서면', 'SEOMYEON']];
const PREFERENCES: Array<[string, string]> = [['맛집', 'FOOD'], ['카페', 'CAFE_HEALING'], ['바다', 'SEA_BEACH'], ['자연', 'NATURE_WALK'], ['역사', 'CULTURE_TEMPLE'], ['야경', 'CITY']];
const PHRASES = [
  { match: ['사진', '찍어'], korean: '사진 한 장 부탁드려도 될까요?', pronunciation: 'sajin han jang butakdeuryeodo doelkkayo?' },
  { match: ['화장실'], korean: '화장실이 어디예요?', pronunciation: 'hwajangsiri eodiyeyo?' },
  { match: ['안 매운', '맵지 않'], korean: '안 매운 음식으로 부탁드려요.', pronunciation: 'an maeun eumsigeuro butakdeuryeoyo.' },
  { match: ['감사'], korean: '감사합니다.', pronunciation: 'gamsahamnida.' },
  { match: ['얼마', '가격'], korean: '이거 얼마예요?', pronunciation: 'igeo eolmayeyo?' },
];

const includesAny = (text: string, words: string[]) => words.some((word) => text.includes(word));

export function understandAssistantMessage(raw: string): AssistantAction {
  const text = raw.trim();
  if (includesAny(text, ['문장', '한국어', '뭐라고', '말해', '표현'])) {
    const phrase = PHRASES.find((item) => includesAny(text, item.match));
    return phrase ? { kind: 'phrase', reply: '현장에서 바로 보여주거나 들려줄 수 있게 준비했어요.', korean: phrase.korean, pronunciation: phrase.pronunciation } : { kind: 'navigate', reply: '상황별 문장을 고를 수 있는 현장 도구로 안내할게요.', label: '현장 말하기 열기', href: '/field/translate' };
  }
  if (includesAny(text.toLowerCase(), ['번역', '메뉴판', 'translate'])) return { kind: 'navigate', reply: '메뉴판이나 안내문 번역 기능으로 이동할 수 있어요.', label: '번역 도구 열기', href: '/field/translate' };
  if (includesAny(text, ['내 일정', '여행 목록', '만든 일정'])) return { kind: 'navigate', reply: '저장한 여행 목록을 열어드릴게요.', label: '내 여행 보기', href: '/trips' };
  if (includesAny(text, ['일정', '여행', '코스', '짜줘', '추천'])) {
    const patch: Partial<PlanDraft> = {}; const summary: string[] = [];
    const dates = text.match(/20\d{2}[-./]\d{1,2}[-./]\d{1,2}/g)?.map((value) => value.replace(/[./]/g, '-').split('-').map((part, index) => index ? part.padStart(2, '0') : part).join('-')) ?? [];
    if (dates[0]) { patch.startDate = dates[0]; summary.push(`출발 ${dates[0]}`); }
    if (dates[1]) { patch.endDate = dates[1]; summary.push(`도착 ${dates[1]}`); }
    const people = text.match(/(\d+)\s*명/);
    if (people) { const count = Math.max(1, Math.min(20, Number(people[1]))); Object.assign(patch, { travelers: count, adults: count, children: 0 }); summary.push(`${count}명`); }
    if (includesAny(text, ['대중교통', '버스', '지하철'])) { patch.transport = 'TRANSIT'; summary.push('대중교통'); } else if (includesAny(text, ['자차', '자동차', '렌터카', '렌트카'])) { patch.transport = 'CAR'; summary.push('자동차'); } else if (includesAny(text, ['도보', '걸어서', '걷기'])) { patch.transport = 'WALK'; summary.push('도보'); }
    const areas = AREAS.filter(([label]) => text.includes(label)); if (areas.length) { patch.travelAreas = areas.map(([, code]) => code); summary.push(areas.map(([label]) => label).join(' · ')); }
    const preferences = PREFERENCES.filter(([key]) => text.includes(key)).map(([, value]) => value);
    if (preferences.length) { patch.preferences = preferences; patch.preferenceAnswerStatus = { category: 'SELECTED', atmosphere: 'UNKNOWN', locality: 'UNKNOWN', quietness: 'UNKNOWN', touristPreference: 'UNKNOWN', foodPreference: 'UNKNOWN' }; summary.push(`취향 ${preferences.length}개`); }
    return { kind: 'plan', patch, summary, reply: summary.length ? '요청에서 아래 조건을 찾았어요. 확인 후 일정 만들기에 적용할게요.' : '날짜와 인원, 가고 싶은 지역이나 분위기를 조금 더 알려주세요.' };
  }
  return { kind: 'help', reply: '부산 일정 만들기, 한국어 현장 문장, 메뉴 번역, 내 여행 찾기를 도와드릴 수 있어요.' };
}
