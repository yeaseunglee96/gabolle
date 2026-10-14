// 로그인 안 한 사용자의 챗봇이 영어·일본어·중국어·베트남어를 못 알아듣던 것 — S15P21E201-2055.
//
// 🔴 갤럭시 S22 실기기(앱 1.0.3, 2026-10-14): 챗봇에 「Haeundae food tips」를 쓰면 일반 안내(「…도와드릴 수 있어요」)만 돌아왔다.
//    손님은 서버 AI 없이 앱 안 해석기(understandAssistantMessage — 낱말만 보고 동작을 고르는 규칙 함수)만 쓰는데,
//    그 해석기가 한국어 낱말만 알았다. 앱은 ko·en·ja·zh-Hans(간체)·zh-Hant(번체)·vi(베트남어) 여섯 언어를 지원한다.
//
// 이 시험이 지키는 것 셋.
//   ① 다른 언어로 써도 한국어와 «같은 동작»이 나온다 — 지역·취향·일정/추천·문장/번역·사투리·로컬·내 일정·이동 수단·인원
//   ② 🔴 한국어로만 쓴 글은 «옛 해석기와 한 줄씩 같다» — 아래 GOLDEN 은 이 변경 전 코드를 돌려 받아 둔 결과다
//   ③ 거짓으로 걸리지 않는다 — 「great」의 「eat」, 「planet」의 「plan」, 베트남어 「chưa」(아직)의 「chùa」(절)
import { understandAssistantMessage, type AssistantAction } from '@/assistant/intent';
import { compileTerms, mentions, peopleCount, prepareSpoken } from '@/assistant/intentLexicon';
import { setCurrentLanguage } from '@/i18n/languages';

afterEach(() => setCurrentLanguage('ko'));

/** 시험이 비교하는 모양 — 답 문구(reply)는 화면 언어에 따라 달라서 뺀다. */
function signature(action: AssistantAction): Record<string, unknown> {
  if (action.kind === 'plan') return { kind: action.kind, patch: { ...action.patch, preferenceAnswerStatus: action.patch.preferenceAnswerStatus?.category }, summary: action.summary };
  if (action.kind === 'navigate') return { kind: action.kind, href: action.href };
  if (action.kind === 'phrase') return { kind: action.kind, korean: action.korean };
  return { kind: action.kind };
}

function asPlan(text: string): Extract<AssistantAction, { kind: 'plan' }> {
  const action = understandAssistantMessage(text);
  if (action.kind !== 'plan') throw new Error(`「${text}」→ plan 이어야 하는데 ${action.kind}`);
  return action;
}

const where = (text: string): string => {
  const action = understandAssistantMessage(text);
  return action.kind === 'navigate' ? action.href : action.kind;
};

