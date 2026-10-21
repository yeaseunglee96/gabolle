// 참고 명소 — 직접 일정을 짤 때 보여 주는 부산 대표 관광지 (S15P21E201-2019).
//
// 🔴 장소 번호는 운영 장소 표의 것을 그대로 옮겼다(2026-10-09, GET /api/v1/places?query= 로 확인). 지어내지 않는다 —
//    없는 번호를 넣으면 「넣기」가 서버에서 404 로 떨어진다. 이름·사진·다른 언어 이름은 화면이 장소 상세에서 받아 온다
//    (loadPlacePhoto) — 사진은 공공 사진(관광공사 공공누리·부산관광아카이브)이 있는 곳만 골랐다.
//    한 줄 소개만 여기 둔다. 번역은 src/i18n/translations.ts 의 같은 한국어 원문 줄에 있다.
//
// 축제는 고정 목록이 아니다 — 여행 날짜에 열리는 것만 서버(GET /api/v1/festivals)에서 받는다. 날짜가 안 맞는 축제를
// 넣으면 서버가 거부한다(PlaceNotOpenDuringTrip).
export type ReferenceThemeKey = 'sea' | 'culture' | 'view' | 'market' | 'nature' | 'festival';

export type ReferenceSight = { placeId: string; nameKo: string; lineKo: string; lineEn: string };

export const REFERENCE_THEMES: ReadonlyArray<{ key: ReferenceThemeKey; ko: string; en: string; glyph: string }> = [
  { key: 'sea', ko: '바다·해변', en: 'Sea & beaches', glyph: '🌊' },
  { key: 'culture', ko: '문화·사찰', en: 'Culture & temples', glyph: '⛩' },
  { key: 'view', ko: '전망·야경', en: 'Views & night', glyph: '🌉' },
  { key: 'market', ko: '시장·먹거리', en: 'Markets & food', glyph: '🧺' },
  { key: 'nature', ko: '자연·산책', en: 'Nature walks', glyph: '🌿' },
  { key: 'festival', ko: '축제', en: 'Festivals', glyph: '🎉' },
];

const SIGHTS: Record<Exclude<ReferenceThemeKey, 'festival'>, ReferenceSight[]> = {
  sea: [
    { placeId: '434a90ee-c94f-4f0e-a709-007913306001', nameKo: '해운대해수욕장', lineKo: '부산을 대표하는 넓은 모래 해변', lineEn: "Busan's signature wide sandy beach" },
    { placeId: '76363fd8-a555-456e-896a-008202423001', nameKo: '광안리해수욕장', lineKo: '광안대교 야경이 보이는 해변', lineEn: 'Beach facing the lit-up Gwangan Bridge' },
    { placeId: '148d72d0-4748-43b0-a650-000126080001', nameKo: '송정해수욕장', lineKo: '서핑하기 좋은 한적한 해변', lineEn: 'Quiet beach popular for surfing' },
    { placeId: '9e41ac6e-440d-4c42-acf7-000126079001', nameKo: '다대포해수욕장', lineKo: '노을과 갯벌이 아름다운 해변', lineEn: 'Beach known for sunsets and tidal flats' },
  ],
  culture: [
    { placeId: 'ce91b390-e725-455f-ac39-000126848001', nameKo: '해동용궁사', lineKo: '바닷가 바위 위에 지은 절', lineEn: 'Temple built on seaside rocks' },
    { placeId: '9110a719-00ba-4ee7-9c53-026884008001', nameKo: '범어사', lineKo: '금정산 자락의 천년 고찰', lineEn: 'Thousand-year-old temple on Geumjeongsan' },
    { placeId: '87a75bd0-579d-4c80-a44b-021362956001', nameKo: '감천문화마을', lineKo: '언덕 위 알록달록한 골목 마을', lineEn: 'Colorful hillside village of alleys' },
    { placeId: '58c4aa95-087c-46e4-a557-002684712001', nameKo: '흰여울문화마을', lineKo: '바다 절벽을 따라 늘어선 마을', lineEn: 'Village lining a seaside cliff' },
  ],
  view: [
    { placeId: '38f3001e-9e2a-4010-bfe9-53d975322410', nameKo: '부산타워', lineKo: '용두산공원에서 원도심을 내려다보는 탑', lineEn: 'Tower overlooking the old downtown' },
    { placeId: 'd7d68934-1a4e-31c5-836a-0f94134e81e4', nameKo: '황령산 전망대', lineKo: '부산 시내 야경을 한눈에', lineEn: "Panoramic view of Busan's city lights" },
    { placeId: '755d0f09-6bc8-4bdc-b24f-c3807856ad39', nameKo: '더베이101', lineKo: '마린시티 고층 빌딩 야경 명소', lineEn: 'Night view of Marine City towers' },
    { placeId: '6f92f891-0e36-386c-9bd4-e029e1e065e5', nameKo: '청사포다릿돌전망대', lineKo: '바다 위로 뻗은 투명 바닥 전망대', lineEn: 'Glass-floored skywalk over the sea' },
  ],
  market: [
    { placeId: '9fcfcf83-4894-3ea4-a1ea-29ec92c147cc', nameKo: '자갈치시장', lineKo: '싱싱한 회와 해산물의 수산 시장', lineEn: 'Fish market for fresh seafood' },
    { placeId: 'f548797c-361a-3b3a-8210-616be343a29d', nameKo: '국제시장', lineKo: '없는 게 없는 원도심 재래시장', lineEn: 'Old downtown market with everything' },
    { placeId: 'c8d304ae-ac0e-482d-a081-001878218003', nameKo: '부평깡통시장', lineKo: '야시장과 길거리 음식', lineEn: 'Night market and street food' },
    { placeId: 'bfa0b970-f9fc-4b65-880e-8697bda93219', nameKo: 'BIFF광장', lineKo: '씨앗호떡과 영화의 거리', lineEn: 'Seed hotteok and the film street' },
  ],
  nature: [
    { placeId: '50b95fb1-6fb8-4c93-a3eb-001945309001', nameKo: '이기대', lineKo: '바다 절벽을 따라 걷는 해안 산책로', lineEn: 'Coastal trail along sea cliffs' },
    { placeId: 'f092687c-b613-4fcd-9310-b9ec7e2dc55b', nameKo: '동백섬', lineKo: '해운대 끝 동백나무 숲 산책', lineEn: 'Camellia forest walk off Haeundae' },
    { placeId: '0f6ebeb1-c446-31b3-9a4f-499993a2a6af', nameKo: '오륙도 스카이워크', lineKo: '오륙도가 보이는 절벽 위 유리 길', lineEn: 'Glass walkway facing Oryukdo islets' },
    { placeId: 'ba6cf0cb-85e3-4f9c-ad00-000126658001', nameKo: '태종대', lineKo: '숲과 해안 절벽, 등대가 있는 공원', lineEn: 'Park of forest, cliffs and a lighthouse' },
  ],
};

/** 갈래의 고정 명소. 축제는 빈 목록 — 날짜로 받아 온다. */
export function referenceSightsOf(theme: ReferenceThemeKey): ReferenceSight[] {
  return theme === 'festival' ? [] : SIGHTS[theme];
}
