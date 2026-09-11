package com.gabolle.backend.batch;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.batch.application.TasteVectorBatchService;
import com.gabolle.backend.batch.application.TasteVectorFoldOutcome;
import com.gabolle.backend.batch.application.TasteVectorFoldService;
import com.gabolle.backend.batch.support.BatchPostgresTest;
import com.gabolle.backend.batch.support.TasteVectorFixtures;
import com.gabolle.backend.preference.domain.TasteDimension;
import com.gabolle.backend.preference.domain.UserTasteVector;
import com.gabolle.backend.preference.repository.UserTasteVectorRepository;
import com.gabolle.backend.preference.repository.UserTasteWeightRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 설문·행동을 취향 벡터로 접는 배치 (MLOps Phase 1).
 *
 * <p>여기서 확인하는 것은 "숫자가 예쁘게 나오나" 가 아니다. <b>같은 구간을 두 번 봐도 같은
 * 결과인가</b>, 그리고 <b>안 물어본 것을 0 으로 적지 않는가</b> 다. 그 둘이 이 배치에서
 * 조용히 틀릴 수 있는 전부이고, DB 제약이 못 잡는 부분이다.
 */
class TasteVectorFoldIntegrationTest extends BatchPostgresTest {

	@Autowired
	private TasteVectorFoldService foldService;

	@Autowired
	private TasteVectorBatchService batchService;

	@Autowired
	private UserTasteVectorRepository vectors;

	@Autowired
	private UserTasteWeightRepository weights;

	@Autowired
	private JdbcTemplate jdbc;

	private TasteVectorFixtures fixtures;

	private static final OffsetDateTime DAY1 = OffsetDateTime.of(2026, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC);

	private static final OffsetDateTime DAY2 = OffsetDateTime.of(2026, 8, 2, 0, 0, 0, 0, ZoneOffset.UTC);

	private static final OffsetDateTime DAY3 = OffsetDateTime.of(2026, 8, 3, 0, 0, 0, 0, ZoneOffset.UTC);

	@BeforeEach
	void setUp() {
		this.fixtures = new TasteVectorFixtures(this.jdbc);
	}

	@Test
	@DisplayName("고른 답만 성분이 된다 — 건너뛴 차원은 0 이 아니라 행이 없다")
	void skippedDimensionsProduceNoRow() {
		UUID userId = this.fixtures.newUser();
		UUID snapshot = this.fixtures.newUserScopeSnapshot(userId, DAY1);
		this.fixtures.selectedCodes(snapshot, "CATEGORY", "CAFE", "MARKET");
		this.fixtures.skipped(snapshot, "FOOD_PREFERENCE");

		TasteVectorFoldOutcome outcome = this.foldService.fold(userId, DAY2);

		assertThat(outcome.action()).isEqualTo(TasteVectorFoldOutcome.Action.REBUILT);
		assertThat(outcome.weightCount()).isEqualTo(2);

		// 🔴 이것이 이 배치의 가장 중요한 약속이다. 건너뛴 차원에 0 이 들어가면
		//    "안 좋아한다" 와 "안 물어봤다" 가 같은 값이 되고, 그때부터 추천은 물어본
		//    적도 없이 그 차원을 근거로 후보를 뺀다. 되돌릴 수 없는 종류의 오류다.
		assertThat(this.weights.findByIdTasteVectorId(outcome.tasteVectorId()))
			.extracting(w -> w.getId().getDimension())
			.containsOnly(TasteDimension.CATEGORY)
			.doesNotContain(TasteDimension.FOOD_PREFERENCE);
	}

	@Test
	@DisplayName("점수 0.75 는 무게 +0.5 가 된다 — 0.5(중립)가 0 이 되도록 늘린다")
	void scoreAnswersStretchToWeightRange() {
		UUID userId = this.fixtures.newUser();
		UUID snapshot = this.fixtures.newUserScopeSnapshot(userId, DAY1);
		this.fixtures.selectedScore(snapshot, "QUIETNESS", 0.75);

		TasteVectorFoldOutcome outcome = this.foldService.fold(userId, DAY2);

		assertThat(this.weights.findByIdTasteVectorId(outcome.tasteVectorId()))
			.singleElement()
			.satisfies(w -> {
				assertThat(w.getId().getDimension()).isEqualTo(TasteDimension.QUIETNESS);
				assertThat(w.getWeight()).isEqualTo(0.5);
			});
	}