// ── ② 한국어는 그대로 ──────────────────────────────────────────────────────────────────────────────────────
// 변경 전 understandAssistantMessage 를 한국어 화면(ko)으로 돌려 받은 결과. 영어 낱말이 섞인 옛 입력 둘(translate · dialect)도 들어 있다.
const GOLDEN: Array<[string, Record<string, unknown>]> = [
  ['사진 부탁할 때 한국어 문장 알려줘', { kind: 'phrase', korean: '사진 한 장 부탁드려도 될까요?' }],
  ['화장실 어디예요 한국어로 뭐라고 해', { kind: 'phrase', korean: '화장실이 어디예요?' }],
  ['안 매운 걸로 주문하고 싶어 표현 알려줘', { kind: 'phrase', korean: '안 매운 음식으로 부탁드려요.' }],
  ['감사 표현 알려줘', { kind: 'phrase', korean: '감사합니다.' }],
  ['얼마예요 한국어로 뭐라고 해', { kind: 'phrase', korean: '이거 얼마예요?' }],
  ['한국어 표현 알려줘', { kind: 'navigate', href: '/field/translate' }],
  ['메뉴판 번역해줘', { kind: 'navigate', href: '/field/translate' }],
  ['번역 가능해?', { kind: 'navigate', href: '/field/translate' }],
  ['translate this menu', { kind: 'navigate', href: '/field/translate' }],
  ['부산 사투리 알려줘', { kind: 'navigate', href: '/field/dialect' }],
  ['경상도 말로 한마디', { kind: 'navigate', href: '/field/dialect' }],
  ['dialect please', { kind: 'navigate', href: '/field/dialect' }],
  ['내 일정 보여줘', { kind: 'navigate', href: '/trips' }],
  ['만든 일정 열어줘', { kind: 'navigate', href: '/trips' }],
  ['여행 목록', { kind: 'navigate', href: '/trips' }],
  ['부산 로컬 스팟 보여줘', { kind: 'navigate', href: '/explore' }],
  ['해운대 야시장 둘러보기', { kind: 'navigate', href: '/explore' }],
  ['로컬 맛집 일정 짜줘', { kind: 'navigate', href: '/explore' }],
  ['해운대와 광안리 2명 맛집 일정 짜줘', { kind: 'plan', patch: { travelers: 2, adults: 2, children: 0, travelAreas: ['HAEUNDAE', 'GWANGALLI'], preferences: ['FOOD'], preferenceAnswerStatus: 'SELECTED' }, summary: ['2명', '해운대 · 광안리', '취향 1개'] }],
  ['2026-10-20 2026-10-22 3명 대중교통 송정 카페 바다', { kind: 'plan', patch: { startDate: '2026-10-20', endDate: '2026-10-22', travelers: 3, adults: 3, children: 0, transport: 'TRANSIT', travelAreas: ['SONGJEONG'], preferences: ['CAFE_HEALING', 'SEA_BEACH'], preferenceAnswerStatus: 'SELECTED' }, summary: ['출발 2026-10-20', '도착 2026-10-22', '3명', '대중교통', '송정', '취향 2개'] }],
  ['2026.1.5 남포동 자차 야경 코스', { kind: 'plan', patch: { startDate: '2026-01-05', transport: 'CAR', travelAreas: ['NAMPO'], preferences: ['CITY'], preferenceAnswerStatus: 'SELECTED' }, summary: ['출발 2026-01-05', '자동차', '남포동', '취향 1개'] }],
  ['영도 걸어서 역사 자연 여행 5명', { kind: 'plan', patch: { travelers: 5, adults: 5, children: 0, transport: 'WALK', travelAreas: ['YEONGDO'], preferences: ['NATURE_WALK', 'CULTURE_TEMPLE'], preferenceAnswerStatus: 'SELECTED' }, summary: ['5명', '도보', '영도', '취향 2개'] }],
  ['서면 렌터카 추천', { kind: 'plan', patch: { transport: 'CAR', travelAreas: ['SEOMYEON'] }, summary: ['자동차', '서면'] }],
  ['해운대 근처 맛집 알려줘', { kind: 'plan', patch: { travelAreas: ['HAEUNDAE'], preferences: ['FOOD'], preferenceAnswerStatus: 'SELECTED' }, summary: ['해운대', '취향 1개'] }],
  ['카페 가고 싶어', { kind: 'plan', patch: { preferences: ['CAFE_HEALING'], preferenceAnswerStatus: 'SELECTED' }, summary: ['취향 1개'] }],
  ['추천해줘', { kind: 'plan', patch: {}, summary: [] }],
  ['일정', { kind: 'plan', patch: {}, summary: [] }],
  ['코스 짜줘', { kind: 'plan', patch: {}, summary: [] }],
  ['안녕하세요', { kind: 'help' }],
  ['날씨 어때?', { kind: 'help' }],
  ['25명 해운대 일정', { kind: 'plan', patch: { travelers: 20, adults: 20, children: 0, travelAreas: ['HAEUNDAE'] }, summary: ['20명', '해운대'] }],
  ['0명 여행', { kind: 'plan', patch: { travelers: 1, adults: 1, children: 0 }, summary: ['1명'] }],
  ['버스 타고 광안리 바다 1명', { kind: 'plan', patch: { travelers: 1, adults: 1, children: 0, transport: 'TRANSIT', travelAreas: ['GWANGALLI'], preferences: ['SEA_BEACH'], preferenceAnswerStatus: 'SELECTED' }, summary: ['1명', '대중교통', '광안리', '취향 1개'] }],
  ['도보 송정 자연', { kind: 'plan', patch: { transport: 'WALK', travelAreas: ['SONGJEONG'], preferences: ['NATURE_WALK'], preferenceAnswerStatus: 'SELECTED' }, summary: ['도보', '송정', '취향 1개'] }],
  ['자동차 남포동 맛집 카페 바다 자연 역사 야경 2명', { kind: 'plan', patch: { travelers: 2, adults: 2, children: 0, transport: 'CAR', travelAreas: ['NAMPO'], preferences: ['FOOD', 'CAFE_HEALING', 'SEA_BEACH', 'NATURE_WALK', 'CULTURE_TEMPLE', 'CITY'], preferenceAnswerStatus: 'SELECTED' }, summary: ['2명', '자동차', '남포동', '취향 6개'] }],
];

