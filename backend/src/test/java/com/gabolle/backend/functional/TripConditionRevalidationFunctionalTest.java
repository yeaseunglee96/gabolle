package com.gabolle.backend.functional;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.functional.support.AuthedClient;
import com.gabolle.backend.functional.support.FunctionalJourneyTest;
import com.gabolle.backend.recommendation.application.RecommendationJobRunner;
import com.gabolle.backend.trip.application.TripCreationService;
import com.jayway.jsonpath.JsonPath;

/**
 * 화면을 거치지 않고 오는 요청도 서버가 같은 조건을 보는지 실제 HTTP 로 확인한다. 규칙 자체의 판정은
 * {@code TripConditionRulesTest} 가 재고, 여기서는 그 판정이 400 과 항목 이름으로 나가는지만 본다.
 */
class TripConditionRevalidationFunctionalTest extends FunctionalJourneyTest {

	private static final String TRIPS = "/api/v1/trips";

	@Autowired
	private JdbcTemplate jdbc;

	/** 숙소 필수 스위치(S15P21E201-1596)를 켜 보는 시험이 있다. 컨텍스트를 새로 띄우지 않으려고 빈에서 바로 켠다. */
	@Autowired
	private TripCreationService tripCreationService;

	@Autowired
	private RecommendationJobRunner recommendationJobRunner;

	/** 공용 컨텍스트를 다른 여정에 넘기기 전에 기본(꺼짐)으로 되돌린다. */
	@AfterEach
	void switchLodgingRuleBackOff() {
		this.tripCreationService.setLodgingRequired(false);
		this.recommendationJobRunner.setLodgingRequired(false);
	}

	private void requireLodging() {
		this.tripCreationService.setLodgingRequired(true);
		this.recommendationJobRunner.setLodgingRequired(true);
	}

	/** 성립하는 요청 한 벌. 각 검사는 여기서 한 칸만 어긋나게 바꿔 보낸다. */
	private static Map<String, Object> validBody() {
		LocalDate start = LocalDate.now().plusDays(30);
		Map<String, Object> body = new HashMap<>();
		body.put("startDate", start.toString());
		body.put("finishDate", start.plusDays(2).toString());
		body.put("partySize", 2);
		body.put("budgetKrw", 100_000);
		body.put("originLat", 35.15);
		body.put("originLng", 129.16);
		body.put("timeWindow", "09:00-18:00");
		// 2박이라 숙소가 있어야 한다(S15P21E201-1585) — 숙소 동네로 채운다.
		body.put("accommodationArea", "HAEUNDAE");
		return body;
	}

	private ResponseEntity<String> post(AuthedClient authed, Map<String, Object> body) {
		return authed.post(TRIPS, body, String.class);
	}

	private static List<String> fieldsOf(ResponseEntity<String> response) {
		return JsonPath.read(response.getBody(), "$.error.fields");
	}

	@Test
	@DisplayName("모든 조건을 만족하는 요청은 저장된다")
	void aValidRequestIsStored() {
		AuthedClient authed = loginAsNewUser("revalidate-ok");

		ResponseEntity<String> response = post(authed, validBody());

		assertThat(response.getStatusCode())
				.withFailMessage("성립하는 요청이 거부됐습니다: %s", response.getBody())
				.isEqualTo(HttpStatus.CREATED);
	}

	@Test
	@DisplayName("오는 날이 앞선 조건을 보내면 거부되고 응답에 항목 이름이 들어 있다")
	void finishBeforeStartIsRejectedWithTheFieldName() {
		AuthedClient authed = loginAsNewUser("revalidate-dates");
		Map<String, Object> body = validBody();
		body.put("finishDate", LocalDate.now().plusDays(29).toString());

		ResponseEntity<String> response = post(authed, body);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(JsonPath.<String>read(response.getBody(), "$.error.code"))
				.isEqualTo("TRIP_VALIDATION_FAILED");
		// "잘못된 요청" 만 돌려주지 않는다. 화면이 어느 칸을 짚을지 알아야 한다.
		assertThat(fieldsOf(response)).anySatisfy((line) -> assertThat(line).contains("finishDate"));
	}

	@Test
	@DisplayName("예산 단위가 어긋난 값이 거부된다")
	void budgetOffTheUnitIsRejected() {
		AuthedClient authed = loginAsNewUser("revalidate-budget");
		Map<String, Object> body = validBody();
		body.put("budgetKrw", 15_500);

		ResponseEntity<String> response = post(authed, body);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(fieldsOf(response)).anySatisfy((line) -> assertThat(line).contains("budgetKrw"));
	}

	@Test
	@DisplayName("출발지 좌표가 없는 요청은 거부되고 응답에 항목 이름이 들어 있다")
	void aTripWithoutOriginIsRejected() {
		AuthedClient authed = loginAsNewUser("revalidate-origin");
		Map<String, Object> body = validBody();
		body.put("originLat", null);
		body.put("originLng", null);

		ResponseEntity<String> response = post(authed, body);

		// 좌표가 없으면 거부한다. 근거는 TripConditionRules 머리말에 있다.
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(fieldsOf(response)).anySatisfy((line) -> assertThat(line).contains("originLat"));
	}

	@Test
	@DisplayName("최대 숙박 수를 넘는 요청이 거부된다")
	void tooLongATripIsRejected() {
		AuthedClient authed = loginAsNewUser("revalidate-nights");
		Map<String, Object> body = validBody();
		body.put("finishDate", LocalDate.now().plusDays(30 + 9).toString());

		ResponseEntity<String> response = post(authed, body);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(fieldsOf(response)).anySatisfy((line) -> assertThat(line).contains("finishDate"));
	}

