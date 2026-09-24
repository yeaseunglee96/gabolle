package com.gabolle.backend.functional;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.gabolle.backend.functional.support.AuthedClient;
import com.gabolle.backend.functional.support.FunctionalJourneyTest;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 갈래 열람 기록과 집계. 기록이 부수적인 일이라는 것이 이 기능의 핵심 성질이라, "쌓이는가" 만 보지
 * 않고 실패했을 때 화면이 멈추지 않는가도 본다.
 */
class FacetViewFunctionalTest extends FunctionalJourneyTest {

	private static final String TRIPS = "/api/v1/trips";

	private static final String COUNTS = "/api/v1/admin/facet-views";

	/** 여행에 안 묶인 전역 탐색이 쓰는 자리. */
	private static final String GLOBAL_FACET_VIEWS = "/api/v1/facet-views";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private String createTrip(AuthedClient authed) {
		LocalDate start = LocalDate.now().plusDays(30);
		Map<String, Object> body = Map.of(
				"startDate", start.toString(),
				"finishDate", start.plusDays(2).toString(),
				"partySize", 2,
				"originLat", 35.15,
				"originLng", 129.16,
				// 2박이라 숙소가 있어야 한다(S15P21E201-1585). 출발지(해운대) 그 동네로.
				"accommodationArea", "HAEUNDAE");
		ResponseEntity<String> created = authed.post(TRIPS, body, String.class);
		assertThat(created.getStatusCode())
				.withFailMessage("여행 만들기가 실패했습니다: %s", created.getBody())
				.isEqualTo(HttpStatus.CREATED);
		return JsonPath.<String>read(created.getBody(), "$.data.tripId");
	}

	private ResponseEntity<String> openFacet(AuthedClient authed, String tripId, String facetKey) {
		return authed.post(TRIPS + "/" + tripId + "/facet-views/" + facetKey, Map.of(), String.class);
	}

	@Test
	@DisplayName("완료 기준 — 갈래를 열면 기록이 한 건 쌓이고 어느 갈래인지 구분된다")
	void openingAFacetIsRecordedAndTellsWhichFacet() {
		AuthedClient authed = loginAsNewUser("facet-view");
		String tripId = createTrip(authed);

		assertThat(openFacet(authed, tripId, "NIGHT_MARKET").getStatusCode())
				.isEqualTo(HttpStatus.ACCEPTED);
		assertThat(openFacet(authed, tripId, "NIGHT_MARKET").getStatusCode())
				.isEqualTo(HttpStatus.ACCEPTED);
		assertThat(openFacet(authed, tripId, "WALK").getStatusCode())
				.isEqualTo(HttpStatus.ACCEPTED);

		Map<String, Long> counts = countsByFacet(authed, "facet-view");
		assertThat(counts.get("NIGHT_MARKET")).isGreaterThanOrEqualTo(2);
		assertThat(counts.get("WALK")).isGreaterThanOrEqualTo(1);
	}

	@Test
	@DisplayName("완료 기준 — 갈래별 열람 수를 조회할 수 있다. 많은 순으로 온다")
	void countsComeBackMostViewedFirst() {
		AuthedClient authed = loginAsNewUser("facet-order");
		String tripId = createTrip(authed);
		// 한 갈래를 확실히 더 많이 연다. 다른 검사가 남긴 기록과 섞여도 순서가 뒤집히지 않을
		// 만큼 차이를 둔다.
		for (int i = 0; i < 12; i++) {
			openFacet(authed, tripId, "TRADITIONAL_MARKET");
		}

		String body = adminCounts(authed, "facet-order").getBody();
		List<String> keys = JsonPath.read(body, "$.data[*].facetKey");
		assertThat(keys).contains("TRADITIONAL_MARKET");
		List<Integer> views = JsonPath.read(body, "$.data[*].views");
		assertThat(views).isSortedAccordingTo((a, b) -> Integer.compare(b, a));
	}

	@Test
	@DisplayName("완료 기준 — 기록에 이름·이메일 같은 값이 없다")
	void theRecordCarriesNoPersonalValues() {
		AuthedClient authed = loginAsNewUser("facet-privacy");
		String tripId = createTrip(authed);
		openFacet(authed, tripId, "NATURE");

		// 집계 응답에 갈래와 수 말고는 아무것도 없다.
		String body = adminCounts(authed, "facet-privacy").getBody();
		List<Map<String, Object>> rows = JsonPath.read(body, "$.data");
		assertThat(rows).allSatisfy((row) -> assertThat(row).containsOnlyKeys("facetKey", "views"));
		assertThat(body).doesNotContain("@example.com");
	}

	@Test
	@DisplayName("남의 여행 번호로는 갈래 열람을 남길 수 없다 — 없는 여행과 같은 404")
	void anotherUsersTripCannotBeUsed() {
		AuthedClient owner = loginAsNewUser("facet-owner");
		String tripId = createTrip(owner);

		AuthedClient stranger = loginAsNewUser("facet-stranger");
		ResponseEntity<String> response = openFacet(stranger, tripId, "ACTIVITY");

		// 아무나 남길 수 있으면 이 숫자로 우선순위를 정할 수 없다.
		assertThat(response.getStatusCode()).isIn(HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN);
	}

