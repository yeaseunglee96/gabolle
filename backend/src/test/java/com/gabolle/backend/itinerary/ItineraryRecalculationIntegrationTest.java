package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryExclusion;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryWarningCodes;
import com.gabolle.backend.recommendation.application.RecommendationCodes;
import com.gabolle.backend.recommendation.application.RecommendationCommand;
import com.gabolle.backend.recommendation.application.JobProgressBroker;
import com.gabolle.backend.recommendation.application.JobProgressReporter;
import com.gabolle.backend.recommendation.application.RecommendationJobWorker;
import com.gabolle.backend.recommendation.application.RecommendationRecorder;
import com.gabolle.backend.recommendation.application.RecommendationService;
import com.gabolle.backend.recommendation.domain.JobStatus;
import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;
import com.gabolle.backend.recommendation.support.FakeRecommendationEngine;
import com.gabolle.backend.recommendation.support.PersonalizationFixture;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.ItinerarySliceApplication;

/**
 * S15P21E201-249 — 제외·그날 재계산이 실제 PostgreSQL 에서 한 바퀴 도는지 본다.
 *
 * <p>추천 슬라이스가 아니라 <b>일정 슬라이스</b>에서 돈다 — 진짜 {@code ItineraryDraftService.revise/
 * publish} 와 진짜 {@code JpaItineraryRepository.appendVersion}(CAS) 이 붙어야 이 티켓의 요구사항
 * (FR-ITN-09 게시 조건, FR-REC-09 부분 반영 금지, 요구사항 3.2 "후보 0건이면 비운다")을 확인할 수
 * 있기 때문이다. 엔진만 가짜다 — 무엇을 추천했는지는 이 테스트의 관심이 아니고, 추천 결과를
 * 어느 자리에 어떻게 앉히는지가 관심이다.
 *
 * <p>Job 은 {@link RecommendationJobWorker#execute} 를 <b>직접 new 한 인스턴스로</b> 동기 호출한다.
 * 빈으로 받으면 {@code @Async} 프록시를 지나 다른 스레드로 가고, 테스트가 끝나기를 기다릴 방법이
 * 없다. 운영 경로에서 Worker 가 하는 일(RUNNING 표시 → continueJob → 예외별 FAILED 기록)은
 * 그대로 지난다.
 */
@SpringBootTest(classes = ItinerarySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true",
		"gabolle.recommendation.service-version=test-service-0.0.1",
		"gabolle.recommendation.deployment-environment=test"
})
// 🔴 classes= 로 앱을 명시하면 중첩 @TestConfiguration 이 자동으로 잡히지 않는다 — 명시적으로 끌어온다.
@Import(ItineraryRecalculationIntegrationTest.FakeEngineOverride.class)
@ExtendWith(PostgresAvailableCondition.class)
class ItineraryRecalculationIntegrationTest {

	/**
	 * 🔴 일정 슬라이스에는 {@code BaselineRecommendationEngine} 이 진짜로 올라온다({@code place}
	 * 패키지를 스캔하므로). 그 옆에 가짜를 {@code @Primary} 로 세우면 {@code RecommendationService}
	 * 의 {@code ObjectProvider.getIfAvailable()} 이 가짜를 고른다 — 진짜 엔진은 여기 없는
	 * {@code place_feature}·코드맵 데이터를 요구해서 이 테스트의 관심 밖 이유로 실패한다.
	 */
	@TestConfiguration(proxyBeanMethods = false)
	static class FakeEngineOverride {

		@Bean
		@Primary
		FakeRecommendationEngine fakeRecommendationEngine() {
			return new FakeRecommendationEngine();
		}
	}

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private RecommendationService recommendationService;

	@Autowired
	private RecommendationRecorder recorder;

	@Autowired
	private RecommendationJobRepository jobRepository;

	@Autowired
	private ItineraryRepository itineraryRepository;

	@Autowired
	private FakeRecommendationEngine engine;

	@Autowired
	private Clock clock;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID userId;
	private UUID tripId;
	private UUID preferenceSnapshotId;
	private UUID constraintSnapshotId;
	private UUID itineraryId;

	// 1일차: A(1) B(2, 고정) C(3) · 2일차: D(1). E·F·G 는 아직 어디에도 없는 후보.
	private UUID placeA, placeB, placeC, placeD, placeE, placeF, placeG;
	private String keyA, keyB, keyC, keyD;