describe('🔴 한국어로 쓴 글은 옛 해석기와 한 줄씩 같다', () => {
  it.each(GOLDEN)('%s', (input, expected) => {
    expect(signature(understandAssistantMessage(input))).toEqual(expected);
  });
});

// ── ① 실기기에서 나온 그 문장 ─────────────────────────────────────────────────────────────────────────────────
describe('🔴 「Haeundae food tips」 — 일반 안내가 아니라 해운대 · 맛집 조건', () => {
  it('영어로 쓴 지역·취향을 한국어 「해운대 맛집」과 같이 받는다', () => {
    const action = asPlan('Haeundae food tips');
    expect(action.patch.travelAreas).toEqual(['HAEUNDAE']);
    expect(action.patch.preferences).toEqual(['FOOD']);
    expect(action.summary).toEqual(['해운대', '취향 1개']);
    expect(signature(action)).toEqual(signature(understandAssistantMessage('해운대 맛집 팁')));
  });

  it('대소문자는 무시한다', () => {
    for (const text of ['HAEUNDAE FOOD TIPS', 'haeundae food tips', 'HaeUnDae Food Tips']) {
      expect(asPlan(text).patch.travelAreas).toEqual(['HAEUNDAE']);
    }
  });

  it('일반 안내(help)로 떨어지지 않는다', () => {
    expect(understandAssistantMessage('Haeundae food tips').kind).not.toBe('help');
  });
});

// ── 지역 ─────────────────────────────────────────────────────────────────────────────────────────────────
describe('지역 — 여섯 곳 × 영어·일본어·중국어(간·번)·베트남어', () => {
  const CASES: Array<[string, string, string]> = [
    ['HAEUNDAE', 'Haeundae beach', 'en'], ['HAEUNDAE', 'ヘウンデ', 'ja'], ['HAEUNDAE', '海雲台', 'ja'], ['HAEUNDAE', '海云台', 'zh-Hans'], ['HAEUNDAE', '海雲臺', 'zh-Hant'], ['HAEUNDAE', 'Gợi ý Haeundae', 'vi'],
    ['GWANGALLI', 'Gwangalli', 'en'], ['GWANGALLI', 'Gwangan bridge', 'en'], ['GWANGALLI', 'クァンアンリ', 'ja'], ['GWANGALLI', '広安里', 'ja'], ['GWANGALLI', '广安里', 'zh-Hans'], ['GWANGALLI', '廣安里', 'zh-Hant'],
    ['SONGJEONG', 'Songjeong', 'en'], ['SONGJEONG', 'ソンジョン', 'ja'], ['SONGJEONG', '松亭', 'zh'],
    ['NAMPO', 'Nampo-dong', 'en'], ['NAMPO', 'Nampodong', 'en'], ['NAMPO', 'ナンポ', 'ja'], ['NAMPO', '南浦洞', 'zh'],
    ['YEONGDO', 'Yeongdo', 'en'], ['YEONGDO', 'ヨンド', 'ja'], ['YEONGDO', '影島', 'ja·zh-Hant'], ['YEONGDO', '影岛', 'zh-Hans'],
    ['SEOMYEON', 'Seomyeon', 'en'], ['SEOMYEON', 'ソミョン', 'ja'], ['SEOMYEON', '西面', 'ja·zh'],
  ];
  it.each(CASES)('%s ← 「%s」(%s)', (code, text) => {
    expect(asPlan(text).patch.travelAreas).toEqual([code]);
  });

  it('여러 곳을 말하면 한국어와 같은 순서로 모두 받는다', () => {
    expect(asPlan('Gwangalli and Haeundae').patch.travelAreas).toEqual(['HAEUNDAE', 'GWANGALLI']);
    expect(asPlan('海雲台と広安里').patch.travelAreas).toEqual(['HAEUNDAE', 'GWANGALLI']);
  });
});

