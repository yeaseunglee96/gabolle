package com.gabolle.backend.place.loader;

import com.gabolle.backend.place.domain.Place.PhotoLicense;
import com.gabolle.backend.place.domain.Place.PhotoSubject;

/**
 * 사진 수집본 한 줄. {@code subject} 는 파일에 적힌 분류가 아니라 {@link PlacePhotoReader} 가
 * 사진 제목과 장소 이름을 대조해 정한 값이고, {@code attribution} 은 화면에 그대로 나갈 문구라
 * 읽는 쪽에서 완성해 둔다 — 적재기가 조립하면 같은 규칙이 두 곳에 생긴다.
 *
 * @param sourceType 장소의 출처({@code place.source_type}). 파일에 {@code contentid} 만 있으면 관광공사다
 * @param sourceId 그 출처의 원천 번호({@code place.source_id})
 * @param license 라이선스. 파일에 없으면 {@code null}
 */
record PlacePhotoRow(String sourceType, String sourceId, String photoUrl, String attribution, PhotoSubject subject,
		PhotoLicense license) {
}
