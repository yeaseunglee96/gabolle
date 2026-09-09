package com.gabolle.backend.auth.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.auth.api.UpdateProfileRequest;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 이름·언어 수정 — S15P21E201-423.
 *
 * <p>엔티티를 불러와 고치고 변경 감지에 맡기는 구조라, 여기서는 리포지토리가 돌려준 엔티티가
 * 실제로 바뀌는지를 본다. 401 은 Spring Security 필터 계층이라 서비스 테스트로는 못 잰다 —
 * `SecurityConfig` 의 `anyRequest().authenticated()` 가 담당하고 permitAll 목록에
 * `/api/v1/auth/me` 가 없다는 것으로 확인했다.
 */
class ProfileUpdateServiceTest {

	private final AppUserRepository userRepository = mock(AppUserRepository.class);

	private final CurrentUserService currentUserService = mock(CurrentUserService.class);

	private ProfileUpdateService service;

	private AppUser user;

	@BeforeEach
	void setUp() {
		this.user = AppUser.register("여행자", "KO", Instant.parse("2026-01-01T00:00:00Z"), "2026-01",
				PersonalizationMode.EXPLICIT_ONLY, UserStatus.ACTIVE);
		this.service = new ProfileUpdateService(this.userRepository, this.currentUserService);
		when(this.userRepository.findById(any())).thenReturn(Optional.of(this.user));
		when(this.currentUserService.get(any()))
				.thenAnswer(invocation -> new CurrentUserService.CurrentUser(this.user, "traveler@example.com"));
	}

	@Test
	@DisplayName("이름을 바꾸면 조회 응답에 바뀐 값이 들어 있다")
	void changesDisplayNameAndReturnsUpdatedProfile() {
		var response = this.service.update(UUID.randomUUID(), new UpdateProfileRequest("부산러버", null));

		assertThat(this.user.getDisplayName()).isEqualTo("부산러버");
		assertThat(response.displayName()).isEqualTo("부산러버");
	}

	@Test
	@DisplayName("🔴 보내지 않은 필드는 그대로 둔다 — null 은 지운다가 아니라 안 바꾼다다")
	void partialUpdateLeavesUnsentFieldUntouched() {
		this.service.update(UUID.randomUUID(), new UpdateProfileRequest("부산러버", null));

		assertThat(this.user.getDisplayName()).isEqualTo("부산러버");
		assertThat(this.user.getLanguage()).isEqualTo("KO");

		this.service.update(UUID.randomUUID(), new UpdateProfileRequest(null, "en"));

		assertThat(this.user.getDisplayName()).isEqualTo("부산러버");
		assertThat(this.user.getLanguage()).isEqualTo("EN");
	}

	@Test
	@DisplayName("빈 이름은 거부한다 — 이름 없는 계정을 만들지 않는다")
	void rejectsBlankDisplayName() {
		assertThatThrownBy(() -> this.service.update(UUID.randomUUID(), new UpdateProfileRequest("  ", null)))
				.isInstanceOf(AuthException.class);

		assertThat(this.user.getDisplayName()).isEqualTo("여행자");
	}

	@Test
	@DisplayName("활성 상태가 아닌 계정은 수정할 수 없다")
	void inactiveAccountCannotBeUpdated() {
		AppUser suspended = AppUser.register("정지됨", "KO", Instant.parse("2026-01-01T00:00:00Z"), "2026-01",
				PersonalizationMode.EXPLICIT_ONLY, UserStatus.DELETION_PENDING);
		when(this.userRepository.findById(any())).thenReturn(Optional.of(suspended));

		assertThatThrownBy(() -> this.service.update(UUID.randomUUID(), new UpdateProfileRequest("새이름", null)))
				.isInstanceOf(AuthException.class);

		assertThat(suspended.getDisplayName()).isEqualTo("정지됨");
	}

	@Test
	@DisplayName("언어 값은 정규화되어 저장된다")
	void languageIsNormalized() {
		this.service.update(UUID.randomUUID(), new UpdateProfileRequest(null, "ko-KR"));

		assertThat(this.user.getLanguage()).isEqualTo("KO");
	}
}
