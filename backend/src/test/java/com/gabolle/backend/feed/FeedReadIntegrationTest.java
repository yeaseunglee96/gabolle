package com.gabolle.backend.feed;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.feed.application.FeedPage;
import com.gabolle.backend.feed.application.FeedQueryService;
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

/**
 * 미리 만들어 둔 피드를 읽는 경로 (S15P21E201-580).
 *
 * <p>여기서 확인하는 것은 "무엇이 나오나" 가 아니라 <b>"읽기가 정말 읽기만 하나"</b> 다.
 * 순서를 다시 매기지 않고, 지난 세대를 안 보고, 없을 때와 빌 때를 가른다.
 */
class FeedReadIntegrationTest extends FeedPostgresTest {

	@Autowired
	private FeedQueryService feedQueryService;

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
	@DisplayName("피드를 만든 적이 없으면 빈 목록이 아니라 '아직 안 만들었다' 로 답한다")
	void 만든_적이_없으면_이유를_알려준다() {
		UUID userId = this.fixtures.newUser();

		FeedPage page = this.feedQueryService.home(userId, null, null);

		// 🔴 빈 목록만 주면 앱은 "아직 안 만들어졌다" 와 "조건에 맞는 게 없다" 를 구분할 수
		//    없다. 앞은 기다리면 풀리고 뒤는 사람이 조건을 바꿔야 풀린다 — 안내가 달라야 한다.
		assertThat(page.buildId()).isNull();
		assertThat(page.items()).isEmpty();
		assertThat(page.emptyReason()).isEqualTo(FeedPage.EmptyReason.NOT_BUILT_YET);
	}

	@Test
	@DisplayName("현재 세대의 줄을 저장된 순서 그대로 읽는다 — 점수로 다시 정렬하지 않는다")
	void 저장된_순서_그대로_읽는다() {
		UUID userId = this.fixtures.newUser();

		// 🔴 점수를 일부러 순서와 거꾸로 넣는다. 읽는 쪽이 점수로 정렬한다면 여기서 드러난다.
		UUID buildId = readyHomeBuild(userId, 3, position -> 0.1 * position);

		FeedPage page = this.feedQueryService.home(userId, null, null);

		assertThat(page.buildId()).isEqualTo(buildId);
		assertThat(page.items()).extracting(item -> item.position()).containsExactly(0, 1, 2);
		assertThat(page.emptyReason()).isNull();
		assertThat(page.stale()).isFalse();
	}

	@Test
	@DisplayName("커서로 이어 읽으면 같은 줄을 두 번 보지 않는다")
	void 커서로_이어_읽는다() {
		UUID userId = this.fixtures.newUser();
		readyHomeBuild(userId, 5, position -> 1.0);

		FeedPage first = this.feedQueryService.home(userId, 2, null);
		assertThat(first.items()).extracting(item -> item.position()).containsExactly(0, 1);
		assertThat(first.nextCursor()).isEqualTo(1);

		FeedPage second = this.feedQueryService.home(userId, 2, first.nextCursor());
		assertThat(second.items()).extracting(item -> item.position()).containsExactly(2, 3);

		FeedPage third = this.feedQueryService.home(userId, 2, second.nextCursor());
		assertThat(third.items()).extracting(item -> item.position()).containsExactly(4);
		// 요청한 만큼 못 채웠으므로 다음이 없다.
		assertThat(third.nextCursor()).isNull();
	}

	@Test
	@DisplayName("한 번에 너무 많이 달라고 해도 상한까지만 준다")
	void 한도를_넘기면_깎는다() {
		UUID userId = this.fixtures.newUser();
		readyHomeBuild(userId, 60, position -> 1.0);

		FeedPage page = this.feedQueryService.home(userId, 10_000, null);

		// 🔴 저장은 싸도 내보내는 것은 안 싸다. 상한이 없으면 요청 하나가 미리 만들어 둔
		//    이점을 통째로 없앤다.
		assertThat(page.items()).hasSize(50);
	}

	@Test
	@DisplayName("자리를 내준 지난 세대는 읽히지 않는다")
	void 지난_세대는_안_읽힌다() {
		UUID userId = this.fixtures.newUser();
		UUID oldBuildId = readyHomeBuild(userId, 2, position -> 1.0);

		// 새 세대로 갈아탄다 — 옛것을 내리고 새것을 올리는 순서다. 뒤집으면 DB 가 거부한다.
		FeedBuild old = this.builds.findById(oldBuildId).orElseThrow();
		old.supersede();
		this.builds.saveAndFlush(old);

		UUID newBuildId = readyHomeBuild(userId, 4, position -> 1.0);

		FeedPage page = this.feedQueryService.home(userId, null, null);

		assertThat(page.buildId()).isEqualTo(newBuildId);
		assertThat(page.items()).hasSize(4);
	}

