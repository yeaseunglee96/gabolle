package com.gabolle.backend.functional;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.functional.support.AuthedClient;
import com.gabolle.backend.functional.support.FunctionalJourneyTest;
import com.gabolle.backend.place.loader.ResearchQueueReader;
import com.gabolle.backend.place.loader.SbizPlaceLoader;
import com.jayway.jsonpath.JsonPath;

/**
 * 진짜 장소를 넣고 추천을 한 번 돌려 본다 — S15P21E201-804.
 *
 * <p>{@code place} 표가 비어 있는 동안 추천은 <b>매 요청 후보 0건</b>이었고, 그래서 점수 계산도
 * 일정 조립도 이동시간도 전부 만들어져 있는데 아무도 실제 결과를 본 적이 없다. 조사한 2,355곳을
 * 넣고 나서 <b>정말로 결과가 나오는가</b> 를 확인하는 것이 이 검사다.
 *
 * <h2>표본 200곳 자급</h2>
 * 표가 비어 있으면 이 검사가 직접 채운다. 실제 대기열에서 뜬 표본 200줄이
 * {@code src/test/resources/research/queue-sample.ndjson} 에 상주하고, 적재 경로는 운영과
 * 같은 것({@link SbizPlaceLoader#saveChunk})을 쓴다.
 *
 * <p>건너뛰지 않는 이유는 이 저장소의 규칙이다 — 검사가 하나라도 건너뛰면 CI 가 빨개진다.
 * 그리고 조건부로 건너뛰는 검사는 아무도 안 보게 된다.
 *
 * <p>전체 2,355곳으로 재고 싶으면 아래로 적재한 뒤 돌린다.
 *
 * <pre>
 * git show origin/bigData/dev:bigData/research/data/queue.ndjson &gt; /tmp/queue.ndjson
 * java -jar build/libs/gabolle-backend-*.jar --spring.profiles.active=dev  *   --gabolle.place.loader.research-queue=/tmp/queue.ndjson  *   --gabolle.place.loader.dataset-version=research-busan-2355-202609
 * </pre>
 *
 */
// 배포에서는 Jenkins 가 -e 로 넣어 주는 값이라 검사 환경에는 비어 있다. 비면 추천이
// VERSION_UNRESOLVED 로 실패한다 — 재현할 수 없는 결과를 재현 가능한 척 남기지 않겠다는
// 규칙이고 그것이 맞다. 그래서 하네스를 고치지 않고 이 검사에서만 채운다.
//
// 하네스의 프로퍼티를 늘리지 않는 이유는 그쪽 주석에 있다 — 여정마다 프로퍼티가 다르면
// Spring 이 컨텍스트를 새로 캐시해 앱이 여러 번 뜬다. 여기서 더하는 둘은 이 클래스만
// 쓰므로 컨텍스트가 하나 더 생기지만, 그 대가로 다른 기능 검사들의 컨텍스트를 안 건드린다.
@org.springframework.test.context.TestPropertySource(properties = {
		"gabolle.recommendation.service-version=test-local",
		"gabolle.recommendation.deployment-environment=test" })
class RecommendationWithRealPlacesFunctionalTest extends FunctionalJourneyTest {

	/**
	 * 🔴 이 여정은 여행 조건에 알레르기(민감 제약)를 실어 보낸다 — S15P21E201-549 로
	 * 건강·식이 동의가 없으면 여행 생성이 403 이다. 동의를 켜고 가입하는 것이 이 여정에서
	 * 재려는 것(추천에 실제 장소가 담기는가)에 닿기 위한 전제다.
	 *
	 * <p>🔴 공용 헬퍼의 기본값을 바꾸지 않고 여기서만 켠다. 전부 켜 두면 동의를 안 받았을 때
	 * 막히는지를 아무 여정도 안 재게 된다.
	 */
	private static final java.util.Map<String, Boolean> HEALTH_CONSENT =
			java.util.Map.of("HEALTH_CONSTRAINTS", true);

	/**
	 * 부산 남구 우암동 언저리. 표본 200곳 중 반경 5km(엔진 기본값) 안에 97곳이 들어와
	 * 후보가 넉넉하다. 전체 2,355곳을 적재해 두었으면 그보다 훨씬 많다.
	 */
	private static final double ORIGIN_LAT = 35.1265;

	private static final double ORIGIN_LNG = 129.0709;

	/** 표본으로 넣은 장소에 붙는 수집분 이름. 운영 수집분과 섞이지 않게 따로 둔다. */
	private static final String SAMPLE_DATASET = "research-sample-functional";

