export type MapStop = {
  id: string;
  number: number;
  name: string;
  latitude: number;
  longitude: number;
  // 있으면 마커가 숫자 대신 이 사진을 원형으로 보여준다, 추억 지도).
  // 없는 호출부는 지금처럼 숫자 마커 그대로다.
  imageUrl?: string;
};

/** 지도에 그리는 선의 한 점. 실제 길을 따라가려면 이 점들이 촘촘히 필요하다. */
export type MapPathPoint = { latitude: number; longitude: number };
