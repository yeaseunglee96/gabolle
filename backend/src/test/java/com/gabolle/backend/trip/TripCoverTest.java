package com.gabolle.backend.trip;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.itinerary.adapter.TripCoverAdapter;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.application.port.TripCoverPort;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;
import com.gabolle.testslice.TripSliceApplication;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * 여행 목록의 표지 — 대표 사진과 첫 방문지 이름 (S15P21E201-1370).
 *
 * <p>진짜 PostgreSQL 을 쓴다. 표지를 고르는 규칙이 통째로 {@code DISTINCT ON} 질의 안에 있어서,
 * 메모리 구현으로 재면 이 기능의 전부인 그 질의를 한 줄도 안 재게 된다.
 *
 * <p>🔴 <b>이 시험이 붙잡는 것 중 제일 중요한 것은 «질의가 몇 번 나가나» 다.</b> 나머지는 틀리면
 * 화면에 바로 보이지만, 줄마다 부르는 방식으로 되돌아간 것은 <b>아무 증상 없이</b> 통과한다 —
 * 표지는 똑같이 나오고 목록만 느려진다. 여행이 몇 개 없는 개발 DB 에서는 그 느려짐조차 안
 * 보인다. 그래서 개수를 직접 센다.
 */
@SpringBootTest(classes = TripSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true",
		// 질의 수를 세려면 켜야 한다. 이 시험 밖으로는 안 나간다.
		"spring.jpa.properties.hibernate.generate_statistics=true"
})
@ExtendWith(PostgresAvailableCondition.class)
@Import(TripCoverTest.CoverAdapterWiring.class)
class TripCoverTest {

	/**
	 * 🔴 {@link TripSliceApplication} 은 일정 모듈을 안 올린다(여행 슬라이스니까). 그래서 표지
	 * 구현을 여기서 손으로 붙인다 — 안 붙이면 {@link TripQueryService} 가
	 * {@link TripCoverPort#NONE} 으로 떨어져서, 이 시험이 <b>전부 통과하면서 아무것도 안 잰다.</b>
	 * 운영에서는 본 애플리케이션이 {@code com.gabolle.backend} 를 통째로 스캔하므로 그냥 붙는다.
	 */
	@TestConfiguration
	static class CoverAdapterWiring {

		@Bean
		TripCoverPort tripCoverPort(EntityManager entityManager) {
			return new TripCoverAdapter(entityManager);
		}
	}

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private TripQueryService queryService;

	@Autowired
	private TripCoverPort coverPort;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	private String ownerId;

	@BeforeEach
	void seedUser() {
		this.ownerId = newUser();
	}

	@Test
	void 표지는_여행이_몇_개든_질의_한_번으로_가져온다() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		List<String> trips = List.of(
				tripWithFirstStop(now, "해운대해수욕장", "Haeundae Beach", "https://example.test/haeundae.jpg"),
				tripWithFirstStop(now, "광안리해수욕장", "Gwangalli Beach", "https://example.test/gwangalli.jpg"),
				tripWithFirstStop(now, "감천문화마을", "Gamcheon Village", null),
				tripWithFirstStop(now, "자갈치시장", "Jagalchi Market", null),
				tripWithFirstStop(now, "태종대", "Taejongdae", "https://example.test/taejongdae.jpg"));

		Statistics stats = statistics();
		stats.clear();

		Map<String, TripCoverPort.Cover> covers = this.coverPort.coversOf(trips);

