package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryLeg;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.ItinerarySliceApplication;

/**
 * 구간 선형이 실제 PostgreSQL 을 한 바퀴 돌아 그대로 돌아오는가 — S15P21E201-1251.
 *
 * <p>단위 시험으로는 여기까지 못 본다. 증명할 것이 셋이고 전부 DB 쪽에 있다.
 *
 * <ul>
 * <li>{@code JSONB} 칸에 넣고 꺼내도 좌표가 <b>순서까지</b> 그대로인가 —
 *     {@code [경도, 위도]} 가 뒤집히면 부산 좌표는 위도 35·경도 129 라 숫자가 그럴듯해
 *     보여서, 지도에 그려 보기 전까지 아무도 못 알아챈다</li>
 * <li>{@code ck_itinerary_leg_path} 가 점 하나짜리 선을 진짜로 막는가</li>
 * <li>선형이 없는 구간이 {@code null} 로 남는가 — 빈 배열로 눕지 않는가</li>
 * </ul>
 */
@SpringBootTest(classes = ItinerarySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class ItineraryLegPathPersistenceIntegrationTest {

	/** 해운대 → 광안리 쪽으로 굽어 가는 길. {@code [경도, 위도]} 순서다. */
	private static final List<double[]> ROAD = List.of(
			new double[] { 129.1604, 35.1587 },
			new double[] { 129.1204, 35.1553 },
			new double[] { 129.0756, 35.1796 });

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private ItineraryRepository itineraryRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private String itineraryId;
	private String userId;
	private String fromPlaceId;
	private String toPlaceId;

	@BeforeEach
	void seed() {
		this.userId = UUID.randomUUID().toString();
		String tripId = UUID.randomUUID().toString();
		this.itineraryId = UUID.randomUUID().toString();
		this.fromPlaceId = UUID.randomUUID().toString();
		this.toPlaceId = UUID.randomUUID().toString();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

		this.jdbcTemplate.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				UUID.fromString(this.userId), now, now);
		this.jdbcTemplate.update(
				"INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, created_at, updated_at) "
						+ "VALUES (?, ?, '2026-09-06', '2026-09-08', 1, ?, ?)",
				UUID.fromString(tripId), UUID.fromString(this.userId), now, now);
		this.jdbcTemplate.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, ?)",
				UUID.fromString(this.itineraryId), UUID.fromString(tripId), now);
		this.jdbcTemplate.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, '해운대해수욕장', ?)",
				UUID.fromString(this.fromPlaceId), now);
		this.jdbcTemplate.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, '광안리해수욕장', ?)",
				UUID.fromString(this.toPlaceId), now);
	}

	private ItineraryVersion versionCandidate(int version) {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		return new ItineraryVersion(
				UUID.randomUUID().toString(), this.itineraryId, version, version - 1,
				ItineraryVersion.Operation.REPLACE_ITEM, this.userId, "req_" + UUID.randomUUID(),
				new ItineraryVersion.Versions(null, null, null, null, null), now);
	}

	private ItineraryLeg leg(List<double[]> path) {
		return new ItineraryLeg(UUID.randomUUID().toString(), null, 0, 1,
				this.fromPlaceId, this.toPlaceId, "WALK", 8_400, 21, 8_400, null, null,
				ItineraryItem.DataStatus.VERIFIED, null, path,
				Instant.now().truncatedTo(ChronoUnit.MICROS));
	}

	private ItineraryLeg saveAndReadBack(ItineraryLeg leg, int version) {
		this.itineraryRepository.appendVersion(versionCandidate(version), List.of(), List.of(leg), List.of());
		return this.itineraryRepository.findContent(this.itineraryId, version).orElseThrow().legs().get(0);
	}

	@Test
	@DisplayName("🔴 선형이 좌표 순서까지 그대로 돌아온다 — [경도, 위도] 다")
	void pathSurvivesTheRoundTrip() {
		ItineraryLeg read = saveAndReadBack(leg(ROAD), 2);

		assertThat(read.path()).hasSize(3);
		assertThat(read.path().get(0)[0]).as("첫 점의 경도").isEqualTo(129.1604);
		assertThat(read.path().get(0)[1]).as("첫 점의 위도").isEqualTo(35.1587);
		assertThat(read.path().get(2)[0]).as("끝 점의 경도").isEqualTo(129.0756);
		assertThat(read.path().get(2)[1]).as("끝 점의 위도").isEqualTo(35.1796);
	}

	@Test
	@DisplayName("선형이 없는 구간은 null 로 남는다 — 빈 배열로 눕지 않는다")
	void legWithoutPathStaysNull() {
		assertThat(saveAndReadBack(leg(null), 2).path()).isNull();

		Integer emptyArrays = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM itinerary_leg WHERE path IS NOT NULL AND jsonb_array_length(path) = 0",
				Integer.class);
		assertThat(emptyArrays).isZero();
	}

	@Test
	@DisplayName("🔴 좌표에 NaN 이 섞이면 선만 버리고 일정은 만들어진다 — JSON 이 깨져 INSERT 가 죽지 않는다")
	void nonFiniteCoordinateDropsTheLineNotTheItinerary() {
		// StringBuilder 는 NaN 을 "NaN" 이라고 적는데 그것은 JSON 이 아니다. 막지 않으면 구간
		// 하나 때문에 INSERT 가 깨져 일정 생성 전체가 실패한다 — 선 하나 못 그리는 것과
		// 여행을 못 만드는 것은 값이 다르다.
		ItineraryLeg read = saveAndReadBack(leg(List.of(
				new double[] { 129.1604, 35.1587 },
				new double[] { Double.NaN, 35.1796 })), 2);

		assertThat(read.path()).as("선은 버린다").isNull();
		assertThat(read.distanceM()).as("구간 자체는 남는다").isEqualTo(8_400);
	}

	@Test
	@DisplayName("🔴 ck_itinerary_leg_path 가 점 하나짜리 선을 막는다 — 도메인을 우회해도 막힌다")
	void checkConstraintRejectsASinglePoint() {
		// 도메인은 점 하나를 null 로 눕혀 주지만, 적재기나 손질 SQL 이 DB 로 바로 들어오는 길도
		// 있다. 그때 막는 것은 이 제약뿐이라, 제약이 실제로 살아 있는지를 여기서 본다.
		this.itineraryRepository.appendVersion(versionCandidate(2), List.of(), List.of(leg(ROAD)), List.of());

		assertThatThrownBy(() -> this.jdbcTemplate.update(
				"UPDATE itinerary_leg SET path = '[[129.1604,35.1587]]'::jsonb WHERE to_place_id = ?",
				UUID.fromString(this.toPlaceId)))
				.hasMessageContaining("ck_itinerary_leg_path");
	}
}