	@Test
	@DisplayName("만료된 세대는 내용은 그대로 주되 낡았다고 알린다")
	void 만료돼도_보여주고_낡음을_알린다() {
		UUID userId = this.fixtures.newUser();
		UUID buildId = readyHomeBuild(userId, 2, position -> 1.0, FeedFixtures.now().minusHours(1));

		FeedPage page = this.feedQueryService.home(userId, null, null);

		// 🔴 낡았다고 안 보여주지 않는다. 빈 화면을 본 사람은 앱이 고장 났다고 생각하고,
		//    어제 추천이라도 본 사람은 앱을 쓴다. 대신 낡았다는 사실을 숨기지 않는다.
		assertThat(page.buildId()).isEqualTo(buildId);
		assertThat(page.items()).hasSize(2);
		assertThat(page.stale()).isTrue();
	}

	@Test
	@DisplayName("세대는 있는데 줄이 없으면 '만든 적 없음' 이 아니라 '후보가 없음' 이다")
	void 줄이_없는_세대는_다른_이유를_답한다() {
		UUID userId = this.fixtures.newUser();
		readyHomeBuild(userId, 0, position -> 1.0);

		FeedPage page = this.feedQueryService.home(userId, null, null);

		assertThat(page.buildId()).isNotNull();
		assertThat(page.items()).isEmpty();
		assertThat(page.emptyReason()).isEqualTo(FeedPage.EmptyReason.NO_ITEMS);
	}

	@Test
	@DisplayName("커뮤니티 피드와 홈 피드는 서로를 안 본다")
	void 화면마다_세대가_따로다() {
		UUID userId = this.fixtures.newUser();
		readyHomeBuild(userId, 3, position -> 1.0);

		FeedPage community = this.feedQueryService.community(userId, null, null);

		assertThat(community.emptyReason()).isEqualTo(FeedPage.EmptyReason.NOT_BUILT_YET);
	}

	// ── 도우미 ────────────────────────────────────────────────────────────────

	private interface ScoreOf {

		double at(int position);

	}

	private UUID readyHomeBuild(UUID userId, int entryCount, ScoreOf score) {
		return readyHomeBuild(userId, entryCount, score, FeedFixtures.now().plusHours(6));
	}

	/**
	 * 홈 피드 한 세대를 READY 까지 만든다.
	 *
	 * <p>🔴 실제 만드는 코드가 밟을 순서를 그대로 밟는다 — 열고, 재료와 판을 박고, 줄을
	 * 넣고, 마지막에 READY 로 올린다. 테스트가 지름길로 가면 그 순서가 지켜지는지 알 수 없다.
	 */
	private UUID readyHomeBuild(UUID userId, int entryCount, ScoreOf score, OffsetDateTime expiresAt) {
		UUID constraintSnapshotId = this.fixtures.newConstraintSnapshot(userId);
		UUID tasteVectorId = currentTasteVector(userId);

		FeedBuild build = FeedBuild.open(userId, FeedSurface.HOME, FeedFixtures.now());
		build.recordInputs(tasteVectorId, constraintSnapshotId, null);
		build.recordVersions("model-test", "feature-test", "onto-test", "policy-test", "dataset-test", "service-test");
		this.builds.saveAndFlush(build);

		for (int position = 0; position < entryCount; position++) {
			this.entries.save(UserFeedEntry.of(build.getBuildId(), position, FeedItemType.PLACE, UUID.randomUUID(),
					score.at(position), new String[] { "TASTE_MATCH" }, FeedFixtures.payload("장소" + position),
					FeedFixtures.now()));
		}
		this.entries.flush();

		build.markReady(FeedFixtures.now(), expiresAt, entryCount);
		this.builds.saveAndFlush(build);

		return build.getBuildId();
	}

	/** 이 사용자의 현재 취향 판. 없으면 만든다. */
	private UUID currentTasteVector(UUID userId) {
		List<UserTasteVector> existing = this.tasteVectors.findByUserIdAndSupersededAtIsNull(userId).stream().toList();
		if (!existing.isEmpty()) {
			return existing.get(0).getTasteVectorId();
		}
		UserTasteVector vector = UserTasteVector.open(userId, 1, null, 0, null, "vector-test", "onto-test",
				FeedFixtures.now());
		return this.tasteVectors.saveAndFlush(vector).getTasteVectorId();
	}
}
