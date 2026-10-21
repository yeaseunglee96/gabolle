import {
  AREA_ENGLISH, AREA_TERMS, DIALECT_INTENT, LOCAL_INTENT, MY_TRIPS_INTENT, PHRASE_INTENT, PHRASE_TOPIC_TERMS, PLAN_INTENT,
  PREFERENCE_TERMS, TRANSLATE_INTENT, TRANSPORT_TERMS, mentions, peopleCount, prepareSpoken,
  type AreaCode, type PreferenceCode, type Terms,
} from '@/assistant/intentLexicon';
import type { PlanDraft } from '@/plan/PlanProvider';
import { getCurrentLanguage } from '@/i18n/languages';
import { pickLanguage } from '@/i18n/pick';

// 키워드 매칭은 한국어 입력을 원문 그대로 보고, 영어·일본어·중국어(간체·번체)·베트남어는 같은 뜻의 낱말을 더 본다
// (intentLexicon.ts) — S15P21E201-2055. reply/summary(응답 문구)는 UI 언어를 따라간다.
// 🔴 전에는 한국어 낱말만 알아서, 로그인 안 한 사용자가 「Haeundae food tips」를 쓰면 일반 안내만 돌아왔다
//    (갤럭시 S22 실기기, 2026-10-14). 한국어 판정은 그대로 두고 다른 언어 낱말을 「또는」으로 더했다 — 한국어 입력의 결과는 같다.
//
// 🔴 번역표를 본다 — S15P21E201-1517. 전에는 「영어가 아니면 한국어」로 골라서(서버용 언어는
//    ko|en 뿐이라) 일본어·중국어 사용자에게 이 답이 늘 영어로 나갔다. 표에 줄이 이미 있었는데도
//    못 썼다. 표에 없는 문구는 전과 같이 영어로 떨어진다(pickLanguage).
const t = (ko: string, en: string) => pickLanguage(getCurrentLanguage(), { ko, en });

/**
 * 비서가 안내할 수 있는 화면 주소.
 *
 * 서버가 내주는 다섯(`assistantApi.ts` 의 `ALLOWED_NAVIGATE_HREFS`)과, 서버 없이 도는
 * 아래 키워드 매처만 쓰는 '/explore' 를 합친 것이다.
 */
export type AssistantNavigatePath =
  | '/plan'
  | '/plan/basic'
  | '/trips'
  | '/explore'
  | '/field/translate'
  | '/field/transit'
  | '/field/exchange-rate'
  // 앱 안에서만 쓰는 이동(서버 허용 목록 ALLOWED_NAVIGATE_HREFS 에는 없다 — 사투리는 앱이 직접 알아듣는다, S15P21E201-1422).
  | '/field/dialect';

/**
 * 🔴 서버는 경로 뒤에 쿼리를 붙여 보낸다 — '/plan/basic?days=2' 처럼.
 * 경로만 받는 타입으로 두면 쿼리가 붙은 주소를 타입이 거부하고, 그걸 피하려고 캐스팅을
 * 끼워 넣게 된다. 쿼리가 따라올 수 있다는 것을 타입에 적어 둔다 (S15P21E201-1273).
 */
export type AssistantNavigateHref = AssistantNavigatePath | `${AssistantNavigatePath}?${string}`;

export type AssistantAction =
  | { kind: 'plan'; reply: string; summary: string[]; patch: Partial<PlanDraft> }
  | { kind: 'phrase'; reply: string; korean: string; pronunciation: string }
  | { kind: 'navigate'; reply: string; label: string; href: AssistantNavigateHref }
  | { kind: 'help'; reply: string };

// 한국어 낱말(앞)과 코드(뒤). 다른 언어 낱말은 intentLexicon.ts 가 같은 코드로 가지고 있다.
const AREAS: Array<[string, AreaCode]> = [['해운대', 'HAEUNDAE'], ['광안리', 'GWANGALLI'], ['송정', 'SONGJEONG'], ['남포동', 'NAMPO'], ['영도', 'YEONGDO'], ['서면', 'SEOMYEON']];
const PREFERENCES: Array<[string, PreferenceCode]> = [['맛집', 'FOOD'], ['카페', 'CAFE_HEALING'], ['바다', 'SEA_BEACH'], ['자연', 'NATURE_WALK'], ['역사', 'CULTURE_TEMPLE'], ['야경', 'CITY']];
// match = 한국어 낱말, other = 같은 뜻의 다른 언어 낱말(순서도 같다 — 먼저 맞는 문장이 이긴다)
const PHRASES = [
  { match: ['사진', '찍어'], other: PHRASE_TOPIC_TERMS.photo, korean: '사진 한 장 부탁드려도 될까요?', pronunciation: 'sajin han jang butakdeuryeodo doelkkayo?' },
  { match: ['화장실'], other: PHRASE_TOPIC_TERMS.toilet, korean: '화장실이 어디예요?', pronunciation: 'hwajangsiri eodiyeyo?' },
  { match: ['안 매운', '맵지 않'], other: PHRASE_TOPIC_TERMS.notSpicy, korean: '안 매운 음식으로 부탁드려요.', pronunciation: 'an maeun eumsigeuro butakdeuryeoyo.' },
  { match: ['감사'], other: PHRASE_TOPIC_TERMS.thanks, korean: '감사합니다.', pronunciation: 'gamsahamnida.' },
  { match: ['얼마', '가격'], other: PHRASE_TOPIC_TERMS.price, korean: '이거 얼마예요?', pronunciation: 'igeo eolmayeyo?' },
];

