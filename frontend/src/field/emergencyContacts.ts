// 긴급 도움·분실물에 적는 번호와 말 — S15P21E201-1879 (UI 캔버스 ㉒).
//
// 🔴 여기 적힌 것은 전부 공식 출처로 확인한 것이다(2026-09-30). 외국인이 가장 급할 때 읽는 글이라, 틀린 한 줄이
//    「이 앱은 믿을 수 없다」가 된다. 확인 못 한 것은 적지 않는다 — 부산 택시 회사 번호는 공식 출처를 못 찾아 뺐다.
//    고칠 때는 출처를 다시 보고, 아래 SOURCES 의 날짜를 바꾼다.
//
//    - 119: 부산소방은 외국어 신고를 통역 기관(관광공사 1330·다누리·BBB)과 3자 통화로 받는다 — 언어를 못 박지 않는다.
//           (부산소방 외국인 119 신고 보도 2026-04-23) 시안의 「영어·중국어·일본어 통역」은 근거가 없어 쓰지 않는다.
//    - 112: 경찰청 112 통역센터가 직접 통역하는 것은 영어·중국어, 365일 24시간(2024-03-18 확대).
//           일본어 등은 아직 직접 통역이 없고 다른 통역 기관과 3자 통화로 잇는다(경찰청 보도·언론 2024).
//    - 1330: 한국관광공사 관광통역안내. 한국어·영어·일본어·중국어 24시간, 러시아어·베트남어·태국어·말레이·인도네시아어는
//           08~19시. 해외에서는 +82-2-1330. 급할 때 119 신고를 도와준다(관광공사·지자체 안내).
//    - 도시철도 유실물센터: 서면역 지하 2층 1·2호선 환승 통로(지하철경찰대 옆), 051-640-7339, 평일 09~18시,
//           습득일부터 7일 보관 뒤 경찰로 넘긴다. 2026-01-26부터 경찰민원24와 통합 운영(부산교통공사 누리집).
//    - 유실물 검색: LOST112 가 2026-01-26 경찰민원24(minwon24.police.go.kr)로 옮겨 갔다.
export const SOURCES_CHECKED_AT = '2026-09-30';

export type Bilingual = { ko: string; en: string };
export type EmergencyLine = { number: string; tel: string; title: Bilingual; what: Bilingual; languages: Bilingual };

export const EMERGENCY_LINES: readonly EmergencyLine[] = [
  {
    number: '119', tel: 'tel:119',
    title: { ko: '화재 · 구급차', en: 'Fire · Ambulance' },
    what: { ko: '다치거나 아플 때, 불이 났을 때', en: 'Injury, sudden illness or fire' },
    languages: { ko: '외국어로 말해도 돼요 — 통역을 연결해 셋이 함께 통화해요', en: 'Speak your language — they bring in an interpreter on the same call' },
  },
  {
    number: '112', tel: 'tel:112',
    title: { ko: '경찰', en: 'Police' },
    what: { ko: '도난 · 사고 · 위험할 때', en: 'Theft, accidents or danger' },
    languages: { ko: '영어·중국어는 24시간 통역 · 그 밖의 언어는 통역 기관을 연결해요', en: 'English and Chinese interpreters 24/7 · other languages via an interpreting service' },
  },
  {
    number: '1330', tel: 'tel:1330',
    title: { ko: '관광 통역 안내', en: 'Korea Travel Hotline' },
    what: { ko: '말이 안 통할 때 · 여행 중 불편할 때', en: 'When you cannot make yourself understood' },
    languages: { ko: '한국어·영어·일본어·중국어 24시간 · 러시아어·베트남어·태국어·말레이·인도네시아어 08~19시', en: 'Korean, English, Japanese, Chinese 24/7 · Russian, Vietnamese, Thai, Malay/Indonesian 8am–7pm' },
  },
];

/** 도시철도 유실물센터 — 부산교통공사 누리집. */
export const METRO_LOST_FOUND = {
  tel: 'tel:051-640-7339',
  phone: '051-640-7339',
  // 위치·운영 시간 문구는 분실물 화면(app/lost-items.tsx)에 문장째 있다 — 번역표 열쇠가 문장이라 여기서 나눠 두지 않는다.
};

/** 전국 유실물 검색 — 경찰·지하철·버스 등에서 맡긴 물건이 모인다. */
export const POLICE_LOST_FOUND_URL = 'https://minwon24.police.go.kr';

/** 말이 안 통할 때 보여 주는 한국어 — 화면 언어와 상관없이 한국어로 크게 보인다. */
export const SHOW_KOREAN = {
  help: { ko: '도와주세요.\n저는 외국인 관광객이에요.', gloss: { ko: '도와주세요. 저는 외국인 관광객이에요.', en: 'Please help me. I am a foreign tourist.' } },
  lost: { ko: '물건을 잃어버렸어요.\n찾는 것을 도와주세요.', gloss: { ko: '물건을 잃어버렸어요. 찾는 것을 도와주세요.', en: 'I lost something. Please help me find it.' } },
} as const;