	@Test
	@DisplayName("완료 기준 — 기록이 실패하는 값이어도 갈래 목록은 그대로 열린다")
	void aFailingRecordNeverBreaksTheScreen() {
		AuthedClient authed = loginAsNewUser("facet-resilient");
		String tripId = createTrip(authed);

		// 표의 칸 길이(40)를 넘는 갈래 코드다. 저장은 실패하지만 이 경로는 그것을 삼킨다 —
		// 기록은 부수적인 일이므로 화면이 대신 실패해서는 안 된다.
		String tooLong = "X".repeat(80);
		ResponseEntity<String> response = openFacet(authed, tripId, tooLong);

		assertThat(response.getStatusCode())
				.withFailMessage("기록 실패가 화면으로 새어 나왔다: %s %s",
						response.getStatusCode(), response.getBody())
				.isEqualTo(HttpStatus.ACCEPTED);

		// 그리고 갈래 목록은 그대로 열린다.
		ResponseEntity<String> facets = authed.get("/api/v1/places/facets", String.class);
		assertThat(facets.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	@DisplayName("집계는 운영자 경로다 — 일반 회원은 못 본다")
	void countsAreForAdminsOnly() {
		AuthedClient authed = loginAsNewUser("facet-not-admin");

		ResponseEntity<String> response = authed.get(COUNTS, String.class);

		assertThat(response.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED);
	}

	/**
	 * 운영자로 승격한 계정으로 집계를 읽는다. 승격을 API 로 열지 않은 것이 의도라 역할을 DB 에서
	 * 직접 바꾼다.
	 *
	 * <p>다시 로그인하지 않아도 된다 — 이 앱은 역할을 토큰이 아니라 매 요청 DB 에서 읽으므로
	 * ({@code HmacJwtAuthenticationFilter}) 행을 바꾸면 다음 요청부터 바로 운영자다.
	 */
	private ResponseEntity<String> adminCounts(AuthedClient authed, String emailPrefix) {
		this.jdbcTemplate.update("UPDATE app_user SET role = 'ADMIN' WHERE user_id IN ("
				+ "SELECT u.user_id FROM app_user u JOIN local_credential c ON c.user_id = u.user_id "
				+ "WHERE c.email LIKE ?)", emailPrefix + "+%");

		ResponseEntity<String> response = authed.get(COUNTS, String.class);
		assertThat(response.getStatusCode())
				.withFailMessage("운영자로 승격했는데 집계를 못 읽었다: %s %s",
						response.getStatusCode(), response.getBody())
				.isEqualTo(HttpStatus.OK);
		return response;
	}

	private ResponseEntity<String> openGlobalFacet(AuthedClient authed, String facetKey) {
		return authed.post(GLOBAL_FACET_VIEWS + "/" + facetKey, Map.of(), String.class);
	}

	/** 갈래별 열람 수를 지도로. 다른 검사가 남긴 기록과 섞이므로 "이상" 으로만 비교한다. */
	private Map<String, Long> countsByFacet(AuthedClient authed, String emailPrefix) {
		List<Map<String, Object>> rows = JsonPath.read(adminCounts(authed, emailPrefix).getBody(), "$.data");
		Map<String, Long> counts = new HashMap<>();
		for (Map<String, Object> row : rows) {
			counts.put((String) row.get("facetKey"), ((Number) row.get("views")).longValue());
		}
		return counts;
	}
	@Test
	@DisplayName("완료 기준 — 여행 없이도 갈래 열람이 남고, 여행 안 기록과 같은 집계에 들어간다 (-894)")
	void aFacetOpenedOutsideAnyTripIsRecordedInTheSameCount() {
		AuthedClient authed = loginAsNewUser("facet-global");
		String tripId = createTrip(authed);

		// 같은 갈래를 여행 안에서 한 번, 여행 밖에서 두 번 연다.
		assertThat(openFacet(authed, tripId, "SEA_BEACH").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(openGlobalFacet(authed, "SEA_BEACH").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(openGlobalFacet(authed, "SEA_BEACH").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

		// 표를 나누지 않았으므로 셋이 한 숫자로 합쳐진다. 나뉘어 있으면 이 단정이 2 에서 멈춘다.
		assertThat(countsByFacet(authed, "facet-global").get("SEA_BEACH")).isGreaterThanOrEqualTo(3);
	}

	@Test
	@DisplayName("여행 밖 기록은 여행에 안 묶인다 — 그 행의 여행 번호가 비어 있다 (-894)")
	void aGlobalRecordCarriesNoTripId() {
		AuthedClient authed = loginAsNewUser("facet-global-null");
		String facetKey = "GLOBAL_ONLY_" + System.nanoTime() % 100000;

		assertThat(openGlobalFacet(authed, facetKey).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

		Integer withoutTrip = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM place_facet_view WHERE facet_key = ? AND trip_id IS NULL",
				Integer.class, facetKey);
		assertThat(withoutTrip).isEqualTo(1);
	}

	@Test
	@DisplayName("로그인하지 않으면 여행 밖 기록도 남길 수 없다 — 열어 두면 아무나 집계를 부풀린다 (-894)")
	void anonymousCannotRecordGlobalFacetView() {
		ResponseEntity<String> response = this.rest.postForEntity(GLOBAL_FACET_VIEWS + "/NIGHT_VIEW",
				Map.of(), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}
}
