// 장소별 한국어 말하기 콘텐츠. 문장을 늘릴 때 이 배열만 고치면 되고
// 모달 화면 코드는 손대지 않는다 —의 사투리 콘텐츠 분리와 같은 패턴이다.
export type PlaceTabKey = 'SIGHT' | 'FOOD' | 'TAXI' | 'STAY';

export type PlacePhrase = {
  id: string;
  ko: string;
  /** 로마자 발음 — 영어·중국어 화면. */
  pronunciation: string;
  /** 가타카나 발음 — 일본어 화면. 로마자는 일본어 사용자에게 두 번 읽는 일이다(S15P21E201-1367). */
  pronunciationJa: string;
  en: string;
};

export const PLACE_TABS: { key: PlaceTabKey; icon: string; labelKo: string; labelEn: string }[] = [
  { key: 'SIGHT', icon: '🏛️', labelKo: '관광지', labelEn: 'Sights' },
  { key: 'FOOD', icon: '🍽️', labelKo: '식당·카페', labelEn: 'Food & Cafe' },
  { key: 'TAXI', icon: '🚕', labelKo: '택시', labelEn: 'Taxi' },
  { key: 'STAY', icon: '🏨', labelKo: '숙소', labelEn: 'Stay' },
];

export const PLACE_PHRASES: Record<PlaceTabKey, PlacePhrase[]> = {
  SIGHT: [
    { id: 'sight-restroom', ko: '화장실이 어디예요?', pronunciation: 'hwajangsiri eodiyeyo?', pronunciationJa: 'ファジャンシリ オディエヨ?', en: 'Where is the restroom?' },
    { id: 'sight-photo', ko: '사진 좀 찍어 주시겠어요?', pronunciation: 'sajin jom jjigeo jusigesseoyo?', pronunciationJa: 'サジン ジョム チゴ ジュシゲッソヨ?', en: 'Could you take a photo for me?' },
    { id: 'sight-fee', ko: '입장료가 얼마예요?', pronunciation: 'ipjangnyoga eolmayeyo?', pronunciationJa: 'イプチャンニョガ オルマエヨ?', en: 'How much is the admission fee?' },
    { id: 'sight-close', ko: '몇 시에 문을 닫아요?', pronunciation: 'myeot sie muneul dadayo?', pronunciationJa: 'ミョッ シエ ムヌル タダヨ?', en: 'What time does it close?' },
  ],
  FOOD: [
    { id: 'food-one', ko: '이거 하나 주세요', pronunciation: 'igeo hana juseyo', pronunciationJa: 'イゴ ハナ ジュセヨ', en: 'One of these, please' },
    { id: 'food-nospicy', ko: '맵지 않게 해 주세요', pronunciation: 'maepji anke hae juseyo', pronunciationJa: 'メプチ アンケ ヘ ジュセヨ', en: 'Please make it not spicy' },
    { id: 'food-bill', ko: '계산서 주세요', pronunciation: 'gyesanseo juseyo', pronunciationJa: 'ケサンソ ジュセヨ', en: 'Check, please' },
    { id: 'food-togo', ko: '포장 되나요?', pronunciation: 'pojang doenayo?', pronunciationJa: 'ポジャン トェナヨ?', en: 'Can I get this to go?' },
  ],
  TAXI: [
    { id: 'taxi-here', ko: '여기로 가 주세요', pronunciation: 'yeogiro ga juseyo', pronunciationJa: 'ヨギロ カ ジュセヨ', en: 'Please take me here' },
    { id: 'taxi-time', ko: '얼마나 걸려요?', pronunciation: 'eolmana geollyeoyo?', pronunciationJa: 'オルマナ コルリョヨ?', en: 'How long will it take?' },
    { id: 'taxi-card', ko: '카드로 계산할게요', pronunciation: 'kadeuro gyesanhalgeyo', pronunciationJa: 'カドゥロ ケサナルケヨ', en: "I'll pay by card" },
    { id: 'taxi-stop', ko: '여기서 내려 주세요', pronunciation: 'yeogiseo naeryeo juseyo', pronunciationJa: 'ヨギソ ネリョ ジュセヨ', en: 'Please let me off here' },
  ],
  STAY: [
    { id: 'stay-checkin', ko: '체크인하고 싶어요', pronunciation: 'chekeuinhago sipeoyo', pronunciationJa: 'チェクインハゴ シポヨ', en: "I'd like to check in" },
    { id: 'stay-wifi', ko: '와이파이 비밀번호가 뭐예요?', pronunciation: 'waipai bimilbeonhoga mwoyeyo?', pronunciationJa: 'ワイパイ ピミルボノガ ムォエヨ?', en: "What's the wifi password?" },
    { id: 'stay-luggage', ko: '짐을 맡길 수 있나요?', pronunciation: 'jimeul matgil su innayo?', pronunciationJa: 'ジムル マッキル ス インナヨ?', en: 'Can I leave my luggage here?' },
    { id: 'stay-checkout', ko: '체크아웃은 몇 시예요?', pronunciation: 'chekeuaus-eun myeot siyeyo?', pronunciationJa: 'チェクアウスン ミョッ シエヨ?', en: 'What time is checkout?' },
  ],
};

// 장소 종류(category) 값의 정확한 목록을 아직 못 받았다(-476 계약에 문자열이라고만
// 돼 있음). 그래서 정확히 아는 척하지 않고, 흔한 키워드가 들어 있으면 그 탭을 기본으로
// 고르고 모르면 관광지로 떨어지는 느슨한 매칭만 한다.
export function defaultTabForCategory(category?: string | null): PlaceTabKey {
  const value = (category ?? '').toUpperCase();
  if (/FOOD|RESTAURANT|CAFE|맛집|카페|식당/.test(value)) return 'FOOD';
  if (/HOTEL|STAY|LODG|숙소|호텔|게스트/.test(value)) return 'STAY';
  if (/TAXI/.test(value)) return 'TAXI';
  return 'SIGHT';
}