		assertThat(covers).hasSize(5);
		assertThat(stats.getPrepareStatementCount())
				.as("여행 5개의 표지를 가져오는 데 나간 질의 수 — 줄마다 부르면 여기가 5 이상이 된다")
				.isEqualTo(1);
	}

	@Test
	void 첫_방문지는_날짜와_순서가_가장_앞선_것이다() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = saveTrip(now);
		String itineraryId = addItinerary(tripId, 1, now);
		String versionId = addVersion(itineraryId, 1, now);

		// 일부러 뒤죽박죽 넣는다 — 넣은 차례가 아니라 (day_index, sequence) 가 정한다.
		addItem(versionId, 2, 1, place("둘째날 첫 집", null, null), now);
		addItem(versionId, 1, 2, place("첫날 둘째 집", null, null), now);
		addItem(versionId, 1, 1, place("첫날 첫 집", "Day one first", "https://example.test/first.jpg"), now);

		TripCoverPort.Cover cover = this.coverPort.coversOf(List.of(tripId)).get(tripId);

		assertThat(cover).isNotNull();
		assertThat(cover.stopNameKo()).isEqualTo("첫날 첫 집");
		assertThat(cover.stopNameEn()).isEqualTo("Day one first");
		assertThat(cover.imageUrl()).isEqualTo("https://example.test/first.jpg");
	}

	@Test
	void 일정이_여럿이면_가장_먼저_만든_일정을_쓴다() {
		// 🔴 실서버에 일정을 셋 가진 여행이 실제로 있다(2026-09-21). 여행을 열었을 때
		//    나오는 것은 JpaItineraryRepository.findByTripIdOrderByCreatedAtAsc — 가장 먼저
		//    만든 일정이다. 표지가 다른 것을 고르면 목록과 상세가 어긋난다.
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = saveTrip(now);

		String older = addItinerary(tripId, 1, now.minusSeconds(3600));
		addItem(addVersion(older, 1, now), 1, 1, place("먼저 만든 일정의 첫 집", null, null), now);

		String newer = addItinerary(tripId, 1, now);
		addItem(addVersion(newer, 1, now), 1, 1, place("나중에 만든 일정의 첫 집", null, null), now);

		assertThat(this.coverPort.coversOf(List.of(tripId)).get(tripId).stopNameKo())
				.isEqualTo("먼저 만든 일정의 첫 집");
	}

	@Test
	void 최신_판의_방문지를_쓴다() {
		// 일정을 편집하면 표지도 따라 바뀌어야 한다 — latest_version 이 가리키는 판을 본다.
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = saveTrip(now);
		String itineraryId = addItinerary(tripId, 2, now);

		addItem(addVersion(itineraryId, 1, now), 1, 1, place("옛 판의 첫 집", null, null), now);
		addItem(addVersion(itineraryId, 2, now), 1, 1, place("새 판의 첫 집", null, null), now);

		assertThat(this.coverPort.coversOf(List.of(tripId)).get(tripId).stopNameKo())
				.isEqualTo("새 판의 첫 집");
	}

	@Test
	void 표지가_없는_여행도_목록에서_빠지지_않는다() {
		// 🔴 표지는 장식이다. 사진이 없다고 여행이 목록에서 사라지면 그게 훨씬 큰 고장이고,
		//    실서버에서는 표지 없는 쪽이 오히려 다수다(59건 중 41건만 첫 방문지가 있다).
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String bare = saveTrip(now);
		String emptyItinerary = saveTrip(now);
		addVersion(addItinerary(emptyItinerary, 1, now), 1, now); // 판만 있고 방문지가 없다
		String withCover = tripWithFirstStop(now, "해운대해수욕장", "Haeundae Beach", null);

		List<TripQueryService.Listing> listing = this.queryService.listWithCovers(this.ownerId, 50);

		assertThat(listing).extracting((l) -> l.row().trip().tripId())
				.contains(bare, emptyItinerary, withCover);
		assertThat(listing).filteredOn((l) -> l.row().trip().tripId().equals(bare))
				.singleElement().extracting(TripQueryService.Listing::cover).isNull();
		assertThat(listing).filteredOn((l) -> l.row().trip().tripId().equals(emptyItinerary))
				.singleElement().extracting(TripQueryService.Listing::cover).isNull();
		assertThat(listing).filteredOn((l) -> l.row().trip().tripId().equals(withCover))
				.singleElement().extracting((l) -> l.cover().stopNameKo()).isEqualTo("해운대해수욕장");
	}

	@Test
	void 앞쪽_어디에도_사진이_없으면_이름만_준다() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = tripWithFirstStop(now, "동백섬횟집", null, null);

		TripCoverPort.Cover cover = this.coverPort.coversOf(List.of(tripId)).get(tripId);

		assertThat(cover.stopNameKo()).isEqualTo("동백섬횟집");
		assertThat(cover.imageUrl()).isNull();
		assertThat(cover.stopNameEn()).isNull();
	}

	// ── 사진은 앞쪽을 훑어 찾는다 (S15P21E201-1436) ─────────────────────────

	@Test
	void 사진은_앞쪽_정차지를_훑어_찾고_이름은_첫_정차지_그대로다() {
		// 🔴 첫 정차지는 식당·카페인 경우가 많고 그런 곳은 관광공사 사진이 없다. 실서버에서
		//    첫 정차지가 정해진 여행 41건 중 사진이 있는 것은 10건뿐이었다(2026-09-21). 화면은 이미
		//    앞 여섯 곳을 훑고 있었다 — 서버가 첫 곳만 보면 화면이 갈아탈 때 사진이 오히려 줄어든다.
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = saveTrip(now);
		String versionId = addVersion(addItinerary(tripId, 1, now), 1, now);
		addItem(versionId, 1, 1, place("무슈뱅상", null, null), now);            // 첫 곳 — 사진 없음
		addItem(versionId, 1, 2, place("제로베이스", null, null), now);
		addItem(versionId, 1, 3, place("광안리해수욕장", null, "https://example.test/gwangalli.jpg"), now);
		addItem(versionId, 1, 4, place("해운대해수욕장", null, "https://example.test/haeundae.jpg"), now);

		TripCoverPort.Cover cover = this.coverPort.coversOf(List.of(tripId)).get(tripId);

		assertThat(cover.imageUrl()).isEqualTo("https://example.test/gwangalli.jpg");
		assertThat(cover.stopNameKo()).as("이름은 사진을 고른 곳이 아니라 «첫 정차지»다").isEqualTo("무슈뱅상");
	}

	@Test
	void 너무_뒤쪽_사진은_쓰지_않는다() {
		// 앞 여섯 곳까지만 본다 — 더 멀리 가면 「첫 방문지 근처」라기 어려운 곳의 사진이 표지가 된다.
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = saveTrip(now);
		String versionId = addVersion(addItinerary(tripId, 1, now), 1, now);
		for (int sequence = 1; sequence <= 6; sequence += 1) {
			addItem(versionId, 1, sequence, place("사진 없는 곳 " + sequence, null, null), now);
		}
		addItem(versionId, 2, 1, place("일곱째 곳", null, "https://example.test/too-far.jpg"), now);

		TripCoverPort.Cover cover = this.coverPort.coversOf(List.of(tripId)).get(tripId);

		assertThat(cover.imageUrl()).isNull();
		assertThat(cover.stopNameKo()).isEqualTo("사진 없는 곳 1");
	}

	@Test
	void 훑기는_첫_일정_안에서만_한다() {
		// 🔴 나중에 만든 일정의 사진을 끌어오면 목록의 표지와 열어 본 화면이 어긋난다.
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = saveTrip(now);

		String older = addItinerary(tripId, 1, now.minusSeconds(3600));
		addItem(addVersion(older, 1, now), 1, 1, place("먼저 만든 일정의 첫 집", null, null), now);

		String newer = addItinerary(tripId, 1, now);
		addItem(addVersion(newer, 1, now), 1, 1, place("나중 일정", null, "https://example.test/newer.jpg"), now);

		TripCoverPort.Cover cover = this.coverPort.coversOf(List.of(tripId)).get(tripId);

		assertThat(cover.stopNameKo()).isEqualTo("먼저 만든 일정의 첫 집");
		assertThat(cover.imageUrl()).as("나중 일정의 사진을 끌어오면 안 된다").isNull();
	}

	@Test
	void 앞쪽을_훑어도_질의는_한_번이다() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		List<String> trips = List.of(
				tripWithFirstStop(now, "해운대해수욕장", null, "https://example.test/a.jpg"),
				tripWithFirstStop(now, "광안리해수욕장", null, null),
				tripWithFirstStop(now, "감천문화마을", null, "https://example.test/c.jpg"));

		Statistics stats = statistics();
		stats.clear();

		this.coverPort.coversOf(trips);

		assertThat(stats.getPrepareStatementCount())
				.as("앞쪽을 훑느라 여행마다 따로 부르면 여기가 3 이상이 된다")
				.isEqualTo(1);
	}

	@Test
	void 빈_목록을_물으면_다녀오지_않는다() {
		Statistics stats = statistics();
		stats.clear();

		assertThat(this.coverPort.coversOf(List.of())).isEmpty();
		assertThat(stats.getPrepareStatementCount()).isZero();
	}

	@Test
	void 식별자_모양이_아닌_것이_섞여도_나머지는_나온다() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = tripWithFirstStop(now, "자갈치시장", null, null);

		Map<String, TripCoverPort.Cover> covers = this.coverPort.coversOf(List.of("trp_모양아님", tripId));

		assertThat(covers).containsOnlyKeys(tripId);
	}

	// ── 시드 ────────────────────────────────────────────────────────────────

	private Statistics statistics() {
		return this.entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
	}

	private String tripWithFirstStop(Instant at, String nameKo, String nameEn, String photoUrl) {
		String tripId = saveTrip(at);
		String versionId = addVersion(addItinerary(tripId, 1, at), 1, at);
		addItem(versionId, 1, 1, place(nameKo, nameEn, photoUrl), at);
		return tripId;
	}

	private String newUser() {
		String id = UUID.randomUUID().toString();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbcTemplate.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				UUID.fromString(id), now, now);
		return id;
	}

	private String saveTrip(Instant at) {
		String tripId = UUID.randomUUID().toString();
		Trip trip = new Trip(tripId, this.ownerId,
				LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8),
				35.1587, 129.1604, 300000, 2,
				"MORNING_TO_EVENING", "Asia/Seoul", at);
		TripMember owner = TripMember.owner(UUID.randomUUID().toString(), tripId, this.ownerId, at);
		PreferenceSnapshot snapshot = new PreferenceSnapshot(
				UUID.randomUUID().toString(), tripId, 1, List.of(), PersonalizationScope.TRIP, List.of(), at);
		this.tripRepository.save(trip, List.of(), owner, snapshot);
		return tripId;
	}

	private String addItinerary(String tripId, int latestVersion, Instant at) {
		UUID id = UUID.randomUUID();
		this.jdbcTemplate.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, ?, ?)",
				id, UUID.fromString(tripId), latestVersion, at.atOffset(ZoneOffset.UTC));
		return id.toString();
	}

	private String addVersion(String itineraryId, int version, Instant at) {
		UUID id = UUID.randomUUID();
		this.jdbcTemplate.update(
				"INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, operation, created_by, "
						+ "request_id, created_at) VALUES (?, ?, ?, 'CREATE', ?, ?, ?)",
				id, UUID.fromString(itineraryId), version, UUID.fromString(this.ownerId),
				"req_" + id, at.atOffset(ZoneOffset.UTC));
		return id.toString();
	}

	private UUID place(String nameKo, String nameEn, String photoUrl) {
		UUID id = UUID.randomUUID();
		this.jdbcTemplate.update(
				"INSERT INTO place (place_id, name_ko, name_en, photo_url, created_at) VALUES (?, ?, ?, ?, ?)",
				id, nameKo, nameEn, photoUrl, OffsetDateTime.now(ZoneOffset.UTC));
		return id;
	}

	private void addItem(String versionId, int dayIndex, int sequence, UUID placeId, Instant at) {
		this.jdbcTemplate.update(
				"INSERT INTO itinerary_item (itinerary_item_id, itinerary_version_id, item_key, day_index, "
						+ "visit_date, sequence, place_id, data_status, created_at) "
						+ "VALUES (?, ?, ?, ?, ?, ?, ?, 'ESTIMATED', ?)",
				UUID.randomUUID(), UUID.fromString(versionId), UUID.randomUUID(), dayIndex,
				LocalDate.of(2026, 9, 5).plusDays(dayIndex), sequence, placeId, at.atOffset(ZoneOffset.UTC));
	}

	// ── 지금 확정된 일정 (S15P21E201-1602) ─────────────────────────────

	private void chooseAt(String itineraryId, Instant at) {
		this.jdbcTemplate.update("UPDATE itineraries SET chosen_at = ? WHERE itinerary_id = ?",
				at.atOffset(ZoneOffset.UTC), UUID.fromString(itineraryId));
	}

	@Test
	void 안_골랐으면_추천이_만든_일정이_확정이다() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = saveTrip(now);
		String a = addItinerary(tripId, 1, now);

		assertThat(this.coverPort.currentItinerariesOf(List.of(tripId))).containsEntry(tripId, a);
	}

	@Test
	void C를_고르면_C가_확정이다() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = saveTrip(now);
		String a = addItinerary(tripId, 1, now);
		String c = addItinerary(tripId, 1, now.plusSeconds(60));
		chooseAt(a, now);
		chooseAt(c, now.plusSeconds(60));

		assertThat(this.coverPort.currentItinerariesOf(List.of(tripId))).containsEntry(tripId, c);
	}

	/** 「가장 나중에 만든 일정」이 아니다 — 만든 차례로 고르면 여기서 C 가 나온다. */
	@Test
	void C를_골랐다가_A를_다시_고르면_A가_확정이다() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = saveTrip(now);
		String a = addItinerary(tripId, 1, now);
		String c = addItinerary(tripId, 1, now.plusSeconds(60));
		chooseAt(a, now);
		chooseAt(c, now.plusSeconds(60));
		chooseAt(a, now.plusSeconds(120));

		assertThat(this.coverPort.currentItinerariesOf(List.of(tripId))).containsEntry(tripId, a);
	}

	@Test
	void 일정이_없으면_확정도_없다_여행_여럿도_질의_한_번이다() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String empty = saveTrip(now);
		String withOne = saveTrip(now);
		String itinerary = addItinerary(withOne, 1, now);

		Statistics stats = statistics();
		stats.clear();
		Map<String, String> current = this.coverPort.currentItinerariesOf(List.of(empty, withOne));

		assertThat(current).doesNotContainKey(empty).containsEntry(withOne, itinerary);
		assertThat(stats.getPrepareStatementCount()).as("여행마다 묻지 않는다").isEqualTo(1);
	}

	@Test
	void 여행_목록_한_줄에_확정_일정이_실린다() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = saveTrip(now);
		String a = addItinerary(tripId, 1, now);

		assertThat(this.queryService.listWithCovers(this.ownerId, 10))
				.filteredOn((listing) -> listing.row().trip().tripId().equals(tripId))
				.singleElement()
				.extracting(TripQueryService.Listing::currentItineraryId)
				.isEqualTo(a);
	}
}
