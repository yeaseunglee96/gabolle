package com.gabolle.backend.place.loader;

import com.gabolle.backend.place.domain.Place.PhotoSubject;

/**
 * 사진 수집본 한 줄. {@code subject} 는 파일에 적힌 분류가 아니라 {@link PlacePhotoReader} 가
 * 사진 제목과 장소 이름을 대조해 정한 값이고, {@code attribution} 은 화면에 그대로 나갈 문구라
 * 읽는 쪽에서 완성해 둔다 — 적재기가 조립하면 같은 규칙이 두 곳에 생긴다.
 */
record PlacePhotoRow(String contentId, String photoUrl, String attribution, PhotoSubject subject) {
}
