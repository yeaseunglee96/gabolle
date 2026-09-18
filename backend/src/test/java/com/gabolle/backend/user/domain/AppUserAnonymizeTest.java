package com.gabolle.backend.user.domain;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 탈퇴한 계정에 무엇이 남는가 — S15P21E201-1297.
 *
 * <h2>🔴 이름만 지우고 얼굴을 남기면 지운 것이 아니다</h2>
 *
 * 탈퇴해도 행은 남는다 — 동행자의 일정 편집 이력이 이 행을 가리키기 때문이다
 * ({@link AppUser#anonymizeForDeletion} 참고). 그래서 그 사람은 동행자 목록·팔로우 목록에
 * <b>「탈퇴한 사용자」로 계속 보인다.</b> 이름은 지우면서 <b>프로필 사진과 커버 사진 주소를
 * 남겨 두면</b> 목록에 얼굴이 그대로 뜬다.
 *
 * <p>게다가 탈퇴는 그 사람이 올린 사진 <b>파일을 실제로 지운다</b>
 * ({@code AccountDeletionService.deleteUploadedFiles}). 주소만 남으면 <b>없는 파일을 가리킨
 * 채</b>라 화면에는 깨진 이미지가 뜬다 — 지우는 쪽과 가리키는 쪽이 어긋난 것이다.
 *
 * <p>이 시험이 재는 것은 그 어긋남 하나다. DB 를 안 띄운다 — 엔티티 하나의 상태 변화라서.
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