	@BeforeEach
	void seed() {
		this.engine.reset();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

		this.userId = PersonalizationFixture.insertUser(this.jdbc);
		this.tripId = UUID.randomUUID();
		// 🔴 활동 시간대(09:00-18:00)를 준다 — 그래야 slotFor 가 시각을 배정하고 "고정 항목의 시각이
		//    움직였다"(RECALC_TIMES_RESHUFFLED)를 확인할 수 있다.
		this.jdbc.update("""
				INSERT INTO trip (trip_id, owner_user_id, version, start_date, end_date, party_size,
				    travel_modes, time_window_start, time_window_end, created_at, updated_at)
				VALUES (?, ?, 1, '2026-09-10', '2026-09-12', 1, ARRAY['WALK']::VARCHAR(30)[], '09:00', '18:00', ?, ?)
				""", this.tripId, this.userId, now, now);
		this.jdbc.update(
				"INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, 'OWNER', ?)",
				UUID.randomUUID(), this.tripId, this.userId, now);
		this.preferenceSnapshotId = PersonalizationFixture.insertPreferenceSnapshot(this.jdbc, this.userId, this.tripId);
		this.constraintSnapshotId = PersonalizationFixture.insertConstraintSnapshot(this.jdbc, this.userId, this.tripId);

		this.placeA = insertPlace("A 해운대해수욕장");
		this.placeB = insertPlace("B 동백섬");
		this.placeC = insertPlace("C 달맞이길");
		this.placeD = insertPlace("D 광안리해변");
		this.placeE = insertPlace("E 감천문화마을");
		this.placeF = insertPlace("F 자갈치시장");
		this.placeG = insertPlace("G 태종대");

		this.itineraryId = UUID.randomUUID();
		UUID versionId = UUID.randomUUID();
		this.jdbc.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, ?)",
				this.itineraryId, this.tripId, now);
		this.jdbc.update(
				"INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, base_version, operation, "
						+ "created_by, request_id, created_at) VALUES (?, ?, 1, NULL, 'CREATE', ?, 'req_seed', ?)",
				versionId, this.itineraryId, this.userId, now);

		this.keyA = insertItem(versionId, 0, "2026-09-10", 1, this.placeA, false, "09:00", "12:00");
		this.keyB = insertItem(versionId, 0, "2026-09-10", 2, this.placeB, true, "12:00", "15:00");
		this.keyC = insertItem(versionId, 0, "2026-09-10", 3, this.placeC, false, "15:00", "18:00");
		this.keyD = insertItem(versionId, 1, "2026-09-11", 1, this.placeD, false, "09:00", "18:00");
	}

	private UUID insertPlace(String name) {
		UUID placeId = UUID.randomUUID();
		this.jdbc.update("INSERT INTO place (place_id, name_ko, lat, lng, created_at) VALUES (?, ?, 35.15, 129.05, now())",
				placeId, name);
		return placeId;
	}

	private String insertItem(UUID versionId, int dayIndex, String visitDate, int sequence, UUID placeId,
			boolean locked, String start, String end) {
		String itemKey = UUID.randomUUID().toString();
		this.jdbc.update(
				"INSERT INTO itinerary_item (itinerary_item_id, itinerary_version_id, item_key, day_index, visit_date, "
						+ "sequence, place_id, start_time, end_time, stay_minutes, locked, data_status, created_at) "
						+ "VALUES (?, ?, ?::uuid, ?, ?::date, ?, ?, ?::time, ?::time, 180, ?, 'ESTIMATED', now())",
				UUID.randomUUID(), versionId, itemKey, dayIndex, visitDate, sequence, placeId, start, end, locked);
		return itemKey;
	}

	private RecommendationCommand removeCommand(int baseVersion, String itemKey) {
		// ItineraryRecalculationService 가 만드는 모양과 같다 — 뺀 장소(A)를 newlyExcludedPlaceIds 에도 싣는다.
		// dayIndex 는 안 준다 — URL 에는 itemId 만 오고, 어느 날인지는 판에서 찾는다.
		return new RecommendationCommand(this.userId, JobType.ITEM_REMOVE, this.tripId, 1,
				this.preferenceSnapshotId, this.constraintSnapshotId, this.itineraryId, null, baseVersion,
				"test-app", 10, new RecommendationCommand.ItineraryEdit(null, itemKey, List.of(this.placeA), "별로였다"));
	}

	private RecommendationCommand recalculateCommand(int baseVersion, int dayIndex) {
		return new RecommendationCommand(this.userId, JobType.ITINERARY_RECALCULATE, this.tripId, 1,
				this.preferenceSnapshotId, this.constraintSnapshotId, this.itineraryId, null, baseVersion,
				"test-app", 10, new RecommendationCommand.ItineraryEdit(dayIndex, null, List.of(), null));
	}

	/** 운영의 {@code RecommendationJobRunner.enqueue} 와 같은 순서 — 만들고, PENDING 으로 저장하고, 실행. 다만 동기다. */
	private RecommendationJob runSynchronously(RecommendationCommand command) {
		RecommendationJob job = this.recommendationService.prepare(command);
		this.jobRepository.save(job);
		new RecommendationJobWorker(this.jobRepository, this.recommendationService, this.recorder, this.clock,
				// 진행률 보고(S15P21E201-193). 이 검사는 진행률을 보지 않지만 워커가 요구하므로
				// 진짜 객체를 준다 — 보는 연결이 하나도 없으면 알림은 그냥 버려진다.
				new JobProgressReporter(this.jobRepository, new JobProgressBroker()))
				.execute(job, command);
		return this.jobRepository.findById(job.getJobId()).orElseThrow();
	}

	private ItineraryContent content(int version) {
		return this.itineraryRepository.findContent(this.itineraryId.toString(), version).orElseThrow();
	}

	private static List<ItineraryItem> day(ItineraryContent content, int dayIndex) {
		return content.items().stream().filter((i) -> i.dayIndex() == dayIndex).sorted(
				java.util.Comparator.comparingInt(ItineraryItem::sequence)).toList();
	}

	private int latestVersionInDatabase() {
		return this.jdbc.queryForObject("SELECT latest_version FROM itineraries WHERE itinerary_id = ?", Integer.class,
				this.itineraryId);
	}

	/**
	 * 🔴 이 파일의 존재 이유. 엔진이 뺀 장소(A)를 <b>1순위로</b> 다시 내놓아도 새 판에 A 는 없어야 한다.
	 *
	 * <p>부수기: {@code ItineraryDraftService.revise} 에서 제외 목록을 {@code unavailable} 에 넣는
	 * 반복문을 지우면 A 가 첫 빈자리에 앉아 여기서 빨개진다. {@code ItineraryRevision.copyExclusions}
	 * 를 비우면 두 번째 재계산({@link #recalculateAgainStillHonoursEarlierExclusion})에서 빨개진다.
	 */
	@Test
	@DisplayName("🔴 항목을 빼면 새 판에서 그 장소가 사라지고, 엔진이 다시 내놓아도 돌아오지 않는다")
	void removeExcludesPlaceEvenWhenEngineRanksItFirst() {
		this.engine.willReturn(FakeRecommendationEngine.batchOf(List.of(
				FakeRecommendationEngine.passing(this.placeA, 0.99),
				FakeRecommendationEngine.passing(this.placeE, 0.90),
				FakeRecommendationEngine.passing(this.placeF, 0.80))));

		RecommendationJob job = runSynchronously(removeCommand(1, this.keyA));

		assertThat(job.getJobStatus()).as("errorCode=%s stage=%s", job.getErrorCode(), job.getFailureStage())
				.isEqualTo(JobStatus.SUCCEEDED);
		assertThat(job.getItineraryVersion()).isEqualTo(2);
		assertThat(latestVersionInDatabase()).isEqualTo(2);

		ItineraryContent v2 = content(2);
		List<ItineraryItem> day0 = day(v2, 0);
		// 남긴 B·C 가 원래 순서로 앞에, 채운 E 가 뒤에. 하루 크기(3)는 그대로다.
		assertThat(day0).extracting(ItineraryItem::placeId)
				.containsExactly(this.placeB.toString(), this.placeC.toString(), this.placeE.toString());
		assertThat(day0).extracting(ItineraryItem::itemKey).startsWith(this.keyB, this.keyC);
		assertThat(day0.get(0).locked()).isTrue();
		assertThat(day0).extracting(ItineraryItem::sequence).containsExactly(1, 2, 3);
		// 시각은 3칸으로 다시 나눴다 — 고정한 B 가 12:00 에서 09:00 으로 옮겨졌고, 그 사실이 판 경고로 남는다.
		assertThat(day0.get(0).startTime()).isEqualTo(LocalTime.of(9, 0));
		assertThat(v2.version().warningCodes()).containsExactly(ItineraryWarningCodes.RECALC_TIMES_RESHUFFLED);

		// 다른 날은 손대지 않았다 — item_key 까지 그대로.
		assertThat(day(v2, 1)).extracting(ItineraryItem::itemKey).containsExactly(this.keyD);
		// 제외 목록에 A 가 남았다 — 누가 왜 뺐는지와 함께.
		assertThat(v2.exclusions()).extracting(ItineraryExclusion::placeId).containsExactly(this.placeA.toString());
		assertThat(v2.exclusions().get(0).reasonCode()).isEqualTo(ItineraryExclusion.REASON_USER_REMOVED);
		assertThat(v2.exclusions().get(0).excludedBy()).isEqualTo(this.userId.toString());
		assertThat(v2.exclusions().get(0).operationalReason()).isEqualTo("별로였다");
		// 바탕 판은 그대로다 — 덮어쓰기 금지(FR-ITN-08).
		assertThat(day(content(1), 0)).extracting(ItineraryItem::placeId)
				.containsExactly(this.placeA.toString(), this.placeB.toString(), this.placeC.toString());
	}

	/**
	 * 🔴 S15P21E201-1080 — 뺀 장소를 행동 신호로 남긴다.
	 *
	 * <p>이 이벤트가 없어서 취향 벡터에 행동이 한 건도 안 들어가고 있었다. {@code event_outbox}
	 * 가 통째로 비어 있었고, 접기 배치는 늘 {@code rebuilt=0} 이었다.
	 *
	 * <p>🔴 <b>요청이 아니라 반영을 적는다.</b> 이 이벤트는 {@code removeItem} 이 Job 을 만들 때가
	 * 아니라 제외가 실제로 새 판에 들어가는 <b>recorder 트랜잭션</b>에서 난다. 일정이 그대로인데
	 * 「이 장소를 거부했다」가 남으면 랭커는 일어나지 않은 일을 배운다.
	 */
	@Test
	@DisplayName("🔴 항목을 빼면 itinerary_remove 가 남는다 — 벡터가 셀 행동 신호")
	void removeRecordsBehaviorSignal() {
		enableBehaviorPersonalization();
		this.engine.willReturn(FakeRecommendationEngine.batchOf(List.of(
				FakeRecommendationEngine.passing(this.placeE, 0.90))));

		RecommendationJob job = runSynchronously(removeCommand(1, this.keyA));
		assertThat(job.getJobStatus()).isEqualTo(JobStatus.SUCCEEDED);

		// 🔴 이 여행의 것만 센다. 표 전체를 세면 같은 종류를 쓰는 다른 검사가 생기는 순간
		//    이 검사가 그 검사 때문에 빨개진다 — 원인이 여기 있는 것처럼 보이면서.
		List<java.util.Map<String, Object>> rows = this.jdbc.queryForList(
				"SELECT aggregate_type, aggregate_id, user_id, trip_id, payload::text AS payload "
						+ "FROM event_outbox WHERE event_type = ? AND trip_id = ?",
				"itinerary_remove", this.tripId);

		assertThat(rows).as("뺀 장소가 이벤트로 남아야 한다").hasSize(1);
		assertThat(rows.get(0).get("aggregate_type")).isEqualTo("trip");
		assertThat(rows.get(0).get("aggregate_id")).hasToString(this.tripId.toString());
		assertThat(rows.get(0).get("user_id")).hasToString(this.userId.toString());
		// 어느 장소를 뺐는지가 payload 에 있어야 접기가 셀 수 있다.
		assertThat((String) rows.get(0).get("payload")).contains(this.placeA.toString());
	}

	/**
	 * 🔴 개인화를 끈 사람은 안 남긴다 — S15P21E201-549 의 규칙이 이 경로에도 걸리는지.
	 *
	 * <p>이 경로는 {@code RecommendationRecorder} 를 지나 {@code OutboxService} 를 직접 부르므로
	 * {@code EventIngestService} 안의 동의 검사를 <b>안 지난다.</b> 그래서 명령을 조립하는 자리에서
	 * 따로 거른다. 우회로 자체를 막는 것은 S15P21E201-1096 이다.
	 *
	 * <p>🔴 이 검사가 {@code PersonalizationFixture} 의 기본값({@code EXPLICIT_ONLY})을 그대로
	 * 쓰는 것이 중요하다 — 위 검사가 일부러 켠 것과 짝이다.
	 */
	@Test
	@DisplayName("🔴 행동 개인화를 끈 사람은 항목을 빼도 itinerary_remove 가 안 남는다")
	void removeRecordsNothingWhenBehaviorPersonalizationIsOff() {
		this.engine.willReturn(FakeRecommendationEngine.batchOf(List.of(
				FakeRecommendationEngine.passing(this.placeE, 0.90))));

		RecommendationJob job = runSynchronously(removeCommand(1, this.keyA));
		assertThat(job.getJobStatus()).isEqualTo(JobStatus.SUCCEEDED);

		// 🔴 일정은 정상으로 바뀌어야 한다. 「안 적는다」가 「동작을 막는다」가 되면 안 된다.
		assertThat(content(2).exclusions()).extracting(ItineraryExclusion::placeId)
				.containsExactly(this.placeA.toString());

		Integer count = this.jdbc.queryForObject(
				"SELECT count(*) FROM event_outbox WHERE event_type = ? AND trip_id = ?",
				Integer.class, "itinerary_remove", this.tripId);
		assertThat(count).as("껐는데 남으면 동의를 어긴 것이다").isZero();
	}

	/** 기본 fixture 는 EXPLICIT_ONLY(행동 개인화 OFF)로 사람을 넣는다. 켜야 하는 검사만 이것을 부른다. */
	private void enableBehaviorPersonalization() {
		this.jdbc.update("UPDATE app_user SET personalization_mode = ? WHERE user_id = ?",
				"BEHAVIOR_ENABLED", this.userId);
	}

	/**
	 * 제외 목록은 판에 매달려 복사된다 — 별도 조회 없이 "몇 번을 재계산해도 다시 안 나온다" 가 성립한다.
	 * 재계산은 고정 항목(B)만 남기고 나머지를 비운 뒤 채운다.
	 */
	@Test
	@DisplayName("🔴 두 번째 재계산도 앞서 뺀 장소를 다시 넣지 않고, 고정 항목만 남긴다")
	void recalculateAgainStillHonoursEarlierExclusion() {
		this.engine.willReturn(FakeRecommendationEngine.batchOf(List.of(
				FakeRecommendationEngine.passing(this.placeE, 0.90))));
		assertThat(runSynchronously(removeCommand(1, this.keyA)).getJobStatus()).isEqualTo(JobStatus.SUCCEEDED);

		this.engine.willReturn(FakeRecommendationEngine.batchOf(List.of(
				FakeRecommendationEngine.passing(this.placeA, 0.99),
				FakeRecommendationEngine.passing(this.placeF, 0.80),
				FakeRecommendationEngine.passing(this.placeG, 0.70),
				FakeRecommendationEngine.passing(this.placeD, 0.60))));
		RecommendationJob job = runSynchronously(recalculateCommand(2, 0));

		assertThat(job.getJobStatus()).as("errorCode=%s", job.getErrorCode()).isEqualTo(JobStatus.SUCCEEDED);
		ItineraryContent v3 = content(3);
		List<ItineraryItem> day0 = day(v3, 0);
		// B(고정)만 남고, A 는 제외라서, D 는 2일차에 이미 있어서 건너뛴다. F·G 로 3자리를 채운다.
		assertThat(day0).extracting(ItineraryItem::placeId)
				.containsExactly(this.placeB.toString(), this.placeF.toString(), this.placeG.toString());
		assertThat(day0.get(0).itemKey()).isEqualTo(this.keyB);
		assertThat(v3.exclusions()).extracting(ItineraryExclusion::placeId).containsExactly(this.placeA.toString());
		assertThat(v3.version().warningCodes()).isEmpty();
	}

	/**
	 * 🔴 FR-ITN-09 — 계산이 끝났을 때 최신 판이 바탕 판과 다르면 결과를 버린다. FR-REC-09 — 실패하면
	 * 이전 판이 그대로 최신이고 부분 반영은 없다.
	 *
	 * <p>부수기: {@code JpaItineraryRepository.appendVersion} 의 포인터 조건부 UPDATE 에서
	 * {@code AND latest_version = ?} 을 빼면 낡은 결과가 3판으로 게시돼 여기서 빨개진다.
	 */
	@Test
	@DisplayName("🔴 계산 중 다른 편집이 판을 올렸으면 결과를 버리고 ITINERARY_VERSION_CONFLICT 로 실패한다")
	void staleBaseVersionIsDiscardedNotPublished() {
		this.engine.willReturn(FakeRecommendationEngine.batchOf(List.of(
				FakeRecommendationEngine.passing(this.placeE, 0.90))));
		RecommendationCommand command = removeCommand(1, this.keyA);
		RecommendationJob job = this.recommendationService.prepare(command);
		this.jobRepository.save(job);

		// 그 사이 누가 고정을 눌러 2판이 생겼다.
		this.jdbc.update(
				"INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, base_version, operation, "
						+ "created_by, request_id, created_at) VALUES (?, ?, 2, 1, 'LOCK_ITEM', ?, 'req_other', now())",
				UUID.randomUUID(), this.itineraryId, this.userId);
		this.jdbc.update("UPDATE itineraries SET latest_version = 2 WHERE itinerary_id = ?", this.itineraryId);

		new RecommendationJobWorker(this.jobRepository, this.recommendationService, this.recorder, this.clock,
				// 진행률 보고(S15P21E201-193). 이 검사는 진행률을 보지 않지만 워커가 요구하므로
				// 진짜 객체를 준다 — 보는 연결이 하나도 없으면 알림은 그냥 버려진다.
				new JobProgressReporter(this.jobRepository, new JobProgressBroker()))
				.execute(job, command);

		RecommendationJob saved = this.jobRepository.findById(job.getJobId()).orElseThrow();
		assertThat(saved.getJobStatus()).isEqualTo(JobStatus.FAILED);
		assertThat(saved.getErrorCode()).isEqualTo(RecommendationCodes.ERROR_ITINERARY_VERSION_CONFLICT);
		assertThat(saved.isRetryable()).isTrue();
		// 3판은 없다. 항목도, 제외도, 후보도 남지 않았다 — 한 트랜잭션이 통째로 되돌려졐다.
		assertThat(latestVersionInDatabase()).isEqualTo(2);
		assertThat(this.itineraryRepository.findVersion(this.itineraryId.toString(), 3)).isEmpty();
		Integer candidateRows = this.jdbc.queryForObject(
				"SELECT count(*) FROM recommendation_candidate WHERE request_id = ?", Integer.class, job.getRequestId());
		assertThat(candidateRows).isZero();
	}

	/**
	 * 요구사항 3.2 — 대체 후보가 0건이면 조건을 완화하지 않고 그 자리를 비운다. 편집 Job 은 그래도
	 * <b>성공</b>이다 — 빈 자리가 정답이다. 사실은 판 경고로 남는다.
	 *
	 * <p>부수기: {@code RecommendationService.continueJob} 의 {@code && !editJob} 을 빼면 Job 이
	 * NO_FEASIBLE_RESULT 로 실패해 여기서 빨개진다.
	 */
	@Test
	@DisplayName("후보가 0건이면 그 자리를 비운 채 성공하고 RECALC_NO_CANDIDATE 를 판에 남긴다")
	void zeroCandidatesLeavesSlotEmptyAndSucceeds() {
		this.engine.willReturn(FakeRecommendationEngine.emptyBatch());

		RecommendationJob job = runSynchronously(removeCommand(1, this.keyA));

		assertThat(job.getJobStatus()).as("errorCode=%s", job.getErrorCode()).isEqualTo(JobStatus.SUCCEEDED);
		ItineraryContent v2 = content(2);
		assertThat(day(v2, 0)).extracting(ItineraryItem::placeId)
				.containsExactly(this.placeB.toString(), this.placeC.toString());
		assertThat(v2.version().warningCodes()).contains(ItineraryWarningCodes.RECALC_NO_CANDIDATE);
		assertThat(v2.exclusions()).extracting(ItineraryExclusion::placeId).containsExactly(this.placeA.toString());
	}
}
