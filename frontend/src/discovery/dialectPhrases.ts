// 부산 사투리 표현 콘텐츠. 표현을 늘릴 때 이 배열만 고치면 되고, 화면 코드는 손대지 않는다.
export type DialectPhrase = {
  id: string;
  dialect: string;
  /**
   * 읽는 법 — 한글을 못 읽는 사람을 위한 로마자·가타카나(말하기 화면 placePhrases 와 같은 방식).
   * 🔴 5개 언어 점검(2026-09-29): 카드 앞면이 한글뿐이라 외국인은 무엇을 누르는지 몰랐다.
   */
  pronunciation: string;
  pronunciationJa: string;
  standard: string;
  en: string;
  situationKo: string;
  situationEn: string;
};

export const DIALECT_PHRASES: DialectPhrase[] = [
  { id: 'mworakano', dialect: '뭐라카노', pronunciation: 'mworakano', pronunciationJa: 'ムォラカノ', standard: '뭐라고 하는 거야', en: 'What are you saying?', situationKo: '상대 말이 안 들리거나 무슨 뜻인지 되물을 때', situationEn: 'When you didn’t catch what someone said' },
  { id: 'dwaetdaaiga', dialect: '됐다 아이가', pronunciation: 'dwaetda aiga', pronunciationJa: 'テッタ アイガ', standard: '됐잖아', en: 'That’s enough already', situationKo: '더 권하거나 사양할 때', situationEn: 'Politely declining more food or help' },
  { id: 'ujjano', dialect: '우짜노', pronunciation: 'ujjano', pronunciationJa: 'ウッチャノ', standard: '어떡하지', en: 'What should I do?', situationKo: '난감하거나 걱정될 때', situationEn: 'When something goes wrong and you’re unsure what to do' },
  { id: 'aida', dialect: '아이다', pronunciation: 'aida', pronunciationJa: 'アイダ', standard: '아니다', en: 'No, that’s not it', situationKo: '가볍게 부정하거나 정정할 때', situationEn: 'Casually correcting or disagreeing' },
  { id: 'haigo', dialect: '하이고', pronunciation: 'haigo', pronunciationJa: 'ハイゴ', standard: '아이고', en: 'Oh dear / Oof', situationKo: '놀라거나 힘들 때 내는 감탄사', situationEn: 'An exclamation of surprise or fatigue' },
  { id: 'maimura', dialect: '마이 무라', pronunciation: 'mai mura', pronunciationJa: 'マイ ムラ', standard: '많이 먹어', en: 'Eat a lot', situationKo: '식당·숙소에서 음식을 권할 때', situationEn: 'Offering food warmly at a restaurant or homestay' },
  { id: 'geukajimara', dialect: '그카지 마라', pronunciation: 'geukaji mara', pronunciationJa: 'クカジ マラ', standard: '그러지 마', en: 'Don’t do that', situationKo: '가볍게 말리거나 장난스럽게 제지할 때', situationEn: 'Playfully telling someone to stop' },
  { id: 'eoksuroota', dialect: '억수로 좋다', pronunciation: 'eoksuro jota', pronunciationJa: 'オクスロ チョタ', standard: '엄청 좋다', en: 'Really, really great', situationKo: '감탄하며 만족을 표현할 때', situationEn: 'Expressing strong satisfaction or delight' },
];