const includesAny = (text: string, words: string[]) => words.some((word) => text.includes(word));

export function understandAssistantMessage(raw: string): AssistantAction {
  const text = raw.trim();
  // 🔴 한국어 판정은 원문 그대로 하고, 다른 언어 낱말은 따로 다듬은 글(said)에서 찾아 「또는」으로 더한다 — S15P21E201-2055.
  //    그래서 한국어만 쓴 글은 전과 같은 결과가 나온다(시험이 옛 결과와 하나씩 맞춰 본다).
  const said = prepareSpoken(text);
  const saysAny = (korean: string[], other: Terms) => includesAny(text, korean) || mentions(said, other);
  if (saysAny(['문장', '한국어', '뭐라고', '말해', '표현'], PHRASE_INTENT)) {
    const phrase = PHRASES.find((item) => includesAny(text, item.match) || mentions(said, item.other));
    return phrase ? { kind: 'phrase', reply: t('현장에서 바로 보여주거나 들려줄 수 있게 준비했어요.', "I've got it ready to show or read aloud on the spot."), korean: phrase.korean, pronunciation: phrase.pronunciation } : { kind: 'navigate', reply: t('상황별 문장을 고를 수 있는 현장 도구로 안내할게요.', "I'll take you to the on-the-go tool where you can pick a phrase for your situation."), label: t('현장 도구 열기', 'Open on-the-go tools'), href: '/field/translate' };
  }
  // : 메뉴판 카메라 번역은 -907로 뺐다. "번역"이라고 해도 실제로 되는 것은
  // 상황별 한국어 문장뿐이라, 없는 기능을 약속하지 않고 그 사실을 그대로 말한다.
  if (includesAny(text.toLowerCase(), ['번역', '메뉴판', 'translate']) || mentions(said, TRANSLATE_INTENT)) return { kind: 'navigate', reply: t('메뉴판 사진 번역은 아직 준비 중이에요. 대신 상황별 한국어 문장은 바로 보여드릴 수 있어요.', "Menu photo translation isn't ready yet — but I can show you Korean phrases for your situation right away."), label: t('현장 도구 열기', 'Open on-the-go tools'), href: '/field/translate' };
  if (saysAny(['사투리', '부산말', '경상도', 'dialect'], DIALECT_INTENT)) return { kind: 'navigate', reply: t('부산 사투리 한마디를 진짜 부산 억양으로 들려드릴게요.', "Here's a word of Busan dialect in a real Busan accent."), label: t('부산 사투리 듣기', 'Hear Busan dialect'), href: '/field/dialect' };
  if (saysAny(['내 일정', '여행 목록', '만든 일정'], MY_TRIPS_INTENT)) return { kind: 'navigate', reply: t('저장한 여행 목록을 열어드릴게요.', "I'll open your saved trip list."), label: t('내 여행 보기', 'View my trips'), href: '/trips' };
  // : 갈래 개수는 GET /api/v1/places/facets 가 정한다 — 숫자를 박지 않는다.
  if (saysAny(['로컬', '야시장', '둘러보'], LOCAL_INTENT)) return { kind: 'navigate', reply: t('부산 로컬 스팟을 보여드릴게요.', "I'll show you local Busan spots."), label: t('로컬 탐색 열기', 'Open local exploring'), href: '/explore' };
  // 🔴 지역이나 취향만 말해도 일정 조건으로 받는다 — S15P21E201-1542. 「해운대 근처 맛집 알려줘」에
  //    「일정·여행…」 낱말이 없다고 일반 안내(「…도와드릴 수 있어요」)로 떨어졌다. 손님은 서버 AI 없이
  //    이 해석기만 쓰므로 그 한 줄이 대화의 전부였다. 아래 목록에서 찾은 것을 조건으로 보여주고 묻는다.
  const areaSaid = ([label, code]: [string, AreaCode]) => text.includes(label) || mentions(said, AREA_TERMS[code]);
  const preferenceSaid = ([key, code]: [string, PreferenceCode]) => text.includes(key) || mentions(said, PREFERENCE_TERMS[code]);
  const mentionsCondition = AREAS.some(areaSaid) || PREFERENCES.some(preferenceSaid);
  if (saysAny(['일정', '여행', '코스', '짜줘', '추천'], PLAN_INTENT) || mentionsCondition) {
    const patch: Partial<PlanDraft> = {}; const summary: string[] = [];
    const dates = text.match(/20\d{2}[-./]\d{1,2}[-./]\d{1,2}/g)?.map((value) => value.replace(/[./]/g, '-').split('-').map((part, index) => index ? part.padStart(2, '0') : part).join('-')) ?? [];
    if (dates[0]) { patch.startDate = dates[0]; summary.push(t(`출발 ${dates[0]}`, `Departs ${dates[0]}`)); }
    if (dates[1]) { patch.endDate = dates[1]; summary.push(t(`도착 ${dates[1]}`, `Returns ${dates[1]}`)); }
    // 인원 — 「2명」을 먼저 본다(한국어는 전과 같다). 없으면 「2 people」「2人」「2位」「2 người」 같은 다른 언어 표현.
    const people = text.match(/(\d+)\s*명/);
    const headcount = people ? Number(people[1]) : peopleCount(said);
    if (headcount !== null) { const count = Math.max(1, Math.min(20, headcount)); Object.assign(patch, { travelers: count, adults: count, children: 0 }); summary.push(t(`${count}명`, `${count} people`)); }
    if (saysAny(['대중교통', '버스', '지하철'], TRANSPORT_TERMS.TRANSIT)) { patch.transport = 'TRANSIT'; summary.push(t('대중교통', 'Public transit')); } else if (saysAny(['자차', '자동차', '렌터카', '렌트카'], TRANSPORT_TERMS.CAR)) { patch.transport = 'CAR'; summary.push(t('자동차', 'Car')); } else if (saysAny(['도보', '걸어서', '걷기'], TRANSPORT_TERMS.WALK)) { patch.transport = 'WALK'; summary.push(t('도보', 'On foot')); }
    // 지역 이름은 화면 언어로 적는다 — 영어 화면에서 「해운대」가 한글로 남지 않게. 한국어 화면은 전과 같다(번역표에 일본어·중국어·베트남어 이름이 있다).
    const areas = AREAS.filter(areaSaid); if (areas.length) { patch.travelAreas = areas.map(([, code]) => code); summary.push(areas.map(([label, code]) => t(label, AREA_ENGLISH[code])).join(' · ')); }
    const preferences = PREFERENCES.filter(preferenceSaid).map(([, value]) => value);
    if (preferences.length) { patch.preferences = preferences; patch.preferenceAnswerStatus = { category: 'SELECTED', atmosphere: 'UNKNOWN', locality: 'UNKNOWN', quietness: 'UNKNOWN', touristPreference: 'UNKNOWN', foodPreference: 'UNKNOWN' }; summary.push(t(`취향 ${preferences.length}개`, `${preferences.length} preferences`)); }
    return { kind: 'plan', patch, summary, reply: summary.length ? t('요청에서 아래 조건을 찾았어요. 확인 후 일정 만들기에 적용할게요.', "I found these conditions in your request. I'll apply them to trip planning once you confirm.") : t('날짜와 인원, 가고 싶은 지역이나 분위기를 조금 더 알려주세요.', 'Tell me a bit more about your dates, number of travelers, or the area/mood you want.') };
  }
  return { kind: 'help', reply: t('부산 일정 만들기, 한국어 현장 문장, 로컬 스팟 탐색, 내 여행 찾기를 도와드릴 수 있어요.', 'I can help you build a Busan itinerary, find on-the-go Korean phrases, explore local spots, or find your saved trips.') };
}

/**
 * 서버 AI 에 못 닿았을 때의 답 — S15P21E201-1749.
 *
 * 🔴 전에는 로컬 해석기(낱말만 보는 거친 도구)의 답을 그대로 보였다. 그 해석기는 대부분의 질문을
 *    못 알아듣고 「…를 도와드릴 수 있어요」라는 기본 안내로 떨어져서, 실기기에서 「자갈치 해산물
 *    추천」에 그 안내만 돌아왔다 — 실패했다는 말이 어디에도 없었다.
 *    알아들은 것(현장 문장·길 안내)은 그대로 쓰고, 못 알아들었을 때만 실패를 알린다.
 */
export function assistantUnavailable(raw: string): AssistantAction {
  const local = understandAssistantMessage(raw);
  if (local.kind !== 'help') return local;
  return { kind: 'help', reply: t('지금은 답을 받지 못했어요. 잠시 뒤 다시 물어봐 주세요.', "I couldn't get an answer just now. Please try again in a moment.") };
}