	private static final String SAMPLE = "/research/queue-sample.ndjson";

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private SbizPlaceLoader loader;

	/**
	 * 표본이 아직 없으면 넣는다. 이미 있으면 그대로 쓴다 — 적재기는 같은 가게를 건너뛴다.
	 *
	 * <h2>🔴 "표가 비었나" 로 묻지 않는다</h2>
	 *
	 * 예전에는 {@code SELECT COUNT(*) FROM place} 가 0 일 때만 표본을 넣었다. 그런데
	 * <b>장소를 심는 마이그레이션이 하나라도 생기면 그 수가 0 이 아니게 된다.</b> 그러면 이
	 * 검사는 표본 200곳을 건너뛰고, 반경 5km 안에 후보가 없어 추천이
	 * {@code ENGINE_NO_CANDIDATES} 로 실패한다 — 엔진이 고장난 것이 아니라 <b>먹일 것을 안
	 * 넣은 것</b>인데, 실패 메시지는 엔진을 가리켜서 원인을 엉뚱한 데서 찾게 된다.
	 *
	 * <p>2026-09-16 에 실제로 그랬다. {@code V20260916210000__curated_core_busan_landmarks.sql}
	 * 이 부산 대표 명소 <b>4곳</b>을 넣자 이 검사 두 건이 무너졌다(MR !982). 바다·자연 장소를
	 * 넣는 뒤 마이그레이션도 같은 자리를 밟는다.
	 *
	 * <p>그래서 <b>이 검사가 넣은 표본이 있는지</b>를 묻는다. 그것이 원래 묻고 싶었던 것이고,
	 * 남이 장소를 몇 곳 넣든 흔들리지 않는다.
	 */
	private void ensurePlaces() {
		if (samplePlaceCount() > 0) {
			return;
		}
		OffsetDateTime collectedAt = OffsetDateTime.now();
		ResearchQueueReader.read(sampleFile(), 500,
				chunk -> this.loader.saveChunk(chunk, SAMPLE_DATASET, collectedAt));

		assertThat(samplePlaceCount()).as("표본을 넣었는데 장소가 하나도 안 들어갔다").isPositive();
	}

	private int samplePlaceCount() {
		Integer count = this.jdbc.queryForObject("SELECT COUNT(*) FROM place WHERE dataset_version = ?",
				Integer.class, SAMPLE_DATASET);
		return count == null ? 0 : count;
	}

	/** 판독기가 경로를 받으므로 상주 표본을 임시 파일로 풀어 준다. */
	private static Path sampleFile() {
		try (InputStream in = RecommendationWithRealPlacesFunctionalTest.class.getResourceAsStream(SAMPLE)) {
			assertThat(in).as("표본 파일이 없다: %s", SAMPLE).isNotNull();
			Path file = Files.createTempFile("queue-sample", ".ndjson");
			file.toFile().deleteOnExit();
			Files.write(file, in.readAllBytes());
			return file;
		}
		catch (java.io.IOException exception) {
			throw new java.io.UncheckedIOException(exception);
		}
	}

	/**
	 * S15P21E201-804 에서 실제로 걸린 자리다. 장소를 넣고 추천을 돌렸더니 작업이
	 * {@code ENGINE_NOT_CONFIGURED} 로 실패했다 — 진단해 보니 <b>엔진이 요구하는 리포지토리도
	 * 후보 조회 서비스도 다 있는데 엔진 빈만 없었다.</b> 프로필도 {@code dev} 로 맞았다.
	 *
	 * <p>그리고 그 상황을 잡으라고 만들어 둔 {@code BaselineEngineStartupValidator} 도 같은
	 * 조건({@code @ConditionalOnBean})을 쓰는 바람에 <b>함께 안 만들어졌다.</b> 감시하려던
	 * 실패에 감시자가 함께 걸린 것이고, 그 파일 주석이 걱정한 상황이 그대로 일어났다.
	 *
	 * <p>이것이 운영에 나가면 앱은 정상으로 뜨고 로그도 안 남는데 <b>모든 추천이 조용히
	 * 실패한다.</b> 그래서 기동 검사기에 기대지 않고 여기서 한 번 더 못 박는다 — 실제 HTTP 로
	 * 도는 이 자리는 조건 평가 순서에 영향을 안 받는다.
	 */
	@Test
	@DisplayName("장소가 있는데 추천 엔진이 없으면 모든 추천이 조용히 실패한다")
	void theEngineMustBeWiredWhenPlacesExist() {
		ensurePlaces();
		AuthedClient authed = loginAsNewUser("rec-wiring", HEALTH_CONSENT);

		String tripId = createTrip(authed);
		String jobId = requestRecommendation(authed, tripId);
		String body = pollUntil(() -> authed.get("/api/v1/jobs/" + jobId, String.class), response -> {
			String status = JsonPath.read(response, "$.data.status");
			return !"PENDING".equals(status) && !"RUNNING".equals(status);
		});

		assertThat(body)
				.as("추천 엔진이 배선되지 않았다. 장소는 있는데 엔진 빈이 없으면 앱은 멀쩡히 뜨고 "
						+ "모든 추천만 조용히 실패한다. 응답 전문: %s", body)
				.doesNotContain("ENGINE_NOT_CONFIGURED");
	}

