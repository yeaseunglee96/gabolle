package com.gabolle.backend.place.photo;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Google 장소 번호를 {@code place.photo_file_page} 에 담고 꺼낸다(S15P21E201-1832).
 *
 * <p>칸을 새로 만들지 않고 이 칸을 쓴다. 이 칸은 원래 "사진의 원본 페이지" 이고, Google 사진의
 * 원본 페이지는 그 장소의 Google 지도 페이지다 — Google 약관이 사진 옆에 두라는 링크도 이것이다.
 * 그리고 Google 약관이 영구 저장을 허락하는 것은 장소 번호뿐이라, 사진 주소 대신 이것만 남긴다.
 */
public final class GooglePlacePhotoLink {

	static final String PREFIX = "https://www.google.com/maps/place/?q=place_id:";

	/** Google 장소 번호에 쓰이는 글자. 그 밖의 글자가 섞이면 주소를 조립하지 않는다. */
	private static final Pattern PLACE_ID = Pattern.compile("[A-Za-z0-9_-]{10,300}");

	/** 사진 출처 이름. 앱은 이 이름을 사진 옆 라이선스 칸에 그대로 보여 준다. */
	public static final String LICENSE_NAME = "Google 지도";

	private GooglePlacePhotoLink() {
	}

	public static String filePageOf(String googlePlaceId) {
		if (googlePlaceId == null || !PLACE_ID.matcher(googlePlaceId).matches()) {
			throw new IllegalArgumentException("Google 장소 번호 형식이 아니다");
		}
		return PREFIX + googlePlaceId;
	}

	/** 이 칸이 Google 장소를 가리키면 그 번호를, 아니면 비어 있음을 돌려준다. */
	public static Optional<String> googlePlaceIdOf(String filePage) {
		if (filePage == null || !filePage.startsWith(PREFIX)) {
			return Optional.empty();
		}
		String id = filePage.substring(PREFIX.length());
		return PLACE_ID.matcher(id).matches() ? Optional.of(id) : Optional.empty();
	}
}
