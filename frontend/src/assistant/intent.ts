import type { PlanDraft } from '@/plan/PlanProvider';
import { getApiLanguage } from '@/api/client';

// 키워드 매칭은 한국어 입력만 인식한다 — reply/summary(응답 문구)는 UI 언어를 따라가지만
// 영어로 입력해도 이 매처 자체는 아직 반응하지 않는다. 별도 범위(영어 입력 인식)로 남겨 둔다.
const t = (ko: string, en: string) => (getApiLanguage() === 'en' ? en : ko);

export type AssistantAction =
  | { kind: 'plan'; reply: string; summary: string[]; patch: Partial<PlanDraft> }
  | { kind: 'phrase'; reply: string; korean: string; pronunciation: string }
  | { kind: 'navigate'; reply: string; label: string; href: '/field/translate' | '/trips' | '/explore' | '/plan' }
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
    return phrase ? { kind: 'phrase', reply: t('현장에서 바로 보여주거나 들려줄 수 있게 준비했어요.', "I've got it ready to show or read aloud on the spot."), korean: phrase.korean, pronunciation: phrase.pronunciation } : { kind: 'navigate', reply: t('상황별 문장을 고를 수 있는 현장 도구로 안내할게요.', "I'll take you to the on-the-go tool where you can pick a phrase for your situation."), label: t('현장 도구 열기', 'Open on-the-go tools'), href: '/field/translate' };
  }
  // : 메뉴판 카메라 번역은 -907로 뺐다. "번역"이라고 해도 실제로 되는 것은
  // 상황별 한국어 문장뿐이라, 없는 기능을 약속하지 않고 그 사실을 그대로 말한다.
  if (includesAny(text.toLowerCase(), ['번역', '메뉴판', 'translate'])) return { kind: 'navigate', reply: t('메뉴판 사진 번역은 아직 준비 중이에요. 대신 상황별 한국어 문장은 바로 보여드릴 수 있어요.', "Menu photo translation isn't ready yet — but I can show you Korean phrases for your situation right away."), label: t('현장 도구 열기', 'Open on-the-go tools'), href: '/field/translate' };
  if (includesAny(text, ['내 일정', '여행 목록', '만든 일정'])) return { kind: 'navigate', reply: t('저장한 여행 목록을 열어드릴게요.', "I'll open your saved trip list."), label: t('내 여행 보기', 'View my trips'), href: '/trips' };
  // : 갈래 개수는 GET /api/v1/places/facets 가 정한다 — 숫자를 박지 않는다.
  if (includesAny(text, ['로컬', '야시장', '둘러보'])) return { kind: 'navigate', reply: t('부산 로컬 스팟을 보여드릴게요.', "I'll show you local Busan spots."), label: t('로컬 탐색 열기', 'Open local exploring'), href: '/explore' };
  if (includesAny(text, ['일정', '여행', '코스', '짜줘', '추천'])) {
    const patch: Partial<PlanDraft> = {}; const summary: string[] = [];
    const dates = text.match(/20\d{2}[-./]\d{1,2}[-./]\d{1,2}/g)?.map((value) => value.replace(/[./]/g, '-').split('-').map((part, index) => index ? part.padStart(2, '0') : part).join('-')) ?? [];
    if (dates[0]) { patch.startDate = dates[0]; summary.push(t(`출발 ${dates[0]}`, `Departs ${dates[0]}`)); }
    if (dates[1]) { patch.endDate = dates[1]; summary.push(t(`도착 ${dates[1]}`, `Returns ${dates[1]}`)); }
    const people = text.match(/(\d+)\s*명/);
    if (people) { const count = Math.max(1, Math.min(20, Number(people[1]))); Object.assign(patch, { travelers: count, adults: count, children: 0 }); summary.push(t(`${count}명`, `${count} people`)); }
    if (includesAny(text, ['대중교통', '버스', '지하철'])) { patch.transport = 'TRANSIT'; summary.push(t('대중교통', 'Public transit')); } else if (includesAny(text, ['자차', '자동차', '렌터카', '렌트카'])) { patch.transport = 'CAR'; summary.push(t('자동차', 'Car')); } else if (includesAny(text, ['도보', '걸어서', '걷기'])) { patch.transport = 'WALK'; summary.push(t('도보', 'On foot')); }
    const areas = AREAS.filter(([label]) => text.includes(label)); if (areas.length) { patch.travelAreas = areas.map(([, code]) => code); summary.push(areas.map(([label]) => label).join(' · ')); }
    const preferences = PREFERENCES.filter(([key]) => text.includes(key)).map(([, value]) => value);
    if (preferences.length) { patch.preferences = preferences; patch.preferenceAnswerStatus = { category: 'SELECTED', atmosphere: 'UNKNOWN', locality: 'UNKNOWN', quietness: 'UNKNOWN', touristPreference: 'UNKNOWN', foodPreference: 'UNKNOWN' }; summary.push(t(`취향 ${preferences.length}개`, `${preferences.length} preferences`)); }
    return { kind: 'plan', patch, summary, reply: summary.length ? t('요청에서 아래 조건을 찾았어요. 확인 후 일정 만들기에 적용할게요.', "I found these conditions in your request. I'll apply them to trip planning once you confirm.") : t('날짜와 인원, 가고 싶은 지역이나 분위기를 조금 더 알려주세요.', 'Tell me a bit more about your dates, number of travelers, or the area/mood you want.') };
  }
  return { kind: 'help', reply: t('부산 일정 만들기, 한국어 현장 문장, 로컬 스팟 탐색, 내 여행 찾기를 도와드릴 수 있어요.', 'I can help you build a Busan itinerary, find on-the-go Korean phrases, explore local spots, or find your saved trips.') };
}
