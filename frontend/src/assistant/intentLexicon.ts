// 비서의 앱 안 해석기(intent.ts)가 «한국어 말고» 알아듣는 낱말들 — S15P21E201-2055.
//
// 🔴 왜 있나. 로그인 안 한 손님은 서버 AI 없이 이 앱 안의 해석기만 쓴다(chat.tsx 의 accessToken 갈림). 그런데 해석기가
//    한국어 낱말만 알아서, 갤럭시 S22 실기기(2026-10-14)에서 「Haeundae food tips」에 일반 안내(「…도와드릴 수 있어요」)만
//    돌아왔다. 앱은 ko·en·ja·zh-Hans(간체)·zh-Hant(번체)·vi(베트남어) 여섯 언어를 지원한다.
//    한국어 낱말은 intent.ts 에 그대로 있고, 여기는 «같은 뜻의 다른 언어 낱말»만 둔다 — 한국어 판정에는 손대지 않는다.
//
// 🔴 낱말을 찾는 규칙 셋. 앞 두 개가 «거짓으로 걸리는 것»을 막는다.
//
//   ① 영어·베트남어(라틴 글자)는 «낱말 경계»로 찾는다. 「great」 안의 「eat」이 맛집으로 잡히면 안 된다.
//      낱말 끝에 s 하나는 봐준다(trip → trips). 끝에 * 를 붙이면 «이 글자로 시작하는 낱말»이다(recommend* → recommendation).
//      띄어쓰기는 하이픈이나 여러 칸이어도 같다(night view = night-view).
//   ② 베트남어는 성조 부호를 뗀 모양으로 찾는다. 「lịch trình」과 「lich trinh」(부호 없이 빨리 친 글)를 같게 본다.
//      그 대신 «부호를 떼면 흔한 낱말과 같아지는 것»은 목록에 넣지 않는다(예: ăn → an, cổ → co, chùa → chua 와 chưa).
//      꼭 필요하면 낱말 앞에 = 를 붙여 «부호까지 같아야» 걸리게 한다(=chùa).
//   ③ 일본어·중국어는 띄어쓰기가 없으니 «글자 조각이 들어 있나»로 찾는다. 한 글자짜리는 다른 낱말 안에서 걸리니 피한다
//      (예: 「海」는 「海雲台」 안에도 있다 — 그래서 바다는 「海辺」「ビーチ」처럼 둘 이상 글자나 조사가 붙은 모양으로).
//
// 모든 글은 NFKC 로 다듬은 뒤 소문자로 본다 — 전각 글자(ＡＢＣ·２人)와 반각 가타카나도 같은 글자가 된다.

/** 찾을 낱말 한 묶음. cjk 는 글자 조각, latin·accented 는 낱말 경계 정규식. */
export type Terms = { readonly cjk: readonly string[]; readonly latin: RegExp | null; readonly accented: RegExp | null };

/** 사용자가 쓴 글을 낱말 찾기용으로 다듬은 두 모양. */
export type Spoken = { readonly folded: string; readonly accented: string };

function normalizeSafe(text: string, form: 'NFC' | 'NFKC' | 'NFD'): string {
  // 헤르메스(안드로이드 JS 엔진)에서 normalize 가 없거나 던지면 글을 그대로 쓴다 — 부호 없는 입력만 못 알아듣고 앱은 멀쩡하다.
  try {
    return text.normalize(form);
  } catch {
    return text;
  }
}

/** 라틴 확장 글자(베트남어 등)의 성조·모양 부호를 떼고 đ 를 d 로. 한글·가나·한자는 건드리지 않는다. */
function stripLatinMarks(text: string): string {
  return text
    .replace(/[À-ɏḀ-ỿ]+/g, (run) => normalizeSafe(run, 'NFD').replace(/[̀-ͯ]/g, ''))
    .replace(/đ/g, 'd');
}

