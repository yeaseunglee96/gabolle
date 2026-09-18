package com.gabolle.backend.auth.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.auth.api.UpdateProfileRequest;
import com.gabolle.backend.story.domain.UploadedImage;
import com.gabolle.backend.story.repository.UploadedImageRepository;
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
 *
 * <p>🔴 커버 사진(S15P21E201-1297)은 <b>받아들이는 기준이 프로필 사진과 다르다.</b> 프로필은
 * 주소의 앞부분만 보고, 커버는 <b>올린 사람이 본인인지</b>까지 본다. 그래서 「남의 사진 주소를
 * 그대로 넣어 보는」 시험이 아래에 따로 있다 — 그 시험이 없으면 두 기준이 같아지는 날에도 아무도
 * 모른다.
 */
class ProfileUpdateServiceTest {

	private static final String COVER_URL = "/api/v1/uploads/images/2026/09/cover.webp";

	private final AppUserRepository userRepository = mock(AppUserRepository.class);

	private final CurrentUserService currentUserService = mock(CurrentUserService.class);

	private final UploadedImageRepository uploadedImageRepository = mock(UploadedImageRepository.class);

	private ProfileUpdateService service;

	private AppUser user;

	@BeforeEach
	void setUp() {
		this.user = AppUser.register("여행자", "KO", Instant.parse("2026-01-01T00:00:00Z"), "2026-01",
				PersonalizationMode.EXPLICIT_ONLY, UserStatus.ACTIVE);
		this.service = new ProfileUpdateService(this.userRepository, this.currentUserService,
				this.uploadedImageRepository, "/api/v1/uploads/images", "");
		when(this.userRepository.findById(any())).thenReturn(Optional.of(this.user));
		when(this.currentUserService.get(any()))
				.thenAnswer(invocation -> new CurrentUserService.CurrentUser(this.user, "traveler@example.com"));
	}

	@Test
	@DisplayName("이름을 바꾸면 조회 응답에 바뀐 값이 들어 있다")
	void changesDisplayNameAndReturnsUpdatedProfile() {
		var response = this.service.update(UUID.randomUUID(), new UpdateProfileRequest("부산러버", null, null, null));

		assertThat(this.user.getDisplayName()).isEqualTo("부산러버");
		assertThat(response.displayName()).isEqualTo("부산러버");
	}

	@Test
	@DisplayName("🔴 보내지 않은 필드는 그대로 둔다 — null 은 지운다가 아니라 안 바꾼다다")
	void partialUpdateLeavesUnsentFieldUntouched() {
		this.service.update(UUID.randomUUID(), new UpdateProfileRequest("부산러버", null, null, null));

		assertThat(this.user.getDisplayName()).isEqualTo("부산러버");
		assertThat(this.user.getLanguage()).isEqualTo("KO");

		this.service.update(UUID.randomUUID(), new UpdateProfileRequest(null, "en", null, null));

		assertThat(this.user.getDisplayName()).isEqualTo("부산러버");
		assertThat(this.user.getLanguage()).isEqualTo("EN");
	}

	@Test
	@DisplayName("빈 이름은 거부한다 — 이름 없는 계정을 만들지 않는다")
	void rejectsBlankDisplayName() {
		assertThatThrownBy(() -> this.service.update(UUID.randomUUID(), new UpdateProfileRequest("  ", null, null, null)))
				.isInstanceOf(AuthException.class);

		assertThat(this.user.getDisplayName()).isEqualTo("여행자");
	}

	@Test
	@DisplayName("활성 상태가 아닌 계정은 수정할 수 없다")
	void inactiveAccountCannotBeUpdated() {
		AppUser suspended = AppUser.register("정지됨", "KO", Instant.parse("2026-01-01T00:00:00Z"), "2026-01",
				PersonalizationMode.EXPLICIT_ONLY, UserStatus.DELETION_PENDING);
		when(this.userRepository.findById(any())).thenReturn(Optional.of(suspended));

		assertThatThrownBy(() -> this.service.update(UUID.randomUUID(), new UpdateProfileRequest("새이름", null, null, null)))
				.isInstanceOf(AuthException.class);

		assertThat(suspended.getDisplayName()).isEqualTo("정지됨");
	}

