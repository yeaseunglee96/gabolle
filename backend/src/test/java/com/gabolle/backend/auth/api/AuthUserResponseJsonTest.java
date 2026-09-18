package com.gabolle.backend.auth.api;

import java.util.UUID;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.user.domain.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 내 정보 응답의 <b>JSON 모양</b> — S15P21E201-1297.
 *
 * <h2>🔴 여기서 재는 것은 값이 아니라 「칸이 있느냐」다</h2>
 *
 * 커버 사진이 없으면 <b>칸째 빠져야</b> 한다. {@code "coverUrl": null} 이 오면 화면은
 * <b>「사용자가 고른 사진」과 「아직 안 고른 사람」을 구분할 수 없다</b> — 「기본 사진으로
 * 되돌리기」 단추를 언제 보여줄지 정할 수 없게 된다.
 *
 * <p>그 판정을 하는 것은 {@code @JsonInclude(NON_NULL)} 어노테이션 한 줄이고, <b>어노테이션은
 * 지워도 아무것도 안 깨진다.</b> 서비스 시험은 {@code response.coverUrl()} 이 {@code null} 인
 * 것만 보므로 그 줄이 사라져도 초록이다. 그래서 <b>직렬화된 글자를 직접 보는 시험</b>이 따로 있다.
 *
 * <h2>🔴 {@code avatarUrl} 은 반대로 「있어야」 한다</h2>
 *
 * 이미 배포된 앱이 그 칸에 {@code null} 이 오는 것을 보고 있다. 지금 키를 빼면 이 티켓과 상관없는
 * 화면이 바뀐다. 두 칸의 모양이 다른 것은 실수가 아니므로, <b>그 차이 자체를 여기서 잰다</b> —
 * 나중에 「통일하자」며 한쪽을 고치면 이 시험이 먼저 말한다.
 */
class AuthUserResponseJsonTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("🔴 커버 사진이 없으면 칸째 빠진다 — 빈 문자열도 null 도 보내지 않는다")
	void omitsCoverUrlWhenThereIsNone() {
		String json = this.objectMapper.writeValueAsString(response(null));

		assertThat(json).doesNotContain("coverUrl");
	}

	@Test
	@DisplayName("커버 사진이 있으면 그 주소가 실린다")
	void includesCoverUrlWhenThereIsOne() {
		String json = this.objectMapper.writeValueAsString(response("/api/v1/uploads/images/2026/09/cover.webp"));

		assertThat(json).contains("\"coverUrl\":\"/api/v1/uploads/images/2026/09/cover.webp\"");
	}

	@Test
	@DisplayName("🔴 프로필 사진 칸은 비어도 남는다 — 배포된 앱이 그 모양을 보고 있다")
	void keepsAvatarUrlKeyEvenWhenNull() {
		String json = this.objectMapper.writeValueAsString(response(null));

		assertThat(json).as("커버와 모양이 다른 것은 의도다 — 클래스 주석 참고").contains("\"avatarUrl\":null");
	}

	private static AuthUserResponse response(String coverUrl) {
		return new AuthUserResponse(UUID.randomUUID(), "traveler@example.com", "부산여행자", "KO", UserStatus.ACTIVE,
				null, coverUrl);
	}
}
