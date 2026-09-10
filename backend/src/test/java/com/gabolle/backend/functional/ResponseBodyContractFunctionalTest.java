package com.gabolle.backend.functional;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
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
 * 응답 본문의 <b>칸 이름과 타입</b>을 고정한다 — S15P21E201-789.
 *
 * <h2>왜 이 검사가 생겼나</h2>
 * 2026-09-08 에 "내 여행" 화면이 죽었다. 서버는 목록을 <b>배열</b>로 주는데 앱은 <b>객체</b>로
 * 읽었다({@code {trips: [...]}} 를 기대했다). 그런데 서버 쪽 검사는 전부 초록이었다 — 상태
 * 코드만 봤기 때문이다. <b>200 은 모양이 맞다는 뜻이 아니다.</b>
 *
 * <p>그래서 이 검사는 응답을 DTO 로 되읽지 않는다. DTO 로 읽으면 Jackson 이 모양을 맞춰 주므로
 * 이름이 바뀌었는지 타입이 바뀌었는지가 <b>가려진다.</b> 날것의 JSON 문자열을 그대로 보고
 * 이름과 타입을 확인한다.
 *
 * <p>실제 소켓과 실제 보안 필터를 지난다({@code FunctionalJourneyTest}) — 앱이 받는 것과 같은
 * 바이트를 본다는 뜻이다.
 */
class ResponseBodyContractFunctionalTest extends FunctionalJourneyTest {

	/** 앱이 실제로 쓰는 목록 경로. 이 계약이 깨진 것이 위 사고였다. */
	private static final String TRIPS = "/api/v1/trips";

	/**
	 * 여행 하나를 만든다.
	 *
	 * <p>🔴 몸통을 문자열이 아니라 {@link Map} 으로 넘긴다. 문자열로 넘기면 요청의
	 * {@code Content-Type} 이 {@code text/plain} 이 되고, 그러면 서버가 415 를 정한 뒤
	 * {@code /error} 로 다시 디스패치하면서 <b>401 로 바뀌어</b> 나온다 — 인증은 멀쩡한데
	 * "로그인이 필요합니다" 가 온다. {@code SecurityConfig} 주석이 같은 함정을 이미 적어 뒀다.
	 * {@code Map} 으로 넘기면 Jackson 이 JSON 으로 쓰면서 헤더도 맞춰 준다.
	 */
	private ResponseEntity<String> createTrip(AuthedClient authed) {
		LocalDate start = LocalDate.now().plusDays(30);
		// 🔴 출발지 좌표를 함께 보낸다 — S15P21E201-440 부터 서버가 좌표 없는 여행을 거부한다
		//    (좌표가 없으면 일정 계산이 성립하지 않는다).
		Map<String, Object> body = Map.of(
				"startDate", start.toString(),
				"finishDate", start.plusDays(2).toString(),
				"partySize", 2,
				"originLat", 35.15,
				"originLng", 129.16);
		return authed.post(TRIPS, body, String.class);
	}