/** 사용자가 쓴 글 → 낱말 찾기용. */
export function prepareSpoken(raw: string): Spoken {
  const accented = normalizeSafe(raw, 'NFKC').toLowerCase();
  return { accented, folded: stripLatinMarks(accented) };
}

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** 라틴 낱말 하나의 정규식 조각. 끝 * 는 «이 글자로 시작하는 낱말», 없으면 «그 낱말 + 복수 s 하나까지». */
function latinPattern(term: string): string {
  const prefix = term.endsWith('*');
  const words = (prefix ? term.slice(0, -1) : term).trim().split(/\s+/).map(escapeRegExp);
  return `${words.join('[\\s\\-]+')}${prefix ? '[a-z0-9]*' : 's?'}`;
}

function boundary(patterns: string[]): RegExp | null {
  return patterns.length ? new RegExp(`(?:^|[^a-z0-9])(?:${patterns.join('|')})(?![a-z0-9])`) : null;
}

/** 낱말 목록을 한 번 정규식으로 만들어 둔다 — 부를 때마다 만들지 않는다. */
export function compileTerms(entries: readonly string[]): Terms {
  const cjk: string[] = [];
  const latin: string[] = [];
  const accented: string[] = [];
  for (const entry of entries) {
    const sensitive = entry.startsWith('=');
    const body = normalizeSafe(sensitive ? entry.slice(1) : entry, 'NFKC').toLowerCase();
    if (sensitive) {
      accented.push(latinPattern(body));
      continue;
    }
    const folded = stripLatinMarks(body);
    if (/^[a-z0-9 *-]+$/.test(folded)) latin.push(latinPattern(folded));
    else cjk.push(body);
  }
  return { cjk, latin: boundary(latin), accented: boundary(accented) };
}

/** 이 글에 그 낱말들 중 하나라도 들어 있나. */
export function mentions(spoken: Spoken, terms: Terms): boolean {
  return terms.cjk.some((term) => spoken.folded.includes(term))
    || (terms.latin !== null && terms.latin.test(spoken.folded))
    || (terms.accented !== null && terms.accented.test(spoken.accented));
}

// ── 무엇을 하려는 말인가 ─────────────────────────────────────────────────────────────────────────────────────

/** 현장 문장 — 한국어의 「문장·한국어·뭐라고·말해·표현」. */
export const PHRASE_INTENT = compileTerms([
  // English
  'phrase*', 'sentence*', 'expression*', 'how do i say', 'how to say', 'how can i say', 'how would i say',
  'what do i say', 'what should i say', 'say in korean', 'in korean', 'korean words', 'korean language', 'speak korean',
  // 日本語
  'フレーズ', '韓国語', '言い方', '何と言', 'なんて言', 'どう言', '表現', '文章',
  // 中文 (简体·繁體)
  '短语', '短語', '韩语', '韓語', '韩文', '韓文', '怎么说', '怎麼說', '怎么讲', '怎麼講', '句子', '表达', '表達', '说法', '說法',
  // Tiếng Việt
  'cụm từ', 'câu nói', 'mẫu câu', 'tiếng hàn', 'nói thế nào', 'nói sao', 'cách nói', 'nói như thế nào',
]);

/** 번역·메뉴판 — 한국어의 「번역·메뉴판·translate」. */
export const TRANSLATE_INTENT = compileTerms([
  'translate*', 'translation*', 'translator', 'menu board', 'menu photo', 'menu scan*',
  '翻訳', '訳して', 'メニュー表', 'メニューの写真', 'メニュー写真',
  '翻译', '翻譯', '菜单照片', '菜單照片',
  'dịch menu', 'dịch thực đơn', 'dịch ảnh', 'bản dịch', 'phiên dịch', 'dịch sang', 'dịch giúp',
]);

/** 부산 사투리 — 한국어의 「사투리·부산말·경상도·dialect」. */
export const DIALECT_INTENT = compileTerms([
  'dialect*', 'busan accent', 'satoori', 'saturi', 'gyeongsang*', 'slang',
  '方言', '釜山弁', 'なまり', '訛り', 'サトゥリ', '慶尚道',
  '庆尚道', '釜山话', '釜山話', '口音',
  'phương ngữ', 'tiếng địa phương', 'giọng busan', 'giọng địa phương',
]);

