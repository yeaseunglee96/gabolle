export type MapStop = {
  id: string;
  number: number;
  name: string;
  latitude: number;
  longitude: number;
  // 있으면 마커가 숫자 대신 이 사진을 원형으로 보여준다(S15P21E201-248, 추억 지도).
  // 없는 호출부는 지금처럼 숫자 마커 그대로다.
  imageUrl?: string;
};