// ── 취향 ─────────────────────────────────────────────────────────────────────────────────────────────────
describe('취향 — 여섯 갈래 × 영어·일본어·중국어(간·번)·베트남어', () => {
  const CASES: Array<[string, string]> = [
    ['FOOD', 'restaurants'], ['FOOD', 'Where to eat'], ['FOOD', 'seafood'], ['FOOD', 'グルメ'], ['FOOD', 'ご飯'], ['FOOD', '美食'], ['FOOD', '餐廳'], ['FOOD', '好吃的'], ['FOOD', 'nhà hàng'], ['FOOD', 'quan an ngon'], ['FOOD', 'ẩm thực'],
    ['CAFE_HEALING', 'cafe'], ['CAFE_HEALING', 'Coffee'], ['CAFE_HEALING', 'café'], ['CAFE_HEALING', 'カフェ'], ['CAFE_HEALING', '咖啡廳'], ['CAFE_HEALING', '咖啡'], ['CAFE_HEALING', 'quán cà phê'],
    ['SEA_BEACH', 'beach'], ['SEA_BEACH', 'sea view'], ['SEA_BEACH', 'ビーチ'], ['SEA_BEACH', '海辺'], ['SEA_BEACH', '海边'], ['SEA_BEACH', '海灘'], ['SEA_BEACH', 'bãi biển'], ['SEA_BEACH', 'biển'],
    ['NATURE_WALK', 'nature'], ['NATURE_WALK', 'hiking trail'], ['NATURE_WALK', '自然'], ['NATURE_WALK', 'ハイキング'], ['NATURE_WALK', '爬山'], ['NATURE_WALK', 'thiên nhiên'],
    ['CULTURE_TEMPLE', 'history'], ['CULTURE_TEMPLE', 'temples'], ['CULTURE_TEMPLE', '歴史'], ['CULTURE_TEMPLE', '寺巡り'], ['CULTURE_TEMPLE', '历史'], ['CULTURE_TEMPLE', '歷史'], ['CULTURE_TEMPLE', 'lịch sử'], ['CULTURE_TEMPLE', 'chùa'],
    ['CITY', 'night view'], ['CITY', 'Night-view spots'], ['CITY', '夜景'], ['CITY', 'cảnh đêm'],
  ];
  it.each(CASES)('%s ← 「%s」', (code, text) => {
    const action = asPlan(text);
    expect(action.patch.preferences).toEqual([code]);
    expect(action.patch.preferenceAnswerStatus?.category).toBe('SELECTED');
  });

  it('여러 취향을 말하면 한국어와 같은 순서로 모두 받는다', () => {
    expect(asPlan('history, cafe and food').patch.preferences).toEqual(['FOOD', 'CAFE_HEALING', 'CULTURE_TEMPLE']);
  });
});

// ── 인원 ─────────────────────────────────────────────────────────────────────────────────────────────────
describe('인원 — 「2명」과 같은 뜻의 말', () => {
  it.each([
    ['Plan a Haeundae trip for 2 people', 2], ['trip for 2 persons', 2], ['trip with two people', 2], ['3 travelers', 3], ['2 adults trip', 2], ['trip, party of 4', 4], ['trip for a group of 5', 5], ['a trip for 2', 2], ['trip 6 ppl', 6],
    ['海雲台の旅行を2人で', 2], ['旅行 3名', 3], ['旅行 ２人', 2], ['旅行 二人', 2], ['旅行を4人分', 4],
    ['海云台行程2位', 2], ['海雲臺行程2位', 2], ['行程 3人', 3], ['行程 2个人', 2], ['行程 两位', 2], ['旅行 十人', 10],
    ['Lịch trình Haeundae cho 2 người', 2], ['lich trinh cho 3 nguoi', 3], ['du lịch 4 khách', 4],
  ])('「%s」→ %i명', (text, count) => {
    const action = asPlan(text);
    expect(action.patch.travelers).toBe(count);
    expect(action.patch.adults).toBe(count);
    expect(action.patch.children).toBe(0);
  });

  it('한국어와 같이 1~20명으로 맞춘다', () => {
    expect(asPlan('trip for 30 people').patch.travelers).toBe(20);
    expect(asPlan('trip for 0 people').patch.travelers).toBe(1);
  });

  it('🔴 기간·값·등급은 인원이 아니다 — 「for 2 days」「3 star hotel」「for 10000 won」', () => {
    for (const text of ['trip for 2 days', 'trip for 3 nights', 'a trip for 2 weeks', 'trip for 3 star hotels', 'trip for 10000 won', 'trip for 2026']) {
      expect(asPlan(text).patch.travelers).toBeUndefined();
    }
  });

  it('한국어 「N명」이 있으면 그것이 먼저다', () => {
    expect(asPlan('3명 trip for 5 people').patch.travelers).toBe(3);
  });

  it('요약 줄에도 같은 말로 적는다', () => {
    setCurrentLanguage('en');
    expect(asPlan('trip for 2 people').summary).toContain('2 people');
  });
});

