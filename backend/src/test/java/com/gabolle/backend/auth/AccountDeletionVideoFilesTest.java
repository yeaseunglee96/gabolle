package com.gabolle.backend.auth;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.auth.service.AccountDeletionService;
import com.gabolle.backend.auth.support.AuthPostgresIntegrationTest;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * S15P21E201-1275 — <b>탈퇴하면 동영상 파일도 실제로 사라지는가</b>, 그리고 <b>탈퇴가 끝까지 도는가.</b>
 *
 * <h2>🔴 이 검사가 막는 것은 「안 지워짐」이 아니라 「탈퇴가 죽는 것」이다</h2>
 *
 * {@code story_video} 가 {@code uploaded_video}·{@code uploaded_image} 를 가리키는데 그 외래키에
 * {@code ON DELETE} 가 없다. 탈퇴가 <b>{@code story_video} 를 먼저 안 지우고</b> 업로드 행을
 * 지우면 외래키 위반으로 <b>트랜잭션이 통째로 되돌아간다</b> — 파일 하나가 아니라
 * <b>탈퇴 전체가 500 으로 죽는다.</b> 애플 심사 5.1.1(v) 항목이다.
 *
 * <p>🔴 <b>그리고 지금 있는 검사가 이 자리에 안 닿는다.</b>
 * {@code AccountDeletionTableInventoryTest} 는 <b>{@code app_user} 로 가는 외래키</b>만 훑는다.
 * {@code story_video} 는 {@code story}·{@code uploaded_video}·{@code uploaded_image} 만 가리키므로
 * <b>그 검사를 조용히 통과한다.</b> 그래서 이 파일이 <b>유일한 그물</b>이다.
 *
 * <h2>🔴 지우기 전에 「넣었는가」를 먼저 센다</h2>
 *
 * 시드가 조용히 실패하면 지운 뒤에도 0건이라 <b>거저 통과한다.</b>
 * {@code AccountDeletionOwnedRowsRemovedTest} 가 같은 규약을 먼저 썼다.
 *
 * <h2>행이 아니라 파일을 본다</h2>
 *
 * 저장 루트를 임시 폴더로 돌리고 <b>진짜 파일을 만들어 둔다.</b> 행만 보면 <b>파일이 저장소에
 * 그대로 남아 있어도 초록</b>이다 — 애플이 묻는 것은 파일 쪽이다.
 */
class AccountDeletionVideoFilesTest extends AuthPostgresIntegrationTest {

	private static final String PASSWORD = "DeleteMe!2026";

	private static final String CONFIRM = AccountDeletionService.CONFIRMATION_PHRASE;

	private static final Path STORAGE_ROOT;

