package com.gabolle.backend.itinerary.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.recommendation.application.port.ItineraryDraft;
import com.gabolle.backend.recommendation.application.port.ItineraryDraftCommand;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.CollaborationSliceApplication;

/**
 * 일정 조립이 장소마다 영업표를 한 번 읽는다 (S15P21E201-1663). 영업시간·브레이크타임·라스트오더 셋이 다 걸리는 48곳으로
 * 8일·하루 5곳을 짠다 — {@link AssembleReadCountIntegrationTest} 에 브레이크타임·라스트오더를 더한 조건이다.
 *
 * <p>지키는 것은 둘이다. 결과가 전과 같다(표로 판정해도 차례·시각·경고가 한 글자도 안 바뀐다). 질의가 장소 수를 따라간다
 * ((장소, 시각)마다 묻던 !1631 은 이 조건에서 457번이었다 — 이 PC 로컬 측정).
 */
@SpringBootTest(classes = CollaborationSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true",
		"spring.jpa.properties.hibernate.generate_statistics=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class AssembleTimeTableIntegrationTest {

	private static final int PLACES = 48;

	private static final String[] CATEGORIES = { "FOOD", "SEA_BEACH", "CULTURE_TEMPLE", "FOOD", "CAFE_HEALING",
			"NATURE_WALK" };

	private static final String[] HOURS = {
			"{\"status\": \"ALWAYS_OPEN\", \"closedDays\": [], \"notes\": [], \"raw\": {}}",
			byDay("[[\"11:00\", \"15:00\"]]", "[[\"11:00\", \"15:00\"]]"),
			byDay("[[\"10:00\", \"18:00\"]]", "[]"),
			byDay("[[\"17:00\", \"23:00\"]]", "[[\"17:00\", \"23:00\"]]"),
			null };

	/**
	 * 고치기 전 코드(!1631 위, (장소, 시각) 답을 기억하던 판)가 이 입력으로 낸 일정 — 날짜마다 {@code 차례:심은번호@시각[경고]}.
	 * 판정 규칙을 <b>일부러</b> 바꾸는 작업이면 새 값으로 갈아 끼운다. 이 시험이 지키는 것은 「표로 판정해도 일정은 안 바뀐다」이다.
	 */
	private static final String BEFORE = "{"
			+ "0=[1:4@09:00[], 2:0@11:24[], 3:32@13:48[], 4:47@16:12[], 5:40@18:36[]], "
			+ "1=[1:29@09:00[], 2:1@11:24[], 3:26@13:48[], 4:18@16:12[OPENING_HOURS_CLOSED], 5:3@18:36[]], "
			+ "2=[1:7@09:00[OPENING_HOURS_CLOSED], 2:35@11:24[], 3:31@13:48[], 4:44@16:12[], 5:33@18:36[]], "
			+ "3=[1:34@09:00[], 2:39@11:24[], 3:25@13:48[], 4:37@16:12[], 5:8@18:36[]], "
			+ "4=[1:15@09:00[], 2:27@11:24[], 3:16@13:48[], 4:17@16:12[], 5:13@18:36[]], "
			+ "5=[1:45@09:00[], 2:24@11:24[], 3:46@13:48[], 4:14@16:12[], 5:9@18:36[]], "
			+ "6=[1:23@09:00[OPENING_HOURS_CLOSED], 2:21@11:24[], 3:11@13:48[], 4:19@16:12[], 5:28@18:36[]], "
			+ "7=[1:5@09:00[], 2:6@11:24[], 3:10@13:48[], 4:2@16:12[], 5:20@18:36[]]}";

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private ItineraryDraftService draftService;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID owner;

	private UUID tripId;

	private final List<ItineraryDraftCommand.PlannedPlace> planned = new ArrayList<>();

	@BeforeEach
	void seed() {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.owner = UUID.randomUUID();
		this.jdbc.update("INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, "
				+ "created_at, updated_at) VALUES (?, '영업표', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)", this.owner, now, now);
		this.tripId = UUID.randomUUID();
		this.jdbc.update("INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, pace, "
				+ "time_window_start, time_window_end, created_at, updated_at) "
				+ "VALUES (?, ?, '2026-10-01', '2026-10-08', 4, 'PACKED', '09:00', '21:00', ?, ?)",
				this.tripId, this.owner, now, now);
		this.jdbc.update("INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) "
				+ "VALUES (?, ?, ?, 'OWNER', now())", UUID.randomUUID(), this.tripId, this.owner);
		long seed = 20260925L;
		for (int i = 0; i < PLACES; i++) {
			seed = (seed * 6364136223846793005L + 1442695040888963407L);
			double lat = 35.08 + ((seed >>> 33) % 1000) / 1000.0 * 0.17;
			seed = (seed * 6364136223846793005L + 1442695040888963407L);
			double lng = 128.97 + ((seed >>> 33) % 1000) / 1000.0 * 0.22;
			UUID placeId = new UUID(0x1663L, i);
			String category = CATEGORIES[i % CATEGORIES.length];
			this.jdbc.update("INSERT INTO place (place_id, name_ko, category, lat, lng, created_at) "
					+ "VALUES (?, ?, ?, ?, ?, now())", placeId, "영업표시험 " + i, category, lat, lng);
			if (HOURS[i % HOURS.length] != null) {
				feature(placeId, "OPENING_HOURS", HOURS[i % HOURS.length]);
			}
			if (i % 3 == 0) {
				feature(placeId, "BREAK_TIME", "{\"start\": \"15:00\", \"end\": \"17:00\"}");
			}
			if (i % 4 == 0) {
				feature(placeId, "LAST_ORDER_TIME", "{\"time\": \"20:00\"}");
			}
			this.planned.add(new ItineraryDraftCommand.PlannedPlace(placeId, i + 1, List.of("REASON"), List.of(),
					category));
		}
	}

	@AfterEach
	void cleanUp() {
		for (ItineraryDraftCommand.PlannedPlace place : this.planned) {
			this.jdbc.update("DELETE FROM place_feature WHERE place_id = ?", place.placeId());
			this.jdbc.update("DELETE FROM place WHERE place_id = ?", place.placeId());
		}
		this.jdbc.update("DELETE FROM trip_member WHERE trip_id = ?", this.tripId);
		this.jdbc.update("DELETE FROM trip WHERE trip_id = ?", this.tripId);
		this.jdbc.update("DELETE FROM app_user WHERE user_id = ?", this.owner);
	}

	@Test
	@DisplayName("🔴 표로 판정해도 일정이 전과 같다 — 차례·시각·경고가 한 글자도 안 바뀐다")
	void theItineraryIsTheSameAsBefore() {
		assertThat(orderOf(this.draftService.assemble(command()))).isEqualTo(BEFORE);
	}

	@Test
	@DisplayName("🔴 질의가 장소 수를 따라간다 — 장소마다 영업표를 한 번 읽는다((장소, 시각)마다 묻던 판은 457번)")
	void readsFollowThePlaceCount() {
		Statistics stats = this.entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		stats.clear();

		this.draftService.assemble(command());

		// 장소 48곳마다 한 번 + 여행·장소·축제 기간 조회 몇 번.
		assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(PLACES + 20);
	}

	private void feature(UUID placeId, String type, String value) {
		this.jdbc.update("""
				INSERT INTO place_feature (place_feature_id, place_id, feature_type, value, evidence_status,
				                           source_type, created_at)
				VALUES (?, ?, ?, ?::jsonb, 'ESTIMATED', 'TEST', now())
				""", UUID.randomUUID(), placeId, type, value);
	}

	private static String byDay(String weekday, String saturday) {
		return "{\"status\": \"PARSED\", \"closedDays\": [], \"notes\": [], \"raw\": {}, \"byDay\": {"
				+ "\"mon\": " + weekday + ", \"tue\": " + weekday + ", \"wed\": " + weekday + ", \"thu\": " + weekday
				+ ", \"fri\": " + weekday + ", \"sat\": " + saturday + ", \"sun\": " + weekday + "}}";
	}

	private ItineraryDraftCommand command() {
		return new ItineraryDraftCommand(UUID.randomUUID(), this.tripId.toString(), this.owner.toString(), this.planned,
				"model-1", "feature-1", "ontology-1", "policy-1", "dataset-1");
	}

	private static String orderOf(ItineraryDraft draft) {
		return draft.items().stream()
				.collect(Collectors.groupingBy(ItineraryDraft.DraftItem::dayIndex, java.util.TreeMap::new,
						Collectors.mapping((item) -> item.sequence() + ":" + item.placeId().getLeastSignificantBits()
								+ "@" + item.startTime() + item.warningCodes(), Collectors.toList())))
				.toString();
	}
}
