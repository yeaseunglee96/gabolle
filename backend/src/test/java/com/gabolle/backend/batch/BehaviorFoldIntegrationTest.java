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

import com.gabolle.backend.batch.application.TasteVectorFoldOutcome;
import com.gabolle.backend.batch.application.TasteVectorFoldService;
import com.gabolle.backend.batch.support.BatchPostgresTest;
import com.gabolle.backend.batch.support.TasteVectorFixtures;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.preference.domain.TasteDimension;
import com.gabolle.backend.preference.domain.TasteEvidence;
import com.gabolle.backend.preference.domain.UserTasteWeight;
import com.gabolle.backend.preference.repository.UserTasteWeightRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 행동을 취향 성분으로 귀속시키는 단계 (S15P21E201-1482).
 *
 * <h2>🔴 이 검사가 메우는 구멍</h2>
 *
 * 지금까지 양쪽이 <b>따로</b> 검사되고 있었다. {@code BaselineCandidateScorerTest} 는
 * {@code UserTasteWeight.fromInteraction} 을 <b>손으로 만들어</b> 「INTERACTION 성분이 있으면
 * 점수가 더해진다」를 증명했고, {@code TasteVectorFoldIntegrationTest} 는 워터마크와 눈금을
 * 꼼꼼히 봤지만 <b>{@code evidence} 를 한 번도 단언하지 않았다.</b>
 *
 * <p>그래서 「만드는 쪽이 {@code SURVEY} 만 만들고, 쓰는 쪽이 {@code SURVEY} 를 거른다」는
 * 상태로 두 검사가 모두 초록일 수 있었다. 여기서 그 이음매를 지난다 — <b>실제로 저장된
 * 성분이 채점기의 두 필터를 통과하는지</b>를 본다.
 */
class BehaviorFoldIntegrationTest extends BatchPostgresTest {

	private static final OffsetDateTime DAY1 = OffsetDateTime.of(2026, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC);

	private static final OffsetDateTime DAY2 = OffsetDateTime.of(2026, 8, 2, 0, 0, 0, 0, ZoneOffset.UTC);

	/** 앱 어휘 여섯 중 하나. 조회표가 외래키로 강제하므로 지어낼 수 없다. */
	private static final String CAFE = "CAFE_HEALING";

	@Autowired
	private TasteVectorFoldService foldService;

	@Autowired
	private UserTasteWeightRepository weights;

	@Autowired
	private JdbcTemplate jdbc;

	private TasteVectorFixtures fixtures;

	@BeforeEach
	void setUp() {
		this.fixtures = new TasteVectorFixtures(this.jdbc);
	}

	@Test
	@DisplayName("🔴 좋아요 두 번이 (CATEGORY, 그 태그) 성분이 된다 — evidence=INTERACTION, support=관측 수")
	void likesBecomeACategoryComponent() {
		UUID user = this.fixtures.newUser();
		UUID cafeA = taggedPlace(CAFE);
		UUID cafeB = taggedPlace(CAFE);

		this.fixtures.tasteSignalForPlace(user, EventType.PLACE_LIKE, cafeA, DAY1);
		this.fixtures.tasteSignalForPlace(user, EventType.PLACE_LIKE, cafeB, DAY1);

		this.foldService.fold(user, DAY2);

		UserTasteWeight component = onlyComponent(user);
		assertThat(component.getDimension()).isEqualTo(TasteDimension.CATEGORY);
		assertThat(component.getCode()).isEqualTo(CAFE);
		assertThat(component.getEvidence()).isEqualTo(TasteEvidence.INTERACTION);
		assertThat(component.getSupport()).isEqualTo(2);
		// raw = 2.0, K = 3 → 2/(2+3)
		assertThat(component.getWeight()).isEqualTo(0.4);
	}

	@Test
	@DisplayName("🔴 이음매 — 만들어진 성분이 채점기의 두 필터를 지난다 (dimension=CATEGORY · evidence≠SURVEY)")
	void theComponentPassesTheScorerFilter() {
		UUID user = this.fixtures.newUser();
		this.fixtures.tasteSignalForPlace(user, EventType.PLACE_LIKE, taggedPlace(CAFE), DAY1);
		this.fixtures.tasteSignalForPlace(user, EventType.PLACE_LIKE, taggedPlace(CAFE), DAY1);

		this.foldService.fold(user, DAY2);

		// BaselineCandidateScorer.applyTasteVectorComponent 가 거르는 그 두 줄 그대로다.
		// 여기가 비면 그 항은 오류 없이 0.0 을 돌려주고, 아무 검사도 안 빨개진다.
		assertThat(components(user))
				.filteredOn((w) -> w.getDimension() == TasteDimension.CATEGORY)
				.filteredOn((w) -> w.getEvidence() != TasteEvidence.SURVEY)
				.as("채점기가 읽을 수 있는 성분이 하나도 없으면 행동 개인화는 도는 척만 한다")
				.isNotEmpty();
	}