// ── 이동 수단 ───────────────────────────────────────────────────────────────────────────────────────────────
describe('이동 수단 — 대중교통 · 자동차 · 도보', () => {
  it.each([
    ['trip by bus', 'TRANSIT'], ['trip by subway', 'TRANSIT'], ['public transport trip', 'TRANSIT'], ['旅行 地下鉄', 'TRANSIT'], ['行程 地铁', 'TRANSIT'], ['行程 地鐵', 'TRANSIT'], ['du lịch xe buýt', 'TRANSIT'],
    ['trip by car', 'CAR'], ['rental car trip', 'CAR'], ['旅行 レンタカー', 'CAR'], ['行程 自驾', 'CAR'], ['行程 自駕', 'CAR'], ['du lịch thuê xe', 'CAR'],
    ['walking trip', 'WALK'], ['trip on foot', 'WALK'], ['旅行 徒歩', 'WALK'], ['行程 步行', 'WALK'], ['du lịch đi bộ', 'WALK'],
  ])('「%s」→ %s', (text, transport) => {
    expect(asPlan(text).patch.transport).toBe(transport);
  });

  it('한국어와 같은 우선순위 — 대중교통 · 자동차 · 도보 순으로 하나만', () => {
    expect(asPlan('trip by bus or car or on foot').patch.transport).toBe('TRANSIT');
    expect(asPlan('trip by car or on foot').patch.transport).toBe('CAR');
  });
});

// ── 일정·추천 ───────────────────────────────────────────────────────────────────────────────────────────────
describe('일정·추천 — 「일정·여행·코스·짜줘·추천」과 같은 뜻의 말', () => {
  it.each([
    'Recommend something', 'Any tips?', 'help me with itinerary ideas', 'plan a trip', 'Suggest a route', 'where to go in Busan', 'Things to do', 'must-see places',
    'おすすめを教えて', '旅行プランを作って', '日程を組んで', '観光コース',
    '推荐一下', '推薦行程', '帮我安排一下', '旅游攻略', '路線規劃',
    'Gợi ý giúp tôi', 'lich trinh du lich', 'kế hoạch đi chơi', 'đi đâu bây giờ',
  ])('「%s」', (text) => {
    expect(understandAssistantMessage(text).kind).toBe('plan');
  });

  it('조건이 없으면 한국어 「추천해줘」처럼 더 물어본다 — 빈 조건', () => {
    const action = asPlan('Recommend something');
    expect(action.summary).toEqual([]);
    expect(action.patch).toEqual({});
  });
});

