package com.gabolle.backend.itinerary.application;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 일정 조립이 DB 를 몇 번 읽나 (S15P21E201-1621). 운영에서 7박 8일·하루 5곳 추천의 조립이 8.3초 걸렸다.
 *
 * <p>차례 고르기({@code shortestSlotOrder})가 하루 장소의 모든 차례를 따지며 차례마다·자리마다 영업시간·브레이크타임·
 * 라스트오더를 DB 에서 새로 읽었다. 판정은 「어느 장소가 몇 번째 자리」에만 달려 있어 서로 다른 경우는 장소 수 × 자리 수뿐이다.
 *
 * <p>진짜 DB 에 운영 사례와 같은 모양(8일 · 하루 5곳 · 좌표가 흩어진 48곳)을 심고 조립을 부른다. 장소 번호와 좌표는
 * 매번 같다 — 같은 입력이면 같은 일정인지({@link #theItineraryIsTheSameAsBefore})를 보려면 입력이 같아야 한다.
 */
@SpringBootTest(classes = CollaborationSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true",
		"spring.jpa.properties.hibernate.generate_statistics=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class AssembleReadCountIntegrationTest {

	private static final int PLACES = 48;

	private static final String[] CATEGORIES = { "FOOD", "SEA_BEACH", "CULTURE_TEMPLE", "FOOD", "CAFE_HEALING",
			"NATURE_WALK" };

	/**
	 * 영업시간 넷을 돌려 붙인다 — 늘 연다 · 점심만(11~15시) · 토요일 쉼 · 저녁만(17~23시) · 모름. 닫는 곳이 섞여야
	 * 차례 고르기의 「걸리는 수가 늘지 않는다」 조건이 실제로 차례를 가른다. 다 열려 있으면 판정은 아무것도 안 바꾼다.
	 */
	private static final String[] HOURS = {
			"{\"status\": \"ALWAYS_OPEN\", \"closedDays\": [], \"notes\": [], \"raw\": {}}",
			byDay("[[\"11:00\", \"15:00\"]]", "[[\"11:00\", \"15:00\"]]"),
			byDay("[[\"10:00\", \"18:00\"]]", "[]"),
			byDay("[[\"17:00\", \"23:00\"]]", "[[\"17:00\", \"23:00\"]]"),
			null };

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

	/**
	 * 고치기 전 코드(back/dev 929d65b79)가 이 입력으로 낸 일정 — 날짜마다 {@code 차례:심은번호@시각[경고]}.
	 * 차례 규칙을 <b>일부러</b> 바꾸는 작업이면 새 값으로 갈아 끼운다. 이 시험이 지키는 것은 「판정을 기억해 쓰는
	 * 것만으로는 일정이 안 바뀐다」이다.
	 */
	private static final String BEFORE =
			"{0=[1:4@09:00[], 2:0@11:24[], 3:32@13:48[], 4:47@16:12[], 5:12@18:36[OPENING_HOURS_CLOSED]], "
			+ "1=[1:29@09:00[], 2:1@11:24[], 3:26@13:48[], 4:18@16:12[OPENING_HOURS_CLOSED], 5:3@18:36[]], "
			+ "2=[1:7@09:00[OPENING_HOURS_CLOSED], 2:35@11:24[], 3:31@13:48[], 4:38@16:12[OPENING_HOURS_CLOSED], 5:33@18:36[]], "
			+ "3=[1:25@09:00[], 2:37@11:24[], 3:34@13:48[], 4:39@16:12[], 5:8@18:36[]], "
			+ "4=[1:15@09:00[], 2:16@11:24[], 3:17@13:48[], 4:27@16:12[], 5:13@18:36[]], "
			+ "5=[1:41@09:00[OPENING_HOURS_CLOSED], 2:24@11:24[], 3:46@13:48[], 4:14@16:12[], 5:9@18:36[]], "
			+ "6=[1:23@09:00[OPENING_HOURS_CLOSED], 2:21@11:24[], 3:11@13:48[], 4:19@16:12[], 5:28@18:36[]], "
			+ "7=[1:5@09:00[], 2:6@11:24[], 3:10@13:48[], 4:2@16:12[], 5:20@18:36[]]}";

	@BeforeEach
	void seed() {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.owner = UUID.randomUUID();
		this.jdbc.update("INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, "
				+ "created_at, updated_at) VALUES (?, '조립', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)", this.owner, now, now);
		this.tripId = UUID.randomUUID();
		this.jdbc.update("INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, pace, "
				+ "time_window_start, time_window_end, created_at, updated_at) "
				+ "VALUES (?, ?, '2026-10-01', '2026-10-08', 4, 'PACKED', '09:00', '21:00', ?, ?)",
				this.tripId, this.owner, now, now);
		this.jdbc.update("INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) "
				+ "VALUES (?, ?, ?, 'OWNER', now())", UUID.randomUUID(), this.tripId, this.owner);

		// 같은 좌표가 매번 나오게 간단한 합동식(LCG — 곱하고 더하고 나누는 난수표)으로 부산 안에 흩는다.
		long seed = 20260925L;
		for (int i = 0; i < PLACES; i++) {
			seed = (seed * 6364136223846793005L + 1442695040888963407L);
			double lat = 35.08 + ((seed >>> 33) % 1000) / 1000.0 * 0.17;
			seed = (seed * 6364136223846793005L + 1442695040888963407L);
			double lng = 128.97 + ((seed >>> 33) % 1000) / 1000.0 * 0.22;
			UUID placeId = new UUID(0xA55EL, i);
			String category = CATEGORIES[i % CATEGORIES.length];
			this.jdbc.update("INSERT INTO place (place_id, name_ko, category, lat, lng, created_at) "
					+ "VALUES (?, ?, ?, ?, ?, now())", placeId, "조립시험 " + i, category, lat, lng);
			String hours = HOURS[i % HOURS.length];
			if (hours != null) {
				this.jdbc.update("""
						INSERT INTO place_feature (place_feature_id, place_id, feature_type, value, evidence_status,
						                           source_type, created_at)
						VALUES (?, ?, 'OPENING_HOURS', ?::jsonb, 'ESTIMATED', 'TEST', now())
						""", UUID.randomUUID(), placeId, hours);
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
	@DisplayName("🔴 같은 입력이면 같은 일정 — 판정을 기억해 쓰기 전과 차례·시각·경고가 한 글자도 안 다르다")
	void theItineraryIsTheSameAsBefore() {
		ItineraryDraft draft = this.draftService.assemble(command());

		assertThat(orderOf(draft)).isEqualTo(BEFORE);
	}

	@Test
	@DisplayName("🔴 DB 읽기가 차례 수를 따라 늘지 않는다 — 하루 (장소 × 칸) 마다 판정 셋까지. 고치기 전에는 5,905번이었다")
	void readsDoNotGrowWithOrderings() {
		Statistics stats = this.entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		stats.clear();

		this.draftService.assemble(command());

		// 8일 × 하루 5곳 × 칸 5개 × 판정 셋(영업시간·브레이크타임·라스트오더) = 600, 장소·축제 기간 조회 몇 번을 더한다.
		assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(8 * 5 * 5 * 3 + 20);
	}

	/** 토요일만 따로 적고 나머지 여섯 요일은 같은 시간으로 채운 영업시간 값. */
	private static String byDay(String weekday, String saturday) {
		return "{\"status\": \"PARSED\", \"closedDays\": [], \"notes\": [], \"raw\": {}, \"byDay\": {"
				+ "\"mon\": " + weekday + ", \"tue\": " + weekday + ", \"wed\": " + weekday + ", \"thu\": " + weekday
				+ ", \"fri\": " + weekday + ", \"sat\": " + saturday + ", \"sun\": " + weekday + "}}";
	}

	private ItineraryDraftCommand command() {
		return new ItineraryDraftCommand(UUID.randomUUID(), this.tripId.toString(), this.owner.toString(), this.planned,
				"model-1", "feature-1", "ontology-1", "policy-1", "dataset-1");
	}

	/** 날짜마다 순서대로의 장소 번호(심은 차례 i) — 같은 입력이면 같은 일정인지 볼 때 쓴다. */
	private static String orderOf(ItineraryDraft draft) {
		return draft.items().stream()
				.collect(Collectors.groupingBy(ItineraryDraft.DraftItem::dayIndex, java.util.TreeMap::new,
						Collectors.mapping((item) -> item.sequence() + ":" + item.placeId().getLeastSignificantBits()
								+ "@" + item.startTime() + item.warningCodes(), Collectors.toList())))
				.toString();
	}
}
