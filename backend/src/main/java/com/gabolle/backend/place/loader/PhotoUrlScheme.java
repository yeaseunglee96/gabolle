package com.gabolle.backend.place.loader;

/**
 * 사진 주소의 스킴을 앱이 열 수 있는 것으로 맞춘다.
 *
 * <p>원천이 같은 호스트의 주소를 어떤 건 {@code https}, 어떤 건 {@code http} 로 주는데 평문
 * {@code http} 는 안드로이드·iOS·웹 어디에서도 차단돼 한 장도 안 보인다. 설정으로 평문을
 * 허용하면 사진 하나 때문에 모든 통신에 평문 구멍을 내게 된다.
 *
 * <p>목록에 있는 호스트만 바꾼다. https 를 지원하는지 재 보지 않은 호스트를 바꾸면 지금 보이는
 * 사진까지 깨진다. 호스트가 늘면 재 보고 목록에 더한다 — 목록 없이 모든 {@code http} 를 바꾸는
 * 쪽으로 넓히지 않는다.
 *
 * <p>판정이 여기 한 곳에만 있다. 사진 주소가 들어오는 자리가 {@link TourApiPlaceLoader} 와
 * {@link PlacePhotoReader} 둘이라, 각자 문자열을 만지면 한쪽만 고쳐져 일부 사진만 안 보이게 된다.
 */
final class PhotoUrlScheme {

	/** {@code https} 로 바꿔도 되는 것을 재 본 호스트. 여기 없는 호스트는 그대로 둔다. */
	private static final String[] HTTPS_VERIFIED_HOSTS = { "tong.visitkorea.or.kr" };

	private static final String HTTP = "http://";

	private PhotoUrlScheme() {
	}

	/**
	 * 아는 호스트의 평문 주소를 {@code https} 로 바꾼다. 그 밖에는 받은 그대로 돌려주고,
	 * {@code null} 이면 {@code null} 이다.
	 */
	static String secure(String photoUrl) {
		if (photoUrl == null || !photoUrl.startsWith(HTTP)) {
			return photoUrl;
		}
		for (String host : HTTPS_VERIFIED_HOSTS) {
			// 호스트 뒤의 '/' 까지 본다. 안 보면 tong.visitkorea.or.kr.evil.com 같은 주소도
			// 걸린다 — 아는 호스트라는 판정이 실제로 그 호스트인 것을 뜻해야 한다.
			if (photoUrl.startsWith(HTTP + host + "/")) {
				return "https://" + photoUrl.substring(HTTP.length());
			}
		}
		return photoUrl;
	}
}
