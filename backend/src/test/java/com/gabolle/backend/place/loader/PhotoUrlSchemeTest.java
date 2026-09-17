package com.gabolle.backend.place.loader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사진 주소 스킴 — S15P21E201-1185. DB 없이 돈다.
 *
 * <p>여기 쓰는 주소는 <b>운영 API 가 실제로 준 것</b>이다(2026-09-17 `GET /api/v1/places/nearby`).
 * 지어낸 예로 검사하면 원천이 실제로 주는 모양과 어긋나도 초록이 된다.
 */
class PhotoUrlSchemeTest {

	@Test
	@DisplayName("🔴 아는 호스트의 평문 주소는 https 로 바뀐다 — 앱이 평문을 막는다")
	void upgradesTheVerifiedHost() {
		String http = "http://tong.visitkorea.or.kr/cms/resource/68/3026468_image2_1.JPG";

		assertThat(PhotoUrlScheme.secure(http))
				.isEqualTo("https://tong.visitkorea.or.kr/cms/resource/68/3026468_image2_1.JPG");
	}

	@Test
	@DisplayName("이미 https 면 그대로 둔다")
	void leavesHttpsAlone() {
		String https = "https://tong.visitkorea.or.kr/cms/resource/47/4105447_image2_1.jpg";

		assertThat(PhotoUrlScheme.secure(https)).isEqualTo(https);
	}

	@Test
	@DisplayName("🔴 모르는 호스트는 안 건드린다 — 지원 여부를 안 재 봤다")
	void leavesUnverifiedHostsAlone() {
		// 🔴 https 가 되는지 재 보지 않은 호스트를 바꾸면, 지금 보이는 사진까지 깨진다.
		//    「안 보이는 것을 고치려다 보이는 것을 깨뜨리는」 쪽이 더 나쁘다.
		String other = "http://example.or.kr/photo.jpg";

		assertThat(PhotoUrlScheme.secure(other)).isEqualTo(other);
	}

	@Test
	@DisplayName("🔴 호스트 이름이 앞에 붙기만 한 주소는 아는 호스트가 아니다")
	void doesNotMatchAHostPrefix() {
		// tong.visitkorea.or.kr 로 시작하지만 실제 호스트는 다른 곳이다. 호스트 뒤의
		// '/' 까지 봐야 이 주소가 안 걸린다.
		String lookalike = "http://tong.visitkorea.or.kr.example.com/photo.jpg";

		assertThat(PhotoUrlScheme.secure(lookalike)).isEqualTo(lookalike);
	}

	@Test
	@DisplayName("사진이 없으면 null 그대로 — 지어내지 않는다")
	void keepsNull() {
		assertThat(PhotoUrlScheme.secure(null)).isNull();
	}
}
