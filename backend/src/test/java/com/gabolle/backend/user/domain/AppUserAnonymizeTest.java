package com.gabolle.backend.user.domain;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 탈퇴한 계정에 무엇이 남는가.
 *
 * <p>탈퇴해도 행은 남으므로({@link AppUser#anonymizeForDeletion} 참고) 그 사람은 동행자·팔로우
 * 목록에 계속 보인다. 이름만 지우고 사진 주소를 남기면 목록에 얼굴이 그대로 뜨고, 탈퇴는 그
 * 사진 파일을 실제로 지우므로 그 주소는 없는 파일을 가리킨다.
 */
class AppUserAnonymizeTest {

	@Test
	@DisplayName("🔴 탈퇴하면 프로필 사진·커버 사진 주소가 함께 비워진다 — 파일은 이미 지워진 뒤다")
	void anonymizeClearsBothPhotoUrls() {
		AppUser user = AppUser.register("여행자", "KO", Instant.parse("2026-01-01T00:00:00Z"), "2026-01",
				PersonalizationMode.EXPLICIT_ONLY, UserStatus.ACTIVE);
		user.changeAvatarUrl("/api/v1/uploads/images/2026/09/avatar.webp");
		user.changeCoverUrl("/api/v1/uploads/images/2026/09/cover.webp");

		user.anonymizeForDeletion(Instant.parse("2026-09-19T00:00:00Z"));

		assertThat(user.getAvatarUrl()).as("목록에 얼굴이 남으면 안 된다").isNull();
		assertThat(user.getCoverUrl()).isNull();
		assertThat(user.getDisplayName()).isEqualTo("탈퇴한 사용자");
		assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
	}
}