	@Test
	@DisplayName("언어 값은 정규화되어 저장된다")
	void languageIsNormalized() {
		this.service.update(UUID.randomUUID(), new UpdateProfileRequest(null, "ko-KR", null, null));

		assertThat(this.user.getLanguage()).isEqualTo("KO");
	}

	@Test
	@DisplayName("올린 사진 주소를 프로필 사진으로 저장하고 응답에 실어 보낸다")
	void savesAvatarUrlFromOurUploadPath() {
		var response = this.service.update(UUID.randomUUID(),
				new UpdateProfileRequest(null, null, "/api/v1/uploads/images/2026/09/abc.webp", null));

		assertThat(this.user.getAvatarUrl()).isEqualTo("/api/v1/uploads/images/2026/09/abc.webp");
		assertThat(response.avatarUrl()).isEqualTo("/api/v1/uploads/images/2026/09/abc.webp");
	}

	@Test
	@DisplayName("빈 문자열은 사진을 뗀다 — 표에는 빈 문자열이 아니라 없음으로 남는다")
	void blankAvatarUrlRemovesThePhoto() {
		this.user.changeAvatarUrl("/api/v1/uploads/images/2026/09/abc.webp");

		var response = this.service.update(UUID.randomUUID(), new UpdateProfileRequest(null, null, "", null));

		assertThat(this.user.getAvatarUrl()).isNull();
		assertThat(response.avatarUrl()).isNull();
	}

	@Test
	@DisplayName("사진 칸을 안 보내면 원래 사진이 그대로 남는다")
	void omittedAvatarUrlKeepsThePhoto() {
		this.user.changeAvatarUrl("/api/v1/uploads/images/2026/09/abc.webp");

		this.service.update(UUID.randomUUID(), new UpdateProfileRequest("부산러버", null, null, null));

		assertThat(this.user.getAvatarUrl()).isEqualTo("/api/v1/uploads/images/2026/09/abc.webp");
	}

	@Test
	@DisplayName("🔴 우리 업로드 자리가 아닌 주소는 거부한다 — 남의 서버 사진을 프로필로 걸 수 없다")
	void rejectsAvatarUrlOutsideOurStorage() {
		assertThatThrownBy(() -> this.service.update(UUID.randomUUID(),
				new UpdateProfileRequest(null, null, "https://evil.example.com/tracker.png", null)))
				.isInstanceOfSatisfying(AuthException.class,
						(e) -> assertThat(e.getCode()).isEqualTo("AVATAR_URL_NOT_ALLOWED"));

		assertThat(this.user.getAvatarUrl()).isNull();
	}

	@Test
	@DisplayName("🔴 저장소 설정이 비어 있으면 어떤 주소도 받지 않는다 — 설정 누락을 조용히 통과시키지 않는다")
	void rejectsEveryAvatarUrlWhenStorageIsNotConfigured() {
		ProfileUpdateService unconfigured = new ProfileUpdateService(this.userRepository, this.currentUserService,
				this.uploadedImageRepository, "", "");

		assertThatThrownBy(() -> unconfigured.update(UUID.randomUUID(),
				new UpdateProfileRequest(null, null, "/api/v1/uploads/images/2026/09/abc.webp", null)))
				.isInstanceOfSatisfying(AuthException.class,
						(e) -> assertThat(e.getCode()).isEqualTo("AVATAR_URL_NOT_ALLOWED"));
	}

	// ── 커버 사진 — S15P21E201-1297 ─────────────────────────────────────────

	@Test
	@DisplayName("내가 올린 사진을 커버로 저장하고 응답에 실어 보낸다")
	void savesCoverUrlFromMyOwnUpload() {
		UUID me = UUID.randomUUID();
		givenUpload(COVER_URL, me);

		var response = this.service.update(me, new UpdateProfileRequest(null, null, null, COVER_URL));

		assertThat(this.user.getCoverUrl()).isEqualTo(COVER_URL);
		assertThat(response.coverUrl()).isEqualTo(COVER_URL);
	}