// ── 현장 문장 · 번역 ─────────────────────────────────────────────────────────────────────────────────────────
describe('현장 문장 — 「문장·한국어·뭐라고·말해·표현」과 같은 뜻의 말', () => {
  it.each([
    ['Show me the Korean phrase for asking someone to take a photo', '사진 한 장 부탁드려도 될까요?'],
    ['How do I say thank you in Korean?', '감사합니다.'],
    ['How do I say where is the toilet in Korean', '화장실이 어디예요?'],
    ['How to say "how much is this" in Korean', '이거 얼마예요?'],
    ['Phrase for not spicy please', '안 매운 음식으로 부탁드려요.'],
    ['韓国語で写真を撮ってもらうフレーズ', '사진 한 장 부탁드려도 될까요?'],
    ['韓国語でトイレはどこですか', '화장실이 어디예요?'],
    ['韓国語でいくらですか', '이거 얼마예요?'],
    ['韓国語で辛くないものをお願いする言い方', '안 매운 음식으로 부탁드려요.'],
    ['谢谢用韩语怎么说', '감사합니다.'],
    ['怎麼說洗手間', '화장실이 어디예요?'],
    ['照片的韩语短语', '사진 한 장 부탁드려도 될까요?'],
    ['不要辣 韓語怎麼說', '안 매운 음식으로 부탁드려요.'],
    ['Cách nói không cay bằng tiếng Hàn', '안 매운 음식으로 부탁드려요.'],
    ['Nói cảm ơn bằng tiếng Hàn thế nào', '감사합니다.'],
    ['Câu tiếng Hàn hỏi nhà vệ sinh', '화장실이 어디예요?'],
  ])('「%s」', (text, korean) => {
    const action = understandAssistantMessage(text);
    expect(action.kind).toBe('phrase');
    expect(action.kind === 'phrase' && action.korean).toBe(korean);
  });

  it('고를 문장이 없으면 한국어처럼 현장 도구로 안내한다', () => {
    for (const text of ['Korean expression please', '韓国語の表現', '韩语句子', 'Mẫu câu tiếng Hàn']) expect(where(text)).toBe('/field/translate');
  });
});

describe('번역 · 사투리 · 내 일정 · 로컬 — 화면으로 안내하는 말', () => {
  it.each([
    ['Menu translation please', '/field/translate'], ['Can you translate this?', '/field/translate'], ['メニューを翻訳して', '/field/translate'], ['翻译菜单', '/field/translate'], ['翻譯這個', '/field/translate'], ['Dịch menu giúp tôi', '/field/translate'], ['phiên dịch', '/field/translate'],
    ['Busan dialect', '/field/dialect'], ['Teach me some Busan accent', '/field/dialect'], ['釜山弁を聞きたい', '/field/dialect'], ['想听釜山话', '/field/dialect'], ['想聽釜山話', '/field/dialect'], ['giọng Busan', '/field/dialect'], ['phương ngữ', '/field/dialect'],
    ['Show my saved trips', '/trips'], ['my itinerary', '/trips'], ['open the trips I made', '/trips'], ['私の旅行を見せて', '/trips'], ['保存した旅行', '/trips'], ['我的行程', '/trips'], ['查看已保存的行程', '/trips'], ['我的旅行', '/trips'], ['Lịch trình của tôi', '/trips'], ['chuyến đi của tôi', '/trips'],
    ['Show me local Busan spots', '/explore'], ['night market', '/explore'], ['I want to explore', '/explore'], ['hidden gems near Gwangalli', '/explore'], ['ローカル探索', '/explore'], ['夜市に行きたい', '/explore'], ['本地景点', '/explore'], ['逛逛', '/explore'], ['Khám phá chợ đêm', '/explore'], ['địa điểm địa phương', '/explore'],
  ])('「%s」→ %s', (text, href) => {
    expect(where(text)).toBe(href);
  });

  it('앞에서 가르는 요청이 일정·추천보다 먼저다 — 한국어 순서 그대로', () => {
    // 「my itinerary」는 일정 낱말이지만 «내 일정 보기»가 먼저다
    expect(where('show my itinerary')).toBe('/trips');
    // 「local food」는 한국어 「로컬 맛집」처럼 로컬 탐색으로 간다
    expect(where('local food')).toBe('/explore');
  });
});

// ── ③ 거짓으로 걸리지 않는다 ───────────────────────────────────────────────────────────────────────────────
describe('🔴 거짓으로 걸리지 않는다 — 낱말 경계 · 흔한 낱말', () => {
  it.each([
    'What a great view!', 'The seat is comfortable', 'Is the planet visible?', 'Hello', 'How are you?', 'What is the weather like tomorrow?',
    'Of course!', 'Where is the parking lot?', 'bus schedule please', 'Tôi chưa biết', 'Anh ơi', 'Co the khong?',
    'お元気ですか', '你好', '今天天气怎么样',
  ])('「%s」은 일반 안내(help)', (text) => {
    expect(understandAssistantMessage(text).kind).toBe('help');
  });

  it('「Anh」「có」처럼 부호를 떼면 다른 낱말과 같아지는 베트남어는 걸리지 않는다', () => {
    expect(mentions(prepareSpoken('Tôi chưa đi'), compileTerms(['=chùa']))).toBe(false);
    expect(mentions(prepareSpoken('Tôi muốn đến chùa'), compileTerms(['=chùa']))).toBe(true);
  });
});

