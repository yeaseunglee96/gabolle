package com.gabolle.backend.place.loader;

import com.gabolle.backend.place.domain.Place.PhotoSubject;

/**
 * 사진 수집본 한 줄 — S15P21E201-1006.
 *
 * <p>데이터 파트가 내는 파일({@code bigData/data/staged/festival-photos-busan.ndjson})의
 * 한 줄을 이 모양으로 옮긴다. 파일 이름과 칸 이름은 그쪽이 정본이다.
 *
 * @param subject 🔴 <b>파일에 적힌 분류를 그대로 옮기지 않는다.</b> {@link PlacePhotoReader}
 *     가 <b>사진 자체의 제목과 장소 이름을 직접 대조해</b> 정한다. 이유는 그 클래스에 있다
 * @param attribution 화면에 그대로 나갈 출처 표기 문구. 여기서 완성해 둔다 —
 *     적재기가 문구를 조립하면 같은 규칙이 두 곳에 생긴다
 */
record PlacePhotoRow(String contentId, String photoUrl, String attribution, PhotoSubject subject) {
}