/** 내가 만든 일정 보기 — 한국어의 「내 일정·여행 목록·만든 일정」. */
export const MY_TRIPS_INTENT = compileTerms([
  'my trip', 'my itinerar*', 'my schedule*', 'my plan', 'my travel', 'saved trip', 'saved itinerar*', 'saved plan',
  'trip list', 'trip history', 'trips i made', 'trips i saved', 'plans i made', 'itinerary i made',
  '私の旅行', '私の日程', '私の予定', 'マイ旅行', 'マイ日程', '自分の旅行', '自分の日程', '保存した旅行', '保存した日程',
  '作った旅行', '作った日程', '作成した旅行', '旅行リスト', '旅行一覧', '旅行の一覧', '旅行の履歴', '私のプラン',
  '我的行程', '我的旅行', '我的日程', '我的计划', '我的計劃', '我的安排', '已保存的行程', '已儲存的行程', '保存的行程', '儲存的行程',
  '行程列表', '旅行列表', '旅行记录', '旅行記錄', '我创建的行程', '我建立的行程',
  'chuyến đi của tôi', 'lịch trình của tôi', 'kế hoạch của tôi', 'hành trình của tôi', 'chuyến đi đã lưu', 'lịch trình đã lưu',
  'chuyến đi đã tạo', 'lịch trình đã tạo', 'danh sách chuyến đi', 'danh sách lịch trình', 'chuyến đi của mình', 'lịch trình của mình',
]);

/** 로컬 탐색 — 한국어의 「로컬·야시장·둘러보」. */
export const LOCAL_INTENT = compileTerms([
  'local', 'locals', 'night market*', 'explore', 'exploring', 'wander*', 'hidden gem*', 'off the beaten path', 'look around', 'stroll*',
  'ローカル', '夜市', '散策', '見て回', 'ぶらぶら', '穴場', 'ご当地',
  '本地', '逛逛', '探索', '当地', '當地', '小众', '小眾',
  'địa phương', 'bản địa', 'chợ đêm', 'khám phá', 'dạo quanh', 'dạo phố',
]);

/** 일정·추천 — 한국어의 「일정·여행·코스·짜줘·추천」. */
export const PLAN_INTENT = compileTerms([
  // English — 「course」「schedule」「visit」는 일부러 뺐다: 「of course」「bus schedule」「visiting hours」에 걸린다
  'itinerar*', 'trip', 'travel*', 'tour*', 'route*', 'plan', 'planning', 'planned', 'recommend*', 'suggest*', 'tip', 'advice',
  'things to do', 'what to do', 'where to go', 'where to eat', 'where should i', 'best place*', 'must see', 'must visit', 'must try',
  'sightseeing', 'vacation', 'holiday', 'journey',
  // 日本語
  '日程', '旅行', 'コース', 'プラン', '計画', 'おすすめ', 'オススメ', 'お勧め', '提案', '作って', '組んで', 'ルート', '観光',
  // 中文
  '行程', '旅游', '旅遊', '路线', '路線', '线路', '線路', '计划', '計劃', '计画', '推荐', '推薦', '安排', '规划', '規劃', '攻略', '玩法',
  '景点', '景點', '游玩', '遊玩', '去哪',
  // Tiếng Việt
  'lịch trình', 'hành trình', 'du lịch', 'chuyến đi', 'gợi ý', 'đề xuất', 'kế hoạch', 'tour', 'lộ trình', 'tham quan', 'đi đâu', 'ăn gì', 'chơi gì', 'nên đi',
]);

// ── 현장 문장 고르기 — 한국어 PHRASES 의 match 와 같은 순서·같은 뜻 ───────────────────────────────────────────