	@Test
	@DisplayName("🔴 같은 장소의 하트가 두 건 들어와도 한 번으로 센다 — 앱과 저장 API 가 각자 적는다")
	void oneHeartCountsOnceEvenWhenRecordedTwice() {
		UUID user = this.fixtures.newUser();
		UUID cafe = taggedPlace(CAFE);

		// 사용자 조작은 «한 번»이다. 저장 API 가 서버에서 한 건, 앱이 분석 이벤트로 한 건.
		this.fixtures.tasteSignalForPlace(user, EventType.PLACE_LIKE, cafe, DAY1);
		this.fixtures.tasteSignalForPlace(user, EventType.PLACE_LIKE, cafe, DAY1);

		this.foldService.fold(user, DAY2);

		assertThat(components(user))
				.as("두 건으로 세면 support=2 가 되어 「한 번 누른 것을 확신처럼 다루지 않는다」가 무너진다")
				.isEmpty();
	}

	@Test
	@DisplayName("보는 것은 반복이 뜻을 가진다 — 같은 장소를 두 번 보면 두 번 센다")
	void viewsOfTheSamePlaceRepeat() {
		UUID user = this.fixtures.newUser();
		UUID cafe = taggedPlace(CAFE);

		this.fixtures.tasteSignalForPlace(user, EventType.PLACE_VIEW, cafe, DAY1);
		this.fixtures.tasteSignalForPlace(user, EventType.PLACE_VIEW, cafe, DAY1);

		this.foldService.fold(user, DAY2);

		UserTasteWeight component = onlyComponent(user);
		assertThat(component.getSupport())
				.as("하트와 달리 조회는 접으면 「두 번 봤다」와 「한 번 봤다」가 같아진다")
				.isEqualTo(2);
		// raw = 0.2 (0.1 × 2), K = 3 → 0.2/3.2
		assertThat(component.getWeight()).isEqualTo(0.0625);
	}

	@Test
	@DisplayName("한 번 누른 것은 성분이 안 된다 — 뒷받침이 모자라면 확신처럼 다루지 않는다")
	void aSingleObservationIsNotEnough() {
		UUID user = this.fixtures.newUser();
		this.fixtures.tasteSignalForPlace(user, EventType.PLACE_LIKE, taggedPlace(CAFE), DAY1);

		this.foldService.fold(user, DAY2);

		assertThat(components(user)).isEmpty();
	}

	@Test
	@DisplayName("🔴 개인화를 끈 사람은 행동이 성분이 안 된다")
	void behaviorIsIgnoredWhenPersonalizationIsOff() {
		UUID user = this.fixtures.newUser();
		this.fixtures.turnBehaviorPersonalizationOff(user);
		this.fixtures.tasteSignalForPlace(user, EventType.PLACE_LIKE, taggedPlace(CAFE), DAY1);
		this.fixtures.tasteSignalForPlace(user, EventType.PLACE_LIKE, taggedPlace(CAFE), DAY1);

		// 설문이 있어야 판이 만들어진다 — 행동만 있고 동의가 없으면 접을 것이 없다.
		UUID snapshot = this.fixtures.newUserScopeSnapshot(user, DAY1);
		this.fixtures.selectedLikert(snapshot, "QUIETNESS", 5);

		this.foldService.fold(user, DAY2);

		assertThat(components(user))
				.as("설문 성분만 남아야 한다")
				.allMatch((w) -> w.getEvidence() == TasteEvidence.SURVEY);
	}

	@Test
	@DisplayName("🔴 일정에서 뺀 것은 «약한 부정» 이다 — 무게가 음수다")
	void removingFromAnItineraryPushesTheTagDown() {
		UUID user = this.fixtures.newUser();
		UUID tripId = UUID.randomUUID();
		UUID cafeA = taggedPlace(CAFE);
		UUID cafeB = taggedPlace(CAFE);

		this.fixtures.itineraryRemove(user, tripId, null, DAY1, cafeA, cafeB);

		this.foldService.fold(user, DAY2);

		UserTasteWeight component = onlyComponent(user);
		assertThat(component.getSupport()).isEqualTo(2);
		// raw = -1.0 (−0.5 × 2), K = 3 → -1/(1+3)
		assertThat(component.getWeight()).isEqualTo(-0.25);
	}

