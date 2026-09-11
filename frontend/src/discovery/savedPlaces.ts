// 저장한 장소 — 홈 캐러셀의 하트, 장소 상세의 "내 여행 후보에 저장", (tabs)/saved.tsx
// 셋이 같은 기기 로컬 키를 공유한다. 서버 저장이 아니라 AsyncStorage 라 기기를 바꾸면
// 안 보인다 — 그건 알려진 한계고, 서버 쪽 "내 여행 후보" 개념이 생기면 그때 옮긴다.
export const SAVED_PLACES_KEY = 'gabolle.saved-home-places';

// 홈 화면의 3개 데모 카드 — place 표가 비어 있어(-547 적재 전) 실제 API로는 아직 안 나온다.
// 그 밖의 placeId는 실제 API(getPlace)로 조회한다.
export const DEMO_PLACES = {
  haeundae: { titleKo: '해운대 해수욕장', titleEn: 'Haeundae Beach', subtitleKo: '푸른 바다와 도시가 만나는 곳', subtitleEn: 'Where the blue sea meets the city', image: require('../../assets/home/haeundae.png') },
  gwangalli: { titleKo: '광안리 해수욕장', titleEn: 'Gwangalli Beach', subtitleKo: '야경과 함께하는 해변 산책', subtitleEn: 'A beach walk under the night view', image: require('../../assets/home/gwangalli.png') },
  gamcheon: { titleKo: '감천문화마을', titleEn: 'Gamcheon Culture Village', subtitleKo: '형형색색 감성 골목 여행', subtitleEn: 'A colorful walk through winding alleys', image: require('../../assets/home/gamcheon.png') },
} as const;
