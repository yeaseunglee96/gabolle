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

/**
 * 화면을 거치지 않고 오는 요청도 서버가 같은 조건을 보는지 실제 HTTP 로 확인한다. 규칙 자체의 판정은
 * {@code TripConditionRulesTest} 가 재고, 여기서는 그 판정이 400 과 항목 이름으로 나가는지만 본다.
 */
class TripConditionRevalidationFunctionalTest extends FunctionalJourneyTest {

	private static final String TRIPS = "/api/v1/trips";

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
}