export const PHRASE_TOPIC_TERMS = {
  /** 사진 한 장 부탁 (사진·찍어) */
  photo: compileTerms([
    'photo*', 'picture*', 'take a photo', 'selfie*', '写真', '撮って', '撮影', '照片', '拍照', '相片', '合影',
    'chụp ảnh', 'chụp hình', 'bức ảnh',
  ]),
  /** 화장실 (화장실) */
  toilet: compileTerms([
    'toilet*', 'restroom*', 'bathroom*', 'washroom*', 'lavatory', 'wc',
    'トイレ', 'お手洗い', '化粧室', '厕所', '廁所', '洗手间', '洗手間', '卫生间', '衛生間', '盥洗室',
    'nhà vệ sinh', 'phòng vệ sinh',
  ]),
  /** 안 매운 음식 (안 매운·맵지 않) */
  notSpicy: compileTerms([
    'not spicy', 'no spice', 'less spicy', 'not too spicy', 'without spice', 'non spicy', 'non-spicy', 'mild',
    '辛くない', '辛くなく', '辛さ控えめ', '辛いのが苦手', '辛いものが苦手', '辛くしないで',
    '不辣', '不要辣', '别太辣', '別太辣', '少辣', '微辣', '不吃辣',
    'không cay', 'đừng cay', 'ít cay', 'không ăn cay',
  ]),
  /** 감사 (감사) */
  thanks: compileTerms([
    'thank*', 'thanks', 'ありがとう', '感謝', '谢谢', '謝謝', '感谢', '多谢', '多謝', 'cảm ơn', 'cám ơn',
  ]),
  /** 가격 (얼마·가격) */
  price: compileTerms([
    'how much', 'price*', 'cost', 'いくら', '値段', '価格', '料金', '多少钱', '多少錢', '价格', '價格', '价钱', '價錢',
    'bao nhiêu', 'giá cả',
  ]),
} as const;

// ── 어디를·무엇을 ───────────────────────────────────────────────────────────────────────────────────────────

export type AreaCode = 'HAEUNDAE' | 'GWANGALLI' | 'SONGJEONG' | 'NAMPO' | 'YEONGDO' | 'SEOMYEON';

/** 지역 — 한국어 이름은 intent.ts 의 AREAS. 일본어·중국어 표기는 번역표(translations.ts)의 지역 이름과 같다. */
export const AREA_TERMS: Readonly<Record<AreaCode, Terms>> = {
  HAEUNDAE: compileTerms(['haeundae*', 'heaundae*', 'haeundea*', 'ヘウンデ', '海雲台', '海雲臺', '海云台']),
  GWANGALLI: compileTerms(['gwangalli*', 'gwangali*', 'gwangan*', 'kwangalli*', 'クァンアンリ', 'クヮンアンリ', 'カンアンリ', '広安里', '广安里', '廣安里']),
  SONGJEONG: compileTerms(['songjeong*', 'songjung*', 'ソンジョン', '松亭']),
  NAMPO: compileTerms(['nampo*', 'ナンポ', '南浦']),
  YEONGDO: compileTerms(['yeongdo*', 'youngdo*', 'yeong do', 'ヨンド', '影島', '影岛']),
  SEOMYEON: compileTerms(['seomyeon*', 'seomyun*', 'seo myeon', 'ソミョン', '西面']),
};

/** 지역 이름(영어) — 영어·베트남어 화면의 「찾은 여행 조건」 줄에 쓴다. 일본어·중국어는 번역표가 이 한국어 이름으로 찾는다. */
export const AREA_ENGLISH: Readonly<Record<AreaCode, string>> = {
  HAEUNDAE: 'Haeundae', GWANGALLI: 'Gwangalli', SONGJEONG: 'Songjeong', NAMPO: 'Nampo-dong', YEONGDO: 'Yeongdo', SEOMYEON: 'Seomyeon',
};

export type PreferenceCode = 'FOOD' | 'CAFE_HEALING' | 'SEA_BEACH' | 'NATURE_WALK' | 'CULTURE_TEMPLE' | 'CITY';