	@Test
	@DisplayName("🔴 운영 사유가 적힌 제외는 취향이 아니다 — 문 닫아서 뺀 것을 «싫다» 로 배우면 안 된다")
	void operationalRemovalsAreNotTasteSignals() {
		UUID user = this.fixtures.newUser();
		UUID tripId = UUID.randomUUID();

		this.fixtures.itineraryRemove(user, tripId, "CLOSED", DAY1, taggedPlace(CAFE), taggedPlace(CAFE));
		UUID snapshot = this.fixtures.newUserScopeSnapshot(user, DAY1);
		this.fixtures.selectedLikert(snapshot, "QUIETNESS", 5);

		this.foldService.fold(user, DAY2);

		assertThat(components(user)).allMatch((w) -> w.getEvidence() == TasteEvidence.SURVEY);
	}

	@Test
	@DisplayName("🔴 설문과 겹치면 BLENDED «한 행» 이다 — PK 가 (판, 차원, 코드) 라 두 행이 될 수 없다")
	void surveyAndBehaviourMeetInOneBlendedRow() {
		UUID user = this.fixtures.newUser();
		UUID snapshot = this.fixtures.newUserScopeSnapshot(user, DAY1);
		this.fixtures.selectedCodes(snapshot, "CATEGORY", CAFE);

		// 「카페 좋아요」라고 답했는데 카페를 두 번 뺐다.
		this.fixtures.itineraryRemove(user, UUID.randomUUID(), null, DAY1, taggedPlace(CAFE), taggedPlace(CAFE));

		this.foldService.fold(user, DAY2);

		UserTasteWeight component = onlyComponent(user);
		assertThat(component.getEvidence()).isEqualTo(TasteEvidence.BLENDED);
		// 설문 +1.0 에 행동 -0.25 를 더한다. 평균이 아니라 합이다.
		assertThat(component.getWeight()).isEqualTo(0.75);
		assertThat(component.getSupport()).isEqualTo(2);
	}

	@Test
	@DisplayName("확인되지 않은 표식(UNKNOWN)은 안 세어진다 — 모르는 것을 «있다» 로 읽지 않는다")
	void unconfirmedTagsDoNotCount() {
		UUID user = this.fixtures.newUser();
		UUID placeA = this.fixtures.newPlace("CAFE");
		UUID placeB = this.fixtures.newPlace("CAFE");
		this.fixtures.placeTag(placeA, "CATEGORY_TAG", CAFE, "UNKNOWN");
		this.fixtures.placeTag(placeB, "CATEGORY_TAG", CAFE, "UNKNOWN");

		this.fixtures.tasteSignalForPlace(user, EventType.PLACE_LIKE, placeA, DAY1);
		this.fixtures.tasteSignalForPlace(user, EventType.PLACE_LIKE, placeB, DAY1);

		this.foldService.fold(user, DAY2);

		assertThat(components(user)).isEmpty();
	}

	@Test
	@DisplayName("같은 구간을 두 번 접어도 성분이 안 늘어난다")
	void foldingTwiceDoesNotDuplicate() {
		UUID user = this.fixtures.newUser();
		this.fixtures.tasteSignalForPlace(user, EventType.PLACE_LIKE, taggedPlace(CAFE), DAY1);
		this.fixtures.tasteSignalForPlace(user, EventType.PLACE_LIKE, taggedPlace(CAFE), DAY1);

		this.foldService.fold(user, DAY2);
		TasteVectorFoldOutcome second = this.foldService.fold(user, DAY2);

		assertThat(second.action()).isEqualTo(TasteVectorFoldOutcome.Action.UNCHANGED);
		assertThat(components(user)).hasSize(1);
	}

	// ── 거들기 ──────────────────────────────────────────────────────────────

	/** 갈래 표식이 확인된 장소 하나. */
	private UUID taggedPlace(String categoryTag) {
		UUID placeId = this.fixtures.newPlace(categoryTag);
		this.fixtures.placeTag(placeId, "CATEGORY_TAG", categoryTag, "VERIFIED");
		return placeId;
	}

	/** 이 사람의 현재 판에 달린 성분 전부. */
	private List<UserTasteWeight> components(UUID userId) {
		UUID vectorId = this.jdbc.queryForObject(
				"SELECT taste_vector_id FROM user_taste_vector WHERE user_id = ? AND superseded_at IS NULL",
				UUID.class, userId);
		return this.weights.findByIdTasteVectorId(vectorId);
	}

	private UserTasteWeight onlyComponent(UUID userId) {
		List<UserTasteWeight> all = components(userId);
		assertThat(all).hasSize(1);
		return all.get(0);
	}
}