	@Test
	@DisplayName("🔴 남이 올린 사진은 주소를 알아도 거절한다 — 프로필 사진의 앞부분 검사는 이것을 못 잡는다")
	void rejectsCoverUrlUploadedBySomeoneElse() {
		UUID me = UUID.randomUUID();
		UUID someoneElse = UUID.randomUUID();
		// 🔴 우리 업로드 자리에서 나온 주소다 — 즉 프로필 사진이 쓰는 앞부분 검사는 통과한다.
		givenUpload(COVER_URL, someoneElse);

		assertThatThrownBy(() -> this.service.update(me, new UpdateProfileRequest(null, null, null, COVER_URL)))
				.isInstanceOfSatisfying(AuthException.class,
						(e) -> assertThat(e.getCode()).isEqualTo("COVER_URL_NOT_OWNED"));

		assertThat(this.user.getCoverUrl()).isNull();
	}

	@Test
	@DisplayName("🔴 올라간 적 없는 주소는 거절한다 — 남의 서버 사진도 여기서 함께 막힌다")
	void rejectsCoverUrlThatWasNeverUploaded() {
		when(this.uploadedImageRepository.findByImageUrlIn(any())).thenReturn(List.of());

		assertThatThrownBy(() -> this.service.update(UUID.randomUUID(),
				new UpdateProfileRequest(null, null, null, "https://evil.example.com/tracker.png")))
				.isInstanceOfSatisfying(AuthException.class,
						(e) -> assertThat(e.getCode()).isEqualTo("COVER_URL_NOT_UPLOADED"));
	}

	@Test
	@DisplayName("이미 지워진 사진은 커버로 쓸 수 없다 — 행은 남아 있어도 파일은 없다")
	void rejectsCoverUrlOfDeletedUpload() {
		UUID me = UUID.randomUUID();
		UploadedImage deleted = upload(COVER_URL, me);
		deleted.markDeleted(Instant.parse("2026-09-18T00:00:00Z"));
		when(this.uploadedImageRepository.findByImageUrlIn(any())).thenReturn(List.of(deleted));

		assertThatThrownBy(() -> this.service.update(me, new UpdateProfileRequest(null, null, null, COVER_URL)))
				.isInstanceOfSatisfying(AuthException.class,
						(e) -> assertThat(e.getCode()).isEqualTo("COVER_URL_NOT_UPLOADED"));
	}

	@Test
	@DisplayName("빈 문자열은 커버를 뗀다 — 「기본 사진으로 되돌리기」가 이것이다")
	void blankCoverUrlRemovesTheCover() {
		this.user.changeCoverUrl(COVER_URL);

		var response = this.service.update(UUID.randomUUID(), new UpdateProfileRequest(null, null, null, ""));

		assertThat(this.user.getCoverUrl()).isNull();
		assertThat(response.coverUrl()).isNull();
	}

	@Test
	@DisplayName("커버 칸을 안 보내면 원래 커버가 그대로 남는다")
	void omittedCoverUrlKeepsTheCover() {
		this.user.changeCoverUrl(COVER_URL);

		this.service.update(UUID.randomUUID(), new UpdateProfileRequest("부산러버", null, null, null));

		assertThat(this.user.getCoverUrl()).isEqualTo(COVER_URL);
	}

	@Test
	@DisplayName("🔴 커버를 바꿔도 프로필 사진은 안 건드린다 — 한 요청이 두 사진을 쥐고 있다")
	void changingCoverLeavesAvatarAlone() {
		UUID me = UUID.randomUUID();
		this.user.changeAvatarUrl("/api/v1/uploads/images/2026/09/avatar.webp");
		givenUpload(COVER_URL, me);

		this.service.update(me, new UpdateProfileRequest(null, null, null, COVER_URL));

		assertThat(this.user.getAvatarUrl()).isEqualTo("/api/v1/uploads/images/2026/09/avatar.webp");
		assertThat(this.user.getCoverUrl()).isEqualTo(COVER_URL);
	}

	// ── 시드 ────────────────────────────────────────────────────────────────

	private void givenUpload(String imageUrl, UUID uploaderUserId) {
		when(this.uploadedImageRepository.findByImageUrlIn(any()))
				.thenReturn(List.of(upload(imageUrl, uploaderUserId)));
	}

	private static UploadedImage upload(String imageUrl, UUID uploaderUserId) {
		return new UploadedImage(UUID.randomUUID(), uploaderUserId, "story/2026/09/cover.webp", imageUrl,
				"image/webp", 1234, Instant.parse("2026-09-18T00:00:00Z"));
	}
}