// ── 낱말 찾는 도구 ──────────────────────────────────────────────────────────────────────────────────────────
describe('낱말 찾기 규칙', () => {
  const has = (text: string, ...terms: string[]) => mentions(prepareSpoken(text), compileTerms(terms));

  it('라틴 글자는 낱말 경계로 — 복수 s 하나만 봐준다, 끝 * 는 «이 글자로 시작하는 낱말»', () => {
    expect(has('great food', 'eat')).toBe(false);
    expect(has('I eat', 'eat')).toBe(true);
    expect(has('trips', 'trip')).toBe(true);
    expect(has('planes', 'plan')).toBe(false);
    expect(has('recommendations', 'recommend*')).toBe(true);
    expect(has('nampodong', 'nampo*')).toBe(true);
  });

  it('띄어쓰기는 하이픈이나 여러 칸이어도 같다', () => {
    expect(has('night-view', 'night view')).toBe(true);
    expect(has('night   view', 'night view')).toBe(true);
  });

  it('베트남어는 부호를 떼도 같은 낱말로 본다', () => {
    expect(has('lich trinh', 'lịch trình')).toBe(true);
    expect(has('LỊCH TRÌNH', 'lịch trình')).toBe(true);
    expect(has('ke hoach', 'kế hoạch')).toBe(true);
  });

  it('전각 글자와 반각 가타카나도 같은 글자로 본다', () => {
    expect(has('ＨＡＥＵＮＤＡＥ', 'haeundae*')).toBe(true);
    expect(has('ﾍｳﾝﾃﾞ', 'ヘウンデ')).toBe(true);
  });

  it('일본어·중국어는 글자 조각이 들어 있으면 된다', () => {
    expect(has('海雲台が好き', '海雲台')).toBe(true);
    expect(has('hello', '海雲台')).toBe(false);
  });

  it('🔴 「海雲台」는 «바다»가 아니다 — 한 글자 「海」로 찾지 않는다', () => {
    const action = asPlan('海雲台');
    expect(action.patch.travelAreas).toEqual(['HAEUNDAE']);
    expect(action.patch.preferences).toBeUndefined();
  });

  it('인원 — 숫자가 더 이어지면 잡지 않는다', () => {
    expect(peopleCount(prepareSpoken('2026 people'))).toBeNull();
    expect(peopleCount(prepareSpoken('12 people'))).toBe(12);
  });
});

// ── 화면 언어에 맞춘 요약 줄 ─────────────────────────────────────────────────────────────────────────────────
describe('「찾은 여행 조건」의 지역 이름은 화면 언어로 — 영어 화면에서 한글이 남지 않는다', () => {
  it('영어 화면', () => {
    setCurrentLanguage('en');
    expect(asPlan('Haeundae and Gwangalli food for 2 people').summary).toEqual(['2 people', 'Haeundae · Gwangalli', '1 preferences']);
  });

  it('한국어로 써도 영어 화면이면 영어 이름이다 (전에는 「해운대」가 그대로 남았다)', () => {
    setCurrentLanguage('en');
    expect(asPlan('해운대 맛집').summary).toEqual(['Haeundae', '1 preferences']);
  });

  it('일본어·중국어 화면은 번역표의 이름', () => {
    setCurrentLanguage('ja');
    expect(asPlan('Haeundae and Gwangalli').summary).toContain('海雲台 · 広安里');
    setCurrentLanguage('zh-Hans');
    expect(asPlan('Haeundae and Gwangalli').summary).toContain('海云台 · 广安里');
    setCurrentLanguage('zh-Hant');
    expect(asPlan('Haeundae and Gwangalli').summary).toContain('海雲臺 · 廣安里');
  });

  it('베트남어 화면은 로마자 이름', () => {
    setCurrentLanguage('vi');
    expect(asPlan('Haeundae and Nampo-dong').summary).toContain('Haeundae · Nampo-dong');
  });

  it('한국어 화면은 한국어 이름 그대로', () => {
    setCurrentLanguage('ko');
    expect(asPlan('Haeundae and Gwangalli').summary).toContain('해운대 · 광안리');
  });
});
