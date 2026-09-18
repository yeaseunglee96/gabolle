package com.gabolle.backend.place.loader;

/**
 * 사진 주소의 스킴을 앱이 열 수 있는 것으로 맞춘다 — S15P21E201-1185.
 *
 * <h2>🔴 왜 필요한가</h2>
 *
 * 관광공사 원천이 같은 호스트의 주소를 <b>어떤 건 {@code https}, 어떤 건 {@code http}</b> 로
 * 준다. 그리고 <b>평문 {@code http} 는 앱에서 한 장도 안 보인다.</b>
 *
 * <ul>
 * <li>안드로이드(API 28+) — {@code usesCleartextTraffic} 이 없으면 기본값이 차단</li>
 * <li>iOS — 앱 전송 보안(ATS)이 기본값으로 차단</li>
 * <li>웹 — {@code https} 페이지에서 {@code http} 이미지는 혼합 콘텐츠로 차단</li>
 * </ul>
 *
 * <p>즉 <b>앱·iOS·웹 어디에서도 안 보인다.</b> 설정을 열어 평문을 허용하는 방법도 있지만,
 * 그건 사진 하나 때문에 <b>모든 통신에 평문 구멍</b>을 내는 일이다.
 *
 * <h2>🔴 아는 호스트만 바꾼다</h2>
 *
 * 2026-09-17 에 같은 주소로 실제로 재 봤다 — 호스트도 경로도 같고 스킴만 다른데 둘 다
 * {@code 200 image/jpg} 를 준다.
 *
 * <pre>
 * http://tong.visitkorea.or.kr/cms/resource/68/3026468_image2_1.JPG   → 200 image/jpg
 * https://tong.visitkorea.or.kr/cms/resource/68/3026468_image2_1.JPG  → 200 image/jpg
 * </pre>
 *
 * <p><b>다른 호스트는 안 건드린다.</b> https 를 지원하는지 재 보지 않았고, 지원하지 않는
 * 호스트를 바꾸면 <b>지금 보이는 사진까지 깨진다.</b> 「안 보이는 것을 고치려다 보이는 것을
 * 깨뜨리는」 쪽이 더 나쁘다.
 *
 * <p>호스트가 늘면 <b>재 보고 이 목록에 더한다.</b> 목록 없이 모든 {@code http} 를 바꾸는
 * 방식으로 넓히지 않는다 — 그건 재 보지 않은 것을 된다고 가정하는 일이다.
 *
 * <h2>이 판정이 한 곳에만 있는 이유</h2>
 *
 * 사진 주소가 들어오는 자리가 둘이다 — {@link TourApiPlaceLoader}(원천의 {@code firstimage})와
 * {@link PlacePhotoReader}(사진 수집본의 {@code photoUrl}). 각자 문자열을 만지면 한쪽만
 * 고쳐지는 날이 오고, 그때 <b>일부 사진만 안 보이는</b> 상태가 된다 — 지금이 정확히 그 상태다.
 */
final class PhotoUrlScheme {

	/**
	 * {@code https} 로 바꿔도 되는 것이 확인된 호스트.
	 *
	 * <p>🔴 <b>재 본 것만 넣는다.</b> 이 목록에 없는 호스트는 그대로 둔다.
	 */
	private static final String[] HTTPS_VERIFIED_HOSTS = { "tong.visitkorea.or.kr" };

	private static final String HTTP = "http://";

	private PhotoUrlScheme() {
	}

	/**
	 * 아는 호스트의 평문 주소를 {@code https} 로 바꾼다. 그 밖에는 <b>받은 그대로</b> 돌려준다.
	 *
	 * @param photoUrl 원천이 준 주소. {@code null} 이면 {@code null}
	 */
	static String secure(String photoUrl) {
		if (photoUrl == null || !photoUrl.startsWith(HTTP)) {
			return photoUrl;
		}
		for (String host : HTTPS_VERIFIED_HOSTS) {
			// 🔴 호스트 뒤에 '/' 까지 본다. 안 보면 tong.visitkorea.or.kr.evil.com 같은
			//    주소도 걸린다 — 이 함수가 여는 구멍은 아니지만, 아는 호스트라는 판정이
			//    실제로 그 호스트인 것을 뜻해야 한다.
			if (photoUrl.startsWith(HTTP + host + "/")) {
				return "https://" + photoUrl.substring(HTTP.length());
			}
		}
		return photoUrl;
	}
}
