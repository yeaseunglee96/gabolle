package com.gabolle.backend.feed;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.feed.domain.FeedBuild;
import com.gabolle.backend.feed.domain.FeedItemType;
import com.gabolle.backend.feed.domain.FeedSurface;
import com.gabolle.backend.feed.domain.UserFeedEntry;
import com.gabolle.backend.feed.repository.FeedBuildRepository;
import com.gabolle.backend.feed.repository.UserFeedRepository;
import com.gabolle.backend.feed.support.FeedFixtures;
import com.gabolle.backend.feed.support.FeedPostgresTest;
import com.gabolle.backend.preference.domain.UserTasteVector;
import com.gabolle.backend.preference.repository.UserTasteVectorRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DB 가 직접 막는 것들. 판정은 DB 하나가 하고 이 테스트는 그 DB 가 실제로 판정하는지를
 * 확인한다. H2 에는 조건부 UNIQUE 색인도 배열 함수도 없어서 진짜 PostgreSQL 이 필요하고,
 * 없으면 건너뜀으로 표시된다.
 */
class FeedBuildConstraintIntegrationTest extends FeedPostgresTest {

	@Autowired
	private FeedBuildRepository builds;

	@Autowired
	private UserFeedRepository entries;

	@Autowired
	private UserTasteVectorRepository tasteVectors;

	@Autowired
	private JdbcTemplate jdbc;

	private FeedFixtures fixtures;

	@BeforeEach
	void setUp() {
		this.fixtures = new FeedFixtures(this.jdbc);
	}