	@Test
	@DisplayName("여행이 없는 계정의 목록은 빈 배열이다 — 객체를 감싸 보내지 않는다")
	void tripListOfANewAccountIsAnEmptyArray() {
		AuthedClient authed = loginAsNewUser("contract-empty");

		ResponseEntity<String> response = authed.get(TRIPS, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		Object data = JsonPath.read(response.getBody(), "$.data");
		assertThat(data)
				.withFailMessage("목록의 data 는 배열이어야 합니다. 객체로 바뀌면 앱의 목록 화면이 죽습니다.")
				.isInstanceOf(List.class);
		assertThat((List<?>) data).isEmpty();
		// 🔴 감싼 모양으로 되돌아가는 것도 계약 위반이다. 앱이 기대했다가 죽은 모양이 이것이다.
		assertThat(response.getBody()).doesNotContain("\"trips\"");
	}

	@Test
	@DisplayName("여행을 만들면 목록 행의 칸 이름과 타입이 약속대로다")
	void tripRowKeepsItsFieldNamesAndTypes() {
		AuthedClient authed = loginAsNewUser("contract-row");
		ResponseEntity<String> created = createTrip(authed);
		assertThat(created.getStatusCode())
				.withFailMessage("여행 만들기가 실패했습니다: %s %s", created.getStatusCode(), created.getBody())
				.isEqualTo(HttpStatus.CREATED);

		ResponseEntity<String> response = authed.get(TRIPS, String.class);

		List<Map<String, Object>> rows = JsonPath.read(response.getBody(), "$.data");
		assertThat(rows).hasSize(1);
		Map<String, Object> row = rows.get(0);

		assertThat(row).containsKeys("tripId", "startDate", "endDate", "dayCount", "partySize",
				"status", "role", "createdAt", "updatedAt");
		assertThat(row.get("tripId")).isInstanceOf(String.class);
		// 🔴 날짜는 문자열이다. 숫자(epoch)로 바뀌면 앱의 날짜 표시가 조용히 깨진다.
		assertThat(row.get("startDate")).isInstanceOf(String.class);
		assertThat(row.get("endDate")).isInstanceOf(String.class);
		// 🔴 개수는 숫자다. 문자열로 바뀌면 앱의 계산이 문자열 이어붙이기가 된다.
		assertThat(row.get("dayCount")).isInstanceOf(Integer.class);
		assertThat(row.get("partySize")).isInstanceOf(Integer.class);
		assertThat(row.get("status")).isInstanceOf(String.class);
		assertThat(row.get("role")).isInstanceOf(String.class);
		assertThat(row.get("createdAt")).isNotNull();
	}

	@Test
	@DisplayName("봉투가 data·error·meta 셋을 유지한다 — 성공 응답의 error 는 비어 있다")
	void envelopeKeepsItsThreeParts() {
		AuthedClient authed = loginAsNewUser("contract-envelope");

		ResponseEntity<String> response = authed.get(TRIPS, String.class);

		Map<String, Object> envelope = JsonPath.read(response.getBody(), "$");
		assertThat(envelope).containsKeys("data", "error", "meta");
		assertThat(envelope.get("error")).isNull();
	}

	@Test
	@DisplayName("실패 응답도 같은 봉투를 쓴다 — 사유 코드가 error.code 에 있다")
	void failureUsesTheSameEnvelope() {
		AuthedClient authed = loginAsNewUser("contract-failure");

		// 없는 여행을 부른다. 모양이 성공과 다르면 앱이 오류를 못 읽는다.
		ResponseEntity<String> response = authed.get(TRIPS + "/00000000-0000-0000-0000-000000000000",
				String.class);

		assertThat(Boolean.valueOf(response.getStatusCode().is2xxSuccessful())).isFalse();
		Map<String, Object> envelope = JsonPath.read(response.getBody(), "$");
		assertThat(envelope).containsKeys("data", "error", "meta");
		Object code = JsonPath.read(response.getBody(), "$.error.code");
		assertThat(code).isInstanceOf(String.class);
	}

	@Test
	@DisplayName("남의 여행은 목록에도 안 나오고 직접 불러도 못 본다")
	void anotherUsersTripIsNeitherListedNorReadable() {
		AuthedClient owner = loginAsNewUser("contract-owner");
		ResponseEntity<String> created = createTrip(owner);
		assertThat(created.getStatusCode())
				.withFailMessage("여행 만들기가 실패했습니다: %s %s", created.getStatusCode(), created.getBody())
				.isEqualTo(HttpStatus.CREATED);
		String tripId = JsonPath.<String>read(created.getBody(), "$.data.tripId");

		AuthedClient stranger = loginAsNewUser("contract-stranger");

		List<?> strangersRows = JsonPath.read(stranger.get(TRIPS, String.class).getBody(), "$.data");
		assertThat(strangersRows).isEmpty();

		ResponseEntity<String> direct = stranger.get(TRIPS + "/" + tripId, String.class);
		assertThat(direct.getStatusCode())
				.withFailMessage("남의 여행을 직접 불렀는데 %s 가 나왔습니다.", direct.getStatusCode())
				.isIn(HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN);
	}

	@Test
	@DisplayName("내 정보의 칸 이름과 타입이 약속대로다")
	void myProfileKeepsItsFieldNamesAndTypes() {
		AuthedClient authed = loginAsNewUser("contract-me");

		ResponseEntity<String> response = authed.get("/api/v1/auth/me", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map<String, Object> me = JsonPath.read(response.getBody(), "$.data");
		assertThat(me).containsKey("email");
		assertThat(me.get("email")).isInstanceOf(String.class);
		assertThat((String) me.get("email")).contains("contract-me");
	}
}