	@Test
	@DisplayName("같은 구간을 두 번 돌려도 판이 늘지 않는다 — 재시도가 안전하다")
	void foldingTheSameWindowTwiceIsIdempotent() {
		UUID userId = this.fixtures.newUser();
		UUID snapshot = this.fixtures.newUserScopeSnapshot(userId, DAY1);
		this.fixtures.selectedCodes(snapshot, "CATEGORY", "CAFE");

		TasteVectorFoldOutcome first = this.foldService.fold(userId, DAY2);
		TasteVectorFoldOutcome second = this.foldService.fold(userId, DAY2);

		assertThat(first.action()).isEqualTo(TasteVectorFoldOutcome.Action.REBUILT);
		// 🔴 두 번째는 아무것도 안 한다. Airflow 의 재시도가 판을 하나 더 만들면
		//    version 이 "몇 번 재시도했나" 를 세게 되고, 아무 뜻도 없어진다.
		assertThat(second.action()).isEqualTo(TasteVectorFoldOutcome.Action.UNCHANGED);
		assertThat(second.tasteVectorId()).isEqualTo(first.tasteVectorId());
		assertThat(currentVersionCount(userId)).isEqualTo(1);
	}

	@Test
	@DisplayName("설문도 행동도 안 바뀌면 판을 만들지 않고 표시만 앞으로 옮긴다")
	void quietWindowOnlyAdvancesTheWatermark() {
		UUID userId = this.fixtures.newUser();
		UUID snapshot = this.fixtures.newUserScopeSnapshot(userId, DAY1);
		this.fixtures.selectedCodes(snapshot, "CATEGORY", "CAFE");

		TasteVectorFoldOutcome first = this.foldService.fold(userId, DAY2);
		TasteVectorFoldOutcome next = this.foldService.fold(userId, DAY3);

		assertThat(next.action()).isEqualTo(TasteVectorFoldOutcome.Action.WATERMARK_ADVANCED);
		assertThat(next.tasteVectorId()).isEqualTo(first.tasteVectorId());
		assertThat(next.version()).isEqualTo(first.version());

		UserTasteVector current = this.vectors.findByUserIdAndSupersededAtIsNull(userId).orElseThrow();
		assertThat(current.getObservedUntil()).isEqualTo(DAY3);
		// 성분은 한 줄도 안 바뀐다 — 표시만 움직였다.
		assertThat(this.weights.findByIdTasteVectorId(current.getTasteVectorId())).hasSize(1);
	}

	@Test
	@DisplayName("설문을 다시 내면 판이 갈리고, 현재 판은 여전히 하나다")
	void newSurveySupersedesTheOldVector() {
		UUID userId = this.fixtures.newUser();
		UUID first = this.fixtures.newUserScopeSnapshot(userId, DAY1);
		this.fixtures.selectedCodes(first, "CATEGORY", "CAFE");
		this.foldService.fold(userId, DAY2);

		UUID second = this.fixtures.newUserScopeSnapshot(userId, DAY2);
		this.fixtures.selectedCodes(second, "CATEGORY", "MARKET", "MUSEUM");

		TasteVectorFoldOutcome outcome = this.foldService.fold(userId, DAY3);

		assertThat(outcome.action()).isEqualTo(TasteVectorFoldOutcome.Action.REBUILT);
		assertThat(outcome.version()).isEqualTo(2);
		assertThat(outcome.weightCount()).isEqualTo(2);

		// 🔴 조건부 UNIQUE 색인(uq_user_taste_vector_current)이 이것을 DB 에서 막는다.
		//    옛 판을 내리기 전에 새 판을 넣으면 여기서 터진다 — 그 순서를 검사하는 줄이다.
		assertThat(currentVersionCount(userId)).isEqualTo(1);
		// 옛 판은 지워지지 않는다. 과거 추천을 설명하려면 남아 있어야 한다.
		assertThat(totalVersionCount(userId)).isEqualTo(2);
	}