	@Test
	@DisplayName("한 사용자·한 화면에 현재 세대가 둘이 될 수 없다 — 원자적 교체의 핵심")
	void 현재_세대는_하나뿐이다() {
		UUID userId = this.fixtures.newUser();
		readyBuild(userId, FeedSurface.HOME);

		FeedBuild second = openBuild(userId, FeedSurface.HOME);

		// 옛 세대를 안 내리고 새 세대를 올리려 한다.
		assertThatThrownBy(() -> {
			second.markReady(FeedFixtures.now(), FeedFixtures.now().plusHours(6), 0);
			this.builds.saveAndFlush(second);
		}).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("옛 세대를 먼저 내리면 새 세대를 올릴 수 있다")
	void 순서를_지키면_갈아탈_수_있다() {
		UUID userId = this.fixtures.newUser();
		UUID oldBuildId = readyBuild(userId, FeedSurface.HOME);

		FeedBuild old = this.builds.findById(oldBuildId).orElseThrow();
		old.supersede();
		this.builds.saveAndFlush(old);

		assertThatCode(() -> readyBuild(userId, FeedSurface.HOME)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("같은 사용자라도 화면이 다르면 현재 세대를 각각 가진다")
	void 화면이_다르면_따로_센다() {
		UUID userId = this.fixtures.newUser();
		readyBuild(userId, FeedSurface.HOME);

		assertThatCode(() -> readyBuild(userId, FeedSurface.COMMUNITY)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("이유 없는 추천은 저장할 수 없다")
	void 이유가_없으면_거부한다() {
		UUID userId = this.fixtures.newUser();
		UUID buildId = openBuild(userId, FeedSurface.HOME).getBuildId();

		assertThatThrownBy(() -> this.entries.saveAndFlush(UserFeedEntry.of(buildId, 0, FeedItemType.PLACE,
				UUID.randomUUID(), 1.0, new String[0], FeedFixtures.payload("이유없음"), FeedFixtures.now())))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("홈 세대는 무엇으로 걸렀는지 모르면 현재가 될 수 없다")
	void 홈은_제약_스냅샷_없이_못_올라간다() {
		UUID userId = this.fixtures.newUser();

		FeedBuild build = FeedBuild.open(userId, FeedSurface.HOME, FeedFixtures.now());
		// 취향 판만 박고 제약 스냅샷은 일부러 뺀다.
		build.recordInputs(currentTasteVector(userId), null, null);
		build.recordVersions("m", "f", "o", "p", "d", "s");
		this.builds.saveAndFlush(build);

		// 홈은 장소를 추천하므로 알레르기·휠체어 같은 안전 판정이 걸린다.
		assertThatThrownBy(() -> {
			build.markReady(FeedFixtures.now(), FeedFixtures.now().plusHours(6), 0);
			this.builds.saveAndFlush(build);
		}).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("현재 세대는 언제까지 믿을지가 반드시 있어야 한다")
	void 만료_없는_현재_세대는_없다() {
		UUID userId = this.fixtures.newUser();
		UUID constraintSnapshotId = this.fixtures.newConstraintSnapshot(userId);

		FeedBuild build = FeedBuild.open(userId, FeedSurface.HOME, FeedFixtures.now());
		build.recordInputs(currentTasteVector(userId), constraintSnapshotId, null);
		build.recordVersions("m", "f", "o", "p", "d", "s");
		this.builds.saveAndFlush(build);

		// null 은 "만료 없음" 이 아니라 "미정" 이다.
		assertThatThrownBy(() -> {
			build.markReady(FeedFixtures.now(), null, 0);
			this.builds.saveAndFlush(build);
		}).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("취향 판도 한 사람에 현재가 하나뿐이다")
	void 취향_판도_현재는_하나다() {
		UUID userId = this.fixtures.newUser();
		currentTasteVector(userId);

		assertThatThrownBy(() -> this.tasteVectors.saveAndFlush(
				UserTasteVector.open(userId, 2, null, 0, null, "vector-test", "onto-test", FeedFixtures.now())))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("행동을 반영했다면 어디까지 반영했는지가 반드시 남는다")
	void 행동을_봤으면_어디까지_봤는지_남는다() {
		UUID userId = this.fixtures.newUser();

		// 이어 붙일 기준점이 없는 벡터는 다음 계산에서 같은 행동을 두 번 센다.
		assertThatThrownBy(() -> this.tasteVectors.saveAndFlush(UserTasteVector.open(userId, 1, null, 42, null,
				"vector-test", "onto-test", FeedFixtures.now())))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("같은 세대에 같은 항목이 두 번 뜨지 않는다")
	void 한_세대에_같은_것은_한_번만() {
		UUID userId = this.fixtures.newUser();
		UUID buildId = openBuild(userId, FeedSurface.HOME).getBuildId();
		UUID placeId = UUID.randomUUID();

		this.entries.saveAndFlush(UserFeedEntry.of(buildId, 0, FeedItemType.PLACE, placeId, 1.0,
				new String[] { "TASTE_MATCH" }, FeedFixtures.payload("광안리"), FeedFixtures.now()));

		assertThatThrownBy(() -> this.entries.saveAndFlush(UserFeedEntry.of(buildId, 1, FeedItemType.PLACE, placeId,
				0.9, new String[] { "NEARBY" }, FeedFixtures.payload("광안리"), FeedFixtures.now())))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("만들다 실패해도 이유 없이는 남지 않는다")
	void 실패에는_이유가_붙는다() {
		UUID userId = this.fixtures.newUser();
		FeedBuild build = openBuild(userId, FeedSurface.HOME);

		build.fail("ENGINE_TIMEOUT");
		this.builds.saveAndFlush(build);

		assertThat(this.builds.findById(build.getBuildId()).orElseThrow().getFailureReason())
			.isEqualTo("ENGINE_TIMEOUT");
	}

	// 도우미

	private FeedBuild openBuild(UUID userId, FeedSurface surface) {
		FeedBuild build = FeedBuild.open(userId, surface, FeedFixtures.now());
		build.recordInputs(currentTasteVector(userId), this.fixtures.newConstraintSnapshot(userId), null);
		build.recordVersions("m", "f", "o", "p", "d", "s");
		return this.builds.saveAndFlush(build);
	}

	private UUID readyBuild(UUID userId, FeedSurface surface) {
		FeedBuild build = openBuild(userId, surface);
		build.markReady(FeedFixtures.now(), FeedFixtures.now().plusHours(6), 0);
		this.builds.saveAndFlush(build);
		return build.getBuildId();
	}

	private UUID currentTasteVector(UUID userId) {
		return this.tasteVectors.findByUserIdAndSupersededAtIsNull(userId)
			.map(UserTasteVector::getTasteVectorId)
			.orElseGet(() -> this.tasteVectors
				.saveAndFlush(UserTasteVector.open(userId, 1, null, 0, null, "vector-test", "onto-test",
						FeedFixtures.now()))
				.getTasteVectorId());
	}
}