/** 취향 — 한국어 낱말은 intent.ts 의 PREFERENCES. */
export const PREFERENCE_TERMS: Readonly<Record<PreferenceCode, Terms>> = {
  // 맛집
  FOOD: compileTerms([
    'food*', 'foodie*', 'restaurant*', 'eat', 'eating', 'dining', 'dine', 'lunch', 'dinner', 'breakfast', 'brunch', 'cuisine*',
    'delicious', 'tasty', 'gourmet', 'seafood', 'street food', 'snack*', 'meal*',
    'グルメ', 'レストラン', '食べ', 'ご飯', 'ごはん', '食事', '料理', 'ランチ', 'ディナー', '美味しい', 'おいしい', '食堂',
    '美食', '餐厅', '餐廳', '餐馆', '餐館', '吃', '好吃', '午餐', '晚餐', '早餐', '美味', '小吃',
    'ăn uống', 'ẩm thực', 'nhà hàng', 'quán ăn', 'món ngon', 'đồ ăn ngon', 'ăn gì', 'ăn trưa', 'ăn tối', 'ăn sáng',
  ]),
  // 카페
  CAFE_HEALING: compileTerms([
    'cafe*', 'coffee*', 'dessert*', 'bakery', 'bakeries', 'tea house', 'teahouse', 'tea room',
    'カフェ', 'コーヒー', '珈琲', '喫茶', 'デザート', 'スイーツ', 'ベーカリー', 'パン屋',
    '咖啡', '甜点', '甜點', '下午茶', '烘焙', '面包店', '麵包店',
    'cà phê', 'tiệm bánh', 'tráng miệng',
  ]),
  // 바다
  SEA_BEACH: compileTerms([
    'sea', 'beach*', 'ocean', 'seaside', 'seashore', 'coast*', 'waterfront', 'shore*', 'swim*', 'sunbathing',
    'ビーチ', '海辺', '海岸', '海水浴', '海沿い', '海を', '海が', '海で', '海の', '海へ', '海は', '海も', '海に', '海と',
    '海边', '海邊', '海滩', '海灘', '沙滩', '沙灘', '大海', '海景', '看海',
    'biển', 'bãi biển', 'bờ biển', 'ven biển',
  ]),
  // 자연
  NATURE_WALK: compileTerms([
    'nature*', 'natural', 'hiking', 'hike*', 'trail*', 'mountain*', 'forest*', 'scenery', 'scenic', 'outdoor*', 'trekking',
    '自然', 'ハイキング', 'トレッキング', '公園', '森', '登山',
    '爬山', '公园', '山林', '森林', '风景', '風景', '户外', '戶外',
    'thiên nhiên', 'núi', 'công viên', 'cảnh quan',
  ]),
  // 역사
  CULTURE_TEMPLE: compileTerms([
    'history', 'historic*', 'historical', 'temple*', 'culture*', 'cultural', 'heritage', 'museum*', 'shrine*', 'tradition*', 'palace*', 'ancient',
    '歴史', '寺', '神社', '文化', '博物館', '伝統', '遺跡', '史跡',
    '历史', '歷史', '博物馆', '传统', '傳統', '古迹', '古蹟', '遗址',
    'lịch sử', '=chùa', 'văn hóa', 'bảo tàng', 'di tích', 'truyền thống',
  ]),
  // 야경
  CITY: compileTerms([
    'night view*', 'nightview*', 'nightscape*', 'night scene*', 'city lights', 'city light', 'nightlife', 'skyline', 'night out',
    '夜景', 'ナイトビュー', 'ナイトライフ', '夜の街', '夜生活',
    'cảnh đêm', 'về đêm', 'ban đêm',
  ]),
};