	@Test
	@DisplayName("늦게 도착한 이벤트를 빠뜨리지 않는다 — 구간을 도착 시각으로 센다")
	void lateArrivingEventsAreStillCounted() {
		UUID userId = this.fixtures.newUser();
		UUID snapshot = this.fixtures.newUserScopeSnapshot(userId, DAY1);
		this.fixtures.selectedCodes(snapshot, "CATEGORY", "CAFE");
		this.foldService.fold(userId, DAY2);

		// 🔴 8월 1일에 **일어난** 일이 8월 2일에 **도착했다** — 비행기 모드였다가 켠 경우다.
		//    구간을 occurred_at 으로 세면 표시가 이미 8월 2일을 지나 있어 이 이벤트는
		//    영원히 안 읽힌다. received_at 으로 세야 다음 구간에서 정확히 한 번 읽힌다.
		this.fixtures.tasteSignal(userId, "PLACE_LIKE", DAY1.plusHours(3), DAY2.plusHours(5));

		TasteVectorFoldOutcome outcome = this.foldService.fold(userId, DAY3);

		assertThat(outcome.action()).isEqualTo(TasteVectorFoldOutcome.Action.REBUILT);
		assertThat(outcome.newEventCount()).isEqualTo(1);

		UserTasteVector current = this.vectors.findByUserIdAndSupersededAtIsNull(userId).orElseThrow();
		assertThat(current.getObservedEventCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("이벤트를 두 구간에 걸쳐 두 번 세지 않는다")
	void eventsAreNeverCountedTwice() {
		UUID userId = this.fixtures.newUser();
		UUID snapshot = this.fixtures.newUserScopeSnapshot(userId, DAY1);
		this.fixtures.selectedCodes(snapshot, "CATEGORY", "CAFE");
		this.fixtures.tasteSignal(userId, "PLACE_LIKE", DAY1.plusHours(1), DAY1.plusHours(1));

		this.foldService.fold(userId, DAY2);
		this.foldService.fold(userId, DAY3);

		UserTasteVector current = this.vectors.findByUserIdAndSupersededAtIsNull(userId).orElseThrow();
		// 🔴 표시가 없으면 같은 이벤트가 구간마다 다시 세어진다. 그러면 행동 수가
		//    날마다 불어나는데, 값이 있기는 하므로 아무 제약도 그것을 못 잡는다.
		assertThat(current.getObservedEventCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("설문도 행동도 없는 사람에게 빈 벡터를 만들지 않는다")
	void usersWithNothingGetNoVector() {
		UUID userId = this.fixtures.newUser();

		TasteVectorFoldOutcome outcome = this.foldService.fold(userId, DAY2);

		assertThat(outcome.action()).isEqualTo(TasteVectorFoldOutcome.Action.NOTHING_TO_FOLD);
		// 🔴 성분이 없는 벡터는 "취향이 없는 사람" 처럼 보이는데 사실은 "아직 안 물어본
		//    사람" 이다. 한 번 섞으면 되돌릴 수 없다.
		assertThat(this.vectors.findByUserIdAndSupersededAtIsNull(userId)).isEmpty();
	}

	@Test
	@DisplayName("여행 전용 취향은 계정 기본 벡터에 섞이지 않는다")
	void tripScopedPreferencesDoNotLeakIntoTheAccountVector() {
		UUID userId = this.fixtures.newUser();
		UUID accountSnapshot = this.fixtures.newUserScopeSnapshot(userId, DAY1);
		this.fixtures.selectedCodes(accountSnapshot, "CATEGORY", "CAFE");

		TasteVectorFoldOutcome outcome = this.foldService.fold(userId, DAY2);

		// 계정 기본 답 하나만 접혔다. TRIP 범위 스냅샷은 애초에 조회되지 않는다
		// (수집 명세 2.2 — 여행에서 고친 값이 계정 기본값을 덮어쓰면 안 된다).
		assertThat(outcome.weightCount()).isEqualTo(1);
		UserTasteVector current = this.vectors.findByUserIdAndSupersededAtIsNull(userId).orElseThrow();
		assertThat(current.getSourcePreferenceSnapshotId()).isEqualTo(accountSnapshot);
	}

	@Test
	@DisplayName("탈퇴한 계정은 접을 대상으로 고르지 않는다")
	void deletedAccountsAreNeverSelected() {
		UUID deleted = this.fixtures.newDeletedUser();
		UUID snapshot = this.fixtures.newUserScopeSnapshot(deleted, DAY1);
		this.fixtures.selectedCodes(snapshot, "CATEGORY", "CAFE");

		List<UUID> stale = this.batchService.staleUsers(DAY2, 500).userIds();

		// 🔴 잊어 달라고 한 사람의 취향을 배치가 다시 계산해 새 행으로 적으면,
		//    탈퇴 처리가 지운 것을 배치가 되살리는 셈이다. 기능 결함이 아니라
		//    개인정보 사고이고, 배치는 사람이 안 보는 시간에 도니 아무도 눈치채지 못한다.
		assertThat(stale).doesNotContain(deleted);
	}

	@Test
	@DisplayName("표시가 뒤처진 사람만 고른다")
	void staleUsersOnlyIncludesUsersBehindTheWatermark() {
		UUID behind = this.fixtures.newUser();
		UUID snapshot = this.fixtures.newUserScopeSnapshot(behind, DAY1);
		this.fixtures.selectedCodes(snapshot, "CATEGORY", "CAFE");

		assertThat(this.batchService.staleUsers(DAY2, 500).userIds()).contains(behind);

		this.foldService.fold(behind, DAY2);

		// 이제 표시가 DAY2 다. 같은 시각을 기준으로는 더 이상 뒤처져 있지 않다.
		assertThat(this.batchService.staleUsers(DAY2, 500).userIds()).doesNotContain(behind);
		// 그러나 시각이 앞으로 가면 다시 후보다 — 그 사이에 무언가 도착했을 수 있다.
		assertThat(this.batchService.staleUsers(DAY3, 500).userIds()).contains(behind);
	}

	@Test
	@DisplayName("🔴 상한에 걸려 잘리면 truncated 로 알린다 — 딱 맞는 것과 구분된다")
	void truncationIsReportedNotHidden() {
		// 뒤처진 사람 셋을 만든다.
		for (int i = 0; i < 3; i++) {
			UUID user = this.fixtures.newUser();
			this.fixtures.selectedCodes(this.fixtures.newUserScopeSnapshot(user, DAY1), "CATEGORY", "CAFE");
		}

		// 🔴 둘만 달라고 하면 셋 중 둘이 온다. 그때 "둘 왔다" 만으로는 마침 둘이었는지
		//    더 있는데 잘렸는지 알 수 없다 — 그 구분이 없으면 밀린 사람이 하루에
		//    상한만큼씩만 빠지면서 배치는 날마다 초록이다.
		TasteVectorBatchService.StalePage cut = this.batchService.staleUsers(DAY2, 2);

		assertThat(cut.userIds()).hasSize(2);
		assertThat(cut.truncated()).isTrue();
		assertThat(cut.limit()).isEqualTo(2);
	}

	@Test
	@DisplayName("상한에 안 걸리면 truncated 는 거짓이다 — 늘 참이면 경고가 무의미해진다")
	void truncationIsFalseWhenEverythingFits() {
		UUID user = this.fixtures.newUser();
		this.fixtures.selectedCodes(this.fixtures.newUserScopeSnapshot(user, DAY1), "CATEGORY", "CAFE");

		TasteVectorBatchService.StalePage whole = this.batchService.staleUsers(DAY2, 500);

		assertThat(whole.userIds()).contains(user);
		assertThat(whole.truncated()).isFalse();
	}

	private int currentVersionCount(UUID userId) {
		Integer count = this.jdbc.queryForObject(
				"SELECT count(*) FROM user_taste_vector WHERE user_id = ? AND superseded_at IS NULL", Integer.class,
				userId);
		return (count == null) ? 0 : count;
	}

	private int totalVersionCount(UUID userId) {
		Integer count = this.jdbc.queryForObject("SELECT count(*) FROM user_taste_vector WHERE user_id = ?",
				Integer.class, userId);
		return (count == null) ? 0 : count;
	}
}