	@Test
	@DisplayName("장소를 넣으면 추천 후보가 0건이 아니다 — 이것이 이 티켓의 완료 기준이다")
	void recommendationFindsCandidatesOncePlacesExist() {
		ensurePlaces();
		AuthedClient authed = loginAsNewUser("rec-real", HEALTH_CONSENT);

		String tripId = createTrip(authed);
		String jobId = requestRecommendation(authed, tripId);

		String body = pollUntil(
				() -> authed.get("/api/v1/jobs/" + jobId, String.class),
				response -> {
					String status = JsonPath.read(response, "$.data.status");
					return !"PENDING".equals(status) && !"RUNNING".equals(status);
				});

		String status = JsonPath.read(body, "$.data.status");
		// 실패해도 그 이유를 그대로 드러낸다. 여기서 조용히 통과시키면 "돌긴 도는데 결과가
		//    없다" 는 상태가 초록으로 보인다.
		assertThat(status).as("추천 작업이 실패했다. 응답 전문: %s", body).isEqualTo("SUCCEEDED");
	}

	@Test
	@DisplayName("추천 결과에 실제 가게 이름이 담긴다 — 빈 목록이 아니라 사람이 갈 수 있는 곳이다")
	void theResultCarriesRealPlaceNames() {
		ensurePlaces();
		AuthedClient authed = loginAsNewUser("rec-names", HEALTH_CONSENT);

		String tripId = createTrip(authed);
		String jobId = requestRecommendation(authed, tripId);
		pollUntil(() -> authed.get("/api/v1/jobs/" + jobId, String.class), response -> {
			String status = JsonPath.read(response, "$.data.status");
			return !"PENDING".equals(status) && !"RUNNING".equals(status);
		});

		ResponseEntity<String> result = authed.get("/api/v1/recommendation-jobs/" + jobId, String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);

		java.util.List<String> names = JsonPath.read(result.getBody(), "$.data.items[*].title");
		assertThat(names)
				.as("추천 결과가 비어 있다. 응답 전문: %s", result.getBody())
				.isNotEmpty();
		// 지어낸 이름이 아니라 조사한 가게가 실제로 담겼는지 본다.
		assertThat(names).allSatisfy(name -> assertThat(name).isNotBlank());
		System.out.println("### 추천된 가게 " + names.size() + "곳: " + names);
	}

	@Test
	@DisplayName("고른 갈래에 맞는 곳이 없으면 그렇다고 말한다 — 버전 오류로 뭉개지지 않는다")
	void anEmptyCategorySaysSoInsteadOfBlamingVersions() {
		ensurePlaces();
		AuthedClient authed = loginAsNewUser("rec-empty-category", HEALTH_CONSENT);

		// 🔴 "후보가 0곳" 을 <b>갈래</b>로 만들지 않는다. 예전에는 "적재된 장소는 전부
		//    음식점이니 바다를 고르면 0곳" 이었는데, 그 전제는 바다 장소를 넣는
		//    마이그레이션 하나로 무너진다 — 2026-09-16 에 실제로 그랬다(MR !984 가
		//    바다·자연 18곳을 넣자 이 작업이 SUCCEEDED 로 끝나 이 검사가 빨개졌다).
		//
		//    그래서 <b>거리</b>로 만든다. 엔진의 후보 질의 반경은 5km 고정이고 넓히지
		//    않으므로(BaselineEngineProperties.radiusM = 5000), 부산에서 멀리 떨어진
		//    바다 한가운데를 출발지로 두면 <b>무엇을 적재하든</b> 후보가 0곳이다.
		//    이 검사가 보려는 것은 "갈래가 비었나" 가 아니라 "후보가 없을 때 무슨 코드로
		//    말하는가" 이므로, 0곳이 되는 이유는 아무래도 좋다.
		String tripId = createTripFarFromAnyPlace(authed);
		String jobId = requestRecommendation(authed, tripId);
		String body = pollUntil(() -> authed.get("/api/v1/jobs/" + jobId, String.class), response -> {
			String status = JsonPath.read(response, "$.data.status");
			return !"PENDING".equals(status) && !"RUNNING".equals(status);
		});

		// 2026-09-10 배포에서 이 요청이 VERSION_UNRESOLVED 로 끝났다. 후보가 없어 수집분
		// 이름을 못 정한 것은 원인이 아니라 결과인데, 그 결과가 코드로 나갔다.
		assertThat(body)
				.as("후보가 없는 것을 버전 문제로 말하고 있다. 응답 전문: %s", body)
				.doesNotContain("VERSION_UNRESOLVED")
				.contains("ENGINE_NO_CANDIDATES");
		// 다시 불러도 안 달라진다 — 자료가 들어와야 바뀐다.
		assertThat(body).contains("\"retryable\":false");
	}