	/**
	 * 여러 항목이 어긋나면 응답이 그 전부를 담는가. 조합에 {@code partySize} 를 넣지 않는다 — 그 칸은
	 * 기본 검증({@code @Min(1)})이 더 앞에서 잡아 여행 조건 재검증까지 오지 않는다.
	 */
	@Test
	@DisplayName("여러 항목이 어긋나면 응답이 그 전부를 담는다")
	void everyBrokenFieldIsListed() {
		AuthedClient authed = loginAsNewUser("revalidate-many");
		Map<String, Object> body = validBody();
		body.put("finishDate", LocalDate.now().plusDays(29).toString());
		body.put("budgetKrw", 15_500);
		// 좌표 위반도 같은 목록에 함께 담기는지 본다.
		body.put("originLat", null);
		body.put("originLng", null);

		ResponseEntity<String> response = post(authed, body);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		// 하나씩 알려 주면 사용자가 고칠 때마다 다시 거절당한다.
		assertThat(fieldsOf(response)).hasSizeGreaterThanOrEqualTo(3);
		assertThat(String.join(" ", fieldsOf(response)))
				.contains("finishDate").contains("budgetKrw").contains("originLat");
	}

	// ── 숙소 (S15P21E201-1585) ─────────────────────────────────────────

	/** 앱은 칸 이름 {@code accommodation} 으로 사람 말을 고른다 — 이름이 바뀌면 화면에 원문이 뜬다. */
	@Test
	@DisplayName("🔴 숙소 필수 스위치가 켜지면 — 1박 이상인데 숙소가 없으면 400 · TRIP_VALIDATION_FAILED · 「accommodation: …」")
	void aMultiDayTripWithoutLodgingIsRejected() {
		requireLodging();
		AuthedClient authed = loginAsNewUser("revalidate-lodging");
		Map<String, Object> body = validBody();
		body.remove("accommodationArea");

		ResponseEntity<String> response = post(authed, body);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(JsonPath.<String>read(response.getBody(), "$.error.code")).isEqualTo("TRIP_VALIDATION_FAILED");
		assertThat(fieldsOf(response)).anySatisfy((line) -> assertThat(line).startsWith("accommodation: "));
	}

	@Test
	@DisplayName("당일치기는 숙소 없이 만들어진다")
	void aDayTripNeedsNoLodging() {
		AuthedClient authed = loginAsNewUser("revalidate-daytrip");
		Map<String, Object> body = validBody();
		body.remove("accommodationArea");
		body.put("finishDate", body.get("startDate"));

		ResponseEntity<String> response = post(authed, body);

		assertThat(response.getStatusCode())
				.withFailMessage("당일치기가 숙소 때문에 거부됐습니다: %s", response.getBody())
				.isEqualTo(HttpStatus.CREATED);
	}

	/**
	 * 규칙이 생기기 전에 만든 숙소 없는 여러 날 여행. 지금은 HTTP 로 만들 수 없어서, 숙소를 넣어 만든 뒤
	 * 숙소 칸을 DB 에서 비워 옛 여행을 흉내 낸다.
	 */
	@Test
	@DisplayName("🔴 숙소 필수 스위치가 켜지면 — 숙소 없는 옛 여러 날 여행은 추천 요청이 여행 만들기와 같은 400 으로 거부된다")
	void anOldMultiDayTripWithoutLodgingCannotBeRecommended() {
		requireLodging();
		AuthedClient authed = loginAsNewUser("revalidate-old-trip");
		ResponseEntity<String> created = post(authed, validBody());
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String tripId = JsonPath.read(created.getBody(), "$.data.tripId");
		this.jdbc.update("UPDATE trip SET accommodation_area = NULL, accommodation_place_id = NULL WHERE trip_id = ?",
				UUID.fromString(tripId));

		ResponseEntity<String> response = authed.post(TRIPS + "/" + tripId + "/recommendation-jobs", Map.of(),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(JsonPath.<String>read(response.getBody(), "$.error.code"))
				.as("여행 만들기와 같은 코드여야 화면이 같은 문장을 고른다").isEqualTo("TRIP_VALIDATION_FAILED");
		assertThat(fieldsOf(response)).anySatisfy((line) -> assertThat(line).startsWith("accommodation: "));
	}

	/**
	 * 앱 build 44 는 추천 동네를 골라도 동네 코드를 안 보낸다. 숙소 필수 규칙이 운영에 나가자 그 판으로 1박 이상
	 * 여행을 못 만들었다 — App Store 재제출이 그 판이다. 그래서 스위치의 기본은 꺼짐이다(S15P21E201-1596).
	 */
	@Test
	@DisplayName("🔴 스위치가 꺼져 있으면(기본) 숙소 없는 1박 이상 여행이 201 로 만들어지고 추천도 접수된다 — 앱 build 44 가 보내는 모양")
	void aMultiDayTripWithoutLodgingIsAcceptedWhileTheSwitchIsOff() {
		AuthedClient authed = loginAsNewUser("revalidate-lodging-off");
		Map<String, Object> body = validBody();
		body.remove("accommodationArea");

		ResponseEntity<String> created = post(authed, body);

		assertThat(created.getStatusCode())
				.withFailMessage("스위치가 꺼졌는데 숙소 때문에 거부됐습니다: %s", created.getBody())
				.isEqualTo(HttpStatus.CREATED);
		String tripId = JsonPath.read(created.getBody(), "$.data.tripId");
		ResponseEntity<String> job = authed.post(TRIPS + "/" + tripId + "/recommendation-jobs", Map.of(), String.class);
		assertThat(job.getBody()).as("추천 요청이 숙소 때문에 거부되면 안 된다").doesNotContain("accommodation: ");
	}
}