/** 이동 수단 — 한국어의 「대중교통·버스·지하철」 / 「자차·자동차·렌터카」 / 「도보·걸어서·걷기」. */
export const TRANSPORT_TERMS = {
  TRANSIT: compileTerms([
    'public transit', 'public transport*', 'bus', 'buses', 'subway', 'metro',
    '公共交通', 'バス', '地下鉄', '電車', '公交', '公車', '公车', '巴士', '地铁', '地鐵', '捷运', '捷運',
    'phương tiện công cộng', 'xe buýt', 'xe bus', 'tàu điện ngầm',
  ]),
  CAR: compileTerms([
    'rental car', 'rent a car', 'by car', 'drive', 'driving', 'self drive', 'self-drive', 'car',
    '自家用車', 'レンタカー', '車で', 'ドライブ', '自動車', '自驾', '自駕', '开车', '開車', '租车', '租車', '汽车', '汽車',
    'ô tô', 'xe hơi', 'tự lái', 'thuê xe', 'lái xe',
  ]),
  WALK: compileTerms([
    'on foot', 'by foot', 'walk*', 'walking',
    '徒歩', '歩いて', '歩き', '步行', '徒步', '走路', '走着', '走著',
    'đi bộ',
  ]),
} as const;

// ── 몇 명 ────────────────────────────────────────────────────────────────────────────────────────────────

const ENGLISH_NUMBER: Readonly<Record<string, number>> = { one: 1, two: 2, three: 3, four: 4, five: 5, six: 6, seven: 7, eight: 8, nine: 9, ten: 10 };
const CJK_NUMBER: Readonly<Record<string, number>> = { 一: 1, 二: 2, 两: 2, 兩: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9, 十: 10 };

/** 몇 명인지 말한 곳 — 한국어의 「N명」은 intent.ts 가 먼저 본다. 여기는 그 밖의 언어. 첫 묶음이 숫자 또는 숫자 낱말이다. */
const PEOPLE_PATTERNS: ReadonlyArray<{ pattern: RegExp; words?: Readonly<Record<string, number>> }> = [
  // English — 2 people · 2 persons · 3 travelers · 2 adults · 4 guests · 2 of us
  { pattern: /(?:^|[^0-9])(\d{1,3})\s*(?:people|persons?|travell?ers?|adults?|guests?|pax|ppl|of us)(?![a-z])/ },
  { pattern: /(?:^|[^a-z0-9])(one|two|three|four|five|six|seven|eight|nine|ten)\s+(?:people|persons?|travell?ers?|adults?|guests?)(?![a-z])/, words: ENGLISH_NUMBER },
  { pattern: /(?:^|[^a-z0-9])(?:party|group) of (\d{1,3})(?![0-9])/ },
  // 「for 2」 — 기간·값·등급이 붙으면 사람 수가 아니다(for 2 days · for 3 star hotels · for 10000 won)
  { pattern: /(?:^|[^a-z0-9])for (\d{1,3})(?![0-9]|\s*(?:-|days?|nights?|hours?|hrs?|h\b|mins?|minutes?|weeks?|months?|years?|stars?|dollars?|usd|krw|won|km|kms|miles?|%))/ },
  // 日本語·中文 — 2人 · 2名 · 2位 · 2个人 · 二人 · 两位
  { pattern: /(?:^|[^0-9])(\d{1,3})\s*(?:个人|個人|人|名|位)/ },
  { pattern: /([一二三四五六七八九十两兩])\s*(?:个人|個人|人|名|位)/, words: CJK_NUMBER },
  // Tiếng Việt — 2 người (부호를 뗀 모양)
  { pattern: /(?:^|[^0-9])(\d{1,3})\s*(?:nguoi|khach)(?![a-z])/ },
];

/** 한국어 말고 다른 언어로 말한 인원. 못 찾으면 null. */
export function peopleCount(spoken: Spoken): number | null {
  for (const { pattern, words } of PEOPLE_PATTERNS) {
    const found = spoken.folded.match(pattern);
    if (!found) continue;
    const value = words ? words[found[1]] : Number(found[1]);
    if (Number.isFinite(value)) return value;
  }
  return null;
}
