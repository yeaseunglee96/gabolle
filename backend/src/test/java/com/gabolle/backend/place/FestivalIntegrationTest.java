package com.gabolle.backend.place;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.gabolle.backend.place.api.FestivalResponse;
import com.gabolle.backend.place.api.FestivalResponse.FestivalItem;
import com.gabolle.backend.place.service.FestivalQueryService;
import com.gabolle.backend.place.service.PlaceRequestException;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 여행 기간과 겹치는 축제 조회. 겹침 판정은 {@code PlaceEventPeriodRepository.findOverlapping}
 * 의 SQL 이 하고 서비스는 장소 정보만 붙이므로, 경계값에서 SQL 이 맞게 거르는가와 그 결과를
 * 서비스가 그대로 살리는가를 함께 본다.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class FestivalIntegrationTest extends PlacePostgresIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private FestivalQueryService festivalQueryService;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	private PlaceFixture fixture;

	@BeforeEach
	void setUp() {
		this.fixture = new PlaceFixture(this.jdbcTemplate);
	}

	@AfterEach
	void tearDown() {
		// place_event_period 은 place 를 ON DELETE CASCADE 로 참조하므로 따로 지울 표가 없다.
		this.fixture.cleanUp();
	}

	@Test
	@DisplayName("경계값 — 축제 종료일이 여행 시작일과 같은 날이면 겹친다")
	void festivalEndingOnTripStartDayIsIncluded() {
		UUID placeId = this.fixture.insertPlace("경계축제", null, "ATTRACTION", 35.1, 129.0);
		insertEventPeriod(placeId, "경계 축제", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5));

		FestivalResponse response = this.festivalQueryService.findOverlapping(
				LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 10));

		FestivalItem item = itemFor(response, placeId);
		assertThat(item.overlapDates()).containsExactly(LocalDate.of(2026, 9, 5));
	}

	/** 위 테스트가 {@code endDate >= :from} 쪽 경계를 보고, 이쪽이 {@code startDate <= :to} 쪽을 본다. */
	@Test
	@DisplayName("경계값 — 축제 시작일이 여행 종료일과 같은 날이면 겹친다")
	void festivalStartingOnTripEndDayIsIncluded() {
		UUID placeId = this.fixture.insertPlace("반대경계축제", null, "ATTRACTION", 35.1, 129.0);
		insertEventPeriod(placeId, "반대 경계 축제", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 25));

		FestivalResponse response = this.festivalQueryService.findOverlapping(
				LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 20));

		FestivalItem item = itemFor(response, placeId);
		assertThat(item.overlapDates()).containsExactly(LocalDate.of(2026, 11, 20));
	}

	@Test
	@DisplayName("여행 기간보다 완전히 앞서거나 완전히 뒤인 축제는 안 나온다")
	void festivalCompletelyOutsideTripRangeIsExcluded() {
		UUID before = this.fixture.insertPlace("이전축제", null, "ATTRACTION", 35.1, 129.0);
		insertEventPeriod(before, "이전", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10));
		UUID after = this.fixture.insertPlace("이후축제", null, "ATTRACTION", 35.1, 129.0);
		insertEventPeriod(after, "이후", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 10));

		FestivalResponse response = this.festivalQueryService.findOverlapping(
				LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 10));

		assertThat(response.items()).extracting(FestivalItem::placeId).doesNotContain(before, after);
	}

	@Test
	@DisplayName("축제가 여행 기간을 완전히 감싸도 나온다 — overlapDates 는 여행 기간 전체")
	void festivalEncompassingTripRangeIsIncludedWithTripRangeAsOverlap() {
		UUID placeId = this.fixture.insertPlace("긴축제", null, "ATTRACTION", 35.1, 129.0);
		insertEventPeriod(placeId, "긴 축제", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

		FestivalResponse response = this.festivalQueryService.findOverlapping(
				LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 15));

		FestivalItem item = itemFor(response, placeId);
		assertThat(item.overlapDates()).containsExactly(
				LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 12),
				LocalDate.of(2026, 3, 13), LocalDate.of(2026, 3, 14), LocalDate.of(2026, 3, 15));
	}

	@Test
	@DisplayName("같은 장소의 회차 둘 중 겹치는 것만 나온다")
	void onlyTheOverlappingPeriodOfTheSamePlaceIsReturned() {
		UUID placeId = this.fixture.insertPlace("두회차장소", null, "ATTRACTION", 35.1, 129.0);
		insertEventPeriod(placeId, "안겹침", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5));
		insertEventPeriod(placeId, "겹침", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10));

		FestivalResponse response = this.festivalQueryService.findOverlapping(
				LocalDate.of(2026, 4, 5), LocalDate.of(2026, 4, 20));

		List<FestivalItem> matches = response.items().stream()
				.filter(item -> placeId.equals(item.placeId()))
				.toList();
		assertThat(matches).hasSize(1);
		assertThat(matches.get(0).title()).isEqualTo("겹침");
	}

	@Test
	@DisplayName("overlapDates 는 축제 기간과 여행 기간의 교집합과 정확히 같다 — 부분 겹침(시작 쪽)")
	void overlapDatesIsExactlyTheIntersectionForPartialOverlapAtStart() {
		UUID placeId = this.fixture.insertPlace("부분겹침", null, "ATTRACTION", 35.1, 129.0);
		insertEventPeriod(placeId, "부분 겹침", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5));

		FestivalResponse response = this.festivalQueryService.findOverlapping(
				LocalDate.of(2026, 5, 3), LocalDate.of(2026, 5, 10));

		FestivalItem item = itemFor(response, placeId);
		assertThat(item.overlapDates()).containsExactly(
				LocalDate.of(2026, 5, 3), LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 5));
	}

	@Test
	@DisplayName("종료일이 시작일보다 빠르면 400 이고 fields 에 endDate 가 담긴다")
	void endDateBeforeStartDateIsRejected() {
		assertThatThrownBy(() -> this.festivalQueryService.findOverlapping(
				LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 1)))
				.isInstanceOf(PlaceRequestException.class)
				.satisfies(exception -> {
					PlaceRequestException requestException = (PlaceRequestException) exception;
					assertThat(requestException.getCode()).isEqualTo("INVALID_REQUEST");
					assertThat(requestException.getFields()).contains("endDate");
				});
	}

	@Test
	@DisplayName("367일 범위는 400, 366일 범위는 통과한다 — 상한 경계")
	void rangeLongerThan366DaysIsRejectedButExactly366Passes() {
		LocalDate start = LocalDate.of(2026, 1, 1);

		// 포함 일수 367일(경계 하루 넘김)
		LocalDate end367 = start.plusDays(366);
		assertThatThrownBy(() -> this.festivalQueryService.findOverlapping(start, end367))
				.isInstanceOf(PlaceRequestException.class)
				.satisfies(exception -> assertThat(((PlaceRequestException) exception).getFields()).isNotEmpty());

		// 포함 일수 366일(상한 그대로) — 거절되면 안 된다
		LocalDate end366 = start.plusDays(365);
		assertThatCode(() -> this.festivalQueryService.findOverlapping(start, end366))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("겹치는 축제가 없으면 예외가 아니라 빈 목록이다")
	void noOverlappingFestivalsReturnsEmptyListNotAnError() {
		FestivalResponse response = this.festivalQueryService.findOverlapping(
				LocalDate.of(2001, 1, 1), LocalDate.of(2001, 1, 5));

		assertThat(response.items()).isEmpty();
		assertThat(response.count()).isZero();
	}

	@Test
	@DisplayName("🔴 축제가 3건일 때와 30건일 때 질의 수가 같다 — 회차마다 장소·입장료를 따로 읽지 않는다")
	void queryCountDoesNotGrowWithFestivalCount() {
		LocalDate tripStart = LocalDate.of(2026, 7, 1);
		LocalDate tripEnd = LocalDate.of(2026, 7, 31);

		insertFestivals(3, tripStart, tripEnd);
		long withThree = countStatementsWhileFinding(tripStart, tripEnd);

		insertFestivals(27, tripStart, tripEnd);
		long withThirty = countStatementsWhileFinding(tripStart, tripEnd);

		assertThat(withThree).isEqualTo(withThirty);
		// 회차 목록 한 번, 장소 배치 조회 한 번, 입장료 배치 조회 한 번 — 셋이다.
		assertThat(withThirty).isEqualTo(3);
	}

	@Test
	@DisplayName("입장료 행이 없으면 priceLevel 은 null 이고, photoUrl 도 지금은 항상 null 이다")
	void priceLevelAndPhotoUrlAreNullWhenNoDataExists() {
		UUID placeId = this.fixture.insertPlace("데이터없음", null, "ATTRACTION", 35.1, 129.0);
		insertEventPeriod(placeId, "데이터 없는 축제", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5));

		FestivalResponse response = this.festivalQueryService.findOverlapping(
				LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5));

		FestivalItem item = itemFor(response, placeId);
		assertThat(item.priceLevel()).isNull();
		// photoUrl 은 지금 채우는 경로가 없어 항상 null 이다.
		assertThat(item.photoUrl()).isNull();
	}

	@Test
	@DisplayName("입장료 행이 있으면 값과 evidenceStatus 가 함께 온다 — 회차마다 따로 조회하지 않는다")
	void priceLevelIsIncludedWhenFeatureRowExists() {
		UUID placeId = this.fixture.insertPlace("입장료있음", null, "ATTRACTION", 35.1, 129.0);
		insertEventPeriod(placeId, "입장료 있는 축제", LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 15));
		this.fixture.insertValueFeature(placeId, "PRICE_LEVEL", "ESTIMATED", "{\"level\": 2}");

		FestivalResponse response = this.festivalQueryService.findOverlapping(
				LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 15));

		FestivalItem item = itemFor(response, placeId);
		assertThat(item.priceLevel()).isNotNull();
		assertThat(item.priceLevel().evidenceStatus()).isEqualTo("ESTIMATED");
		assertThat(item.priceLevel().value().get("level").asInt()).isEqualTo(2);
	}

	private void insertFestivals(int count, LocalDate tripStart, LocalDate tripEnd) {
		for (int index = 0; index < count; index++) {
			UUID placeId = this.fixture.insertPlace("대량축제" + index, null, "ATTRACTION", 35.1, 129.0);
			insertEventPeriod(placeId, "대량 축제 " + index, tripStart, tripEnd);
		}
	}

	private long countStatementsWhileFinding(LocalDate startDate, LocalDate endDate) {
		Statistics statistics = this.entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		long before = statistics.getPrepareStatementCount();
		this.festivalQueryService.findOverlapping(startDate, endDate);
		return statistics.getPrepareStatementCount() - before;
	}

	private FestivalItem itemFor(FestivalResponse response, UUID placeId) {
		return response.items().stream()
				.filter(item -> placeId.equals(item.placeId()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("응답에 해당 장소가 없다: " + placeId));
	}

	/** {@code PlaceFixture} 에는 회차를 넣는 메서드가 없어 여기서 직접 넣는다. */
	private void insertEventPeriod(UUID placeId, String title, LocalDate startDate, LocalDate endDate) {
		this.jdbcTemplate.update("""
				INSERT INTO place_event_period (place_event_period_id, place_id, title, start_date, end_date,
				                                 source_type, source_id, observed_at, created_at)
				VALUES (?, ?, ?, ?, ?, 'FIXTURE', ?, now(), now())
				""",
				UUID.randomUUID(), placeId, title, startDate, endDate, this.fixture.token());
	}
}