	private String createTrip(AuthedClient authed) {
		return createTrip(authed, "FOOD", ORIGIN_LAT, ORIGIN_LNG);
	}

	private String createTrip(AuthedClient authed, String categoryCode) {
		return createTrip(authed, categoryCode, ORIGIN_LAT, ORIGIN_LNG);
	}

	/**
	 * 반경 5km 안에 장소가 하나도 없을 수밖에 없는 출발지로 여행을 만든다 — 남해 먼바다다.
	 *
	 * <p>이 좌표에 장소가 적재될 일은 없다. 바다 위라서다. 그래서 이 검사는 <b>앞으로
	 * 무엇을 적재하든</b> 안 흔들린다.
	 */
	private String createTripFarFromAnyPlace(AuthedClient authed) {
		return createTrip(authed, "SEA_BEACH", 34.60, 128.40);
	}

	private String createTrip(AuthedClient authed, String categoryCode, double originLat, double originLng) {
		// 알레르기를 "없다"(NONE)로 답한다. 실제 사용자가 "알레르기 없음" 을 고르는 것과
		//    같고, 서버가 제약을 최소 하나 요구하기 때문이기도 하다(RecommendationJobRunner).
		//
		//    SELECTED 로 답하면 안 된다 — 알레르기 표식(ALLERGEN_TAG)이 아직 비어 있어서
		//    후보가 "확인 안 됨" 으로 전량 제외된다. 그것은 이 검사가 보려는 것과 다른
		//    문제이고, 짐작으로 채우는 것을 DB 가 막고 있어 지금은 풀 수 없다.
		Map<String, Object> request = Map.ofEntries(
				Map.entry("startDate", "2026-10-01"),
				Map.entry("finishDate", "2026-10-02"),
				Map.entry("originLat", originLat),
				Map.entry("originLng", originLng),
				Map.entry("partySize", 2),
				Map.entry("timeWindow", "09:00-18:00"),
				Map.entry("timezone", "Asia/Seoul"),
				Map.entry("preferences", java.util.List.of(
						Map.of("dimension", "category", "value", "[\"" + categoryCode + "\"]", "answerStatus", "SELECTED"))),
				Map.entry("constraints", java.util.List.of(Map.of(
						"type", "ALLERGY",
						"constraintKey", "NONE",
						// 알레르기는 언제나 HARD 다 — 서버가 SOFT 를 거부한다. 안전에 관한
						// 것을 "웬만하면" 으로 두면 그 순간 안전장치가 아니게 된다.
						"severity", "HARD",
						"operator", "EXCLUDES",
						"answerStatus", "NONE"))));

		ResponseEntity<String> response = authed.post("/api/v1/trips", request, String.class);
		assertThat(response.getStatusCode())
				.as("여행 생성이 실패했다. 응답 전문: %s", response.getBody())
				.isEqualTo(HttpStatus.CREATED);
		return JsonPath.read(response.getBody(), "$.data.tripId");
	}

	private String requestRecommendation(AuthedClient authed, String tripId) {
		ResponseEntity<String> response = authed.post(
				"/api/v1/trips/" + tripId + "/recommendation-jobs", Map.of(), String.class);
		assertThat(response.getStatusCode())
				.as("추천 접수가 실패했다. 응답 전문: %s", response.getBody())
				.isEqualTo(HttpStatus.ACCEPTED);
		return JsonPath.read(response.getBody(), "$.data.jobId");
	}
}