	static {
		try {
			STORAGE_ROOT = Files.createTempDirectory("gabolle-video-withdraw-test");
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@DynamicPropertySource
	static void storageRoot(DynamicPropertyRegistry registry) {
		registry.add("gabolle.storage.root", STORAGE_ROOT::toString);
	}

	@Autowired
	private AccountDeletionService accountDeletionService;

	@Autowired
	private LocalCredentialRepository credentialRepository;

	@Autowired
	private AppUserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transactionTemplate;

	private UUID userId;

	private UUID storyId;

	private UUID videoUploadId;

	private UUID thumbnailUploadId;

	private String videoKey;

	private String thumbnailKey;

	@BeforeEach
	void setUp() {
		this.userId = createUser("video-erase-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
		this.storyId = createStory(this.userId);

		this.videoKey = "story-video/2026/09/" + UUID.randomUUID() + ".mp4";
		this.thumbnailKey = "story/2026/09/" + UUID.randomUUID() + ".jpg";
		this.videoUploadId = insertUploadedVideo(this.videoKey);
		this.thumbnailUploadId = insertUploadedImage(this.thumbnailKey);
		insertStoryVideo();

		writeRealFile(this.videoKey);
		writeRealFile(this.thumbnailKey);
	}

	@AfterEach
	void tearDown() {
		// 🔴 표를 비우지 않는다. 내가 만든 것만 지운다.
		this.jdbc.update("DELETE FROM story_video WHERE story_id = ?", this.storyId);
		this.jdbc.update("DELETE FROM uploaded_video WHERE uploaded_video_id = ?", this.videoUploadId);
		this.jdbc.update("DELETE FROM uploaded_image WHERE uploaded_image_id = ?", this.thumbnailUploadId);
		this.jdbc.update("DELETE FROM story WHERE story_id = ?", this.storyId);
		this.jdbc.update("DELETE FROM auth_session WHERE user_id = ?", this.userId);
		this.jdbc.update("DELETE FROM local_credential WHERE user_id = ?", this.userId);
		this.jdbc.update("DELETE FROM app_user WHERE user_id = ?", this.userId);
	}

	@Test
	@DisplayName("🔴 탈퇴가 외래키로 죽지 않는다 — story_video 를 먼저 안 지우면 탈퇴 전체가 실패한다")
	void withdrawalDoesNotBlowUpOnForeignKey() {
		assertThat(countStoryVideo()).as("시드가 안 들어갔다면 이 검사는 아무것도 막지 못한다").isEqualTo(1);

		assertThatCode(() -> this.accountDeletionService.delete(this.userId, CONFIRM, PASSWORD))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("🔴 탈퇴 뒤 uploaded_video 에 그 사람 행이 0건이다")
	void withdrawalRemovesUploadedVideoRows() {
		// 🔴 먼저 「넣었는가」. 안 넣었으면 지운 뒤 0건인 것이 무엇 때문인지 모른다.
		assertThat(countUploadedVideo()).isEqualTo(1);

		this.accountDeletionService.delete(this.userId, CONFIRM, PASSWORD);

		assertThat(countUploadedVideo()).isZero();
		assertThat(countStoryVideo()).isZero();
	}

	@Test
	@DisplayName("🔴 탈퇴 뒤 동영상 파일과 썸네일 파일이 저장소에서 실제로 사라진다 — 애플 심사 5.1.1(v)")
	void withdrawalRemovesBothFiles() {
		assertThat(fileOf(this.videoKey)).exists();
		assertThat(fileOf(this.thumbnailKey)).exists();

		this.accountDeletionService.delete(this.userId, CONFIRM, PASSWORD);

		assertThat(fileOf(this.videoKey)).doesNotExist();
		assertThat(fileOf(this.thumbnailKey)).doesNotExist();
	}

	// ── 시드 ────────────────────────────────────────────────────────────────

	private UUID createUser(String address) {
		return this.transactionTemplate.execute(status -> {
			AppUser user = this.userRepository.save(AppUser.register("여행자", "KO", Instant.now(), "2026-01",
					PersonalizationMode.EXPLICIT_ONLY, UserStatus.ACTIVE));
			LocalCredential credential = LocalCredential.create(user, address,
					this.passwordEncoder.encode(PASSWORD));
			credential.markEmailVerified(Instant.now());
			this.credentialRepository.save(credential);
			return user.getUserId();
		});
	}

	private UUID createStory(UUID authorUserId) {
		UUID newStoryId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.update("""
				INSERT INTO story (story_id, author_user_id, body, visibility, publish_at, created_at, updated_at)
				VALUES (?, ?, '동영상이 붙은 글', 'PUBLIC', ?, ?, ?)
				""", newStoryId, authorUserId, now.minusHours(1), now, now);
		return newStoryId;
	}

	private UUID insertUploadedVideo(String key) {
		UUID id = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO uploaded_video
				    (uploaded_video_id, uploader_user_id, storage_key, video_url, content_type,
				     byte_size, duration_sec, created_at)
				VALUES (?, ?, ?, ?, 'video/mp4', 4321, 12, now())
				""", id, this.userId, key, "/photos/" + key);
		return id;
	}

	private UUID insertUploadedImage(String key) {
		UUID id = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO uploaded_image
				    (uploaded_image_id, uploader_user_id, storage_key, image_url, content_type, byte_size, created_at)
				VALUES (?, ?, ?, ?, 'image/jpeg', 1234, now())
				""", id, this.userId, key, "/api/v1/uploads/images/" + key);
		return id;
	}

	private void insertStoryVideo() {
		this.jdbc.update("""
				INSERT INTO story_video
				    (story_video_id, story_id, uploaded_video_id, thumbnail_upload_id, created_at)
				VALUES (?, ?, ?, ?, now())
				""", UUID.randomUUID(), this.storyId, this.videoUploadId, this.thumbnailUploadId);
	}

	private void writeRealFile(String key) {
		try {
			Path target = STORAGE_ROOT.resolve(key);
			Files.createDirectories(target.getParent());
			Files.write(target, "seed".getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	// ── 확인 ────────────────────────────────────────────────────────────────

	private java.io.File fileOf(String key) {
		return STORAGE_ROOT.resolve(key).toFile();
	}

	private Integer countUploadedVideo() {
		return this.jdbc.queryForObject("SELECT count(*) FROM uploaded_video WHERE uploader_user_id = ?",
				Integer.class, this.userId);
	}

	private Integer countStoryVideo() {
		return this.jdbc.queryForObject("SELECT count(*) FROM story_video WHERE story_id = ?", Integer.class,
				this.storyId);
	}
}
