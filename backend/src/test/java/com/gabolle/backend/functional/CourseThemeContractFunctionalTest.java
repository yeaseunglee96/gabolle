package com.gabolle.backend.functional;

import static org.assertj.core.api.Assertions.assertThat;

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
 * 코스 테마 목록이 실제 서버에서 무엇을 내보내는가 — S15P21E201-450.
 *
 * <p>이 목록은 화면이 그대로 그리는 값이라 <b>칸 이름과 타입</b>이 계약이다. 그래서 응답을
 * DTO 로 되읽지 않고 날것의 JSON 을 본다({@code ResponseBodyContractFunctionalTest} 와 같은
 * 이유 — 어제 목록 화면이 죽은 것이 그 자리였다).
 */
class CourseThemeContractFunctionalTest extends FunctionalJourneyTest {

	private static final String THEMES = "/api/v1/course-categories";

	/**
	 * 티켓이 이름을 적어 둔 여섯. 나머지 넷은 "외 4종" 으로만 적혀 있어 설정 파일에 제안으로
	 * 넣었고, 여기서는 그 넷의 이름을 고정하지 않는다 — 확정되면 이 목록에 더한다.
	 */
	private static final List<String> NAMED_IN_THE_TICKET = List.of(
			"ZERO_WON", "BEST_BANG", "SPLURGE", "OLD_TOWN", "NATURE_FIX", "PICTURE_PERFECT");

	@Test
	@DisplayName("완료 기준 — 목록 조회가 테마 10종을 돌려준다")
	void theListReturnsTenThemes() {
		AuthedClient authed = loginAsNewUser("course-theme");

		ResponseEntity<String> response = authed.get(THEMES, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<?> themes = JsonPath.read(response.getBody(), "$.data");
		assertThat(themes).hasSize(10);
	}

	@Test
	@DisplayName("티켓이 이름을 적어 둔 여섯 코드가 그대로 들어 있다")
	void theSixCodesNamedInTheTicketArePresent() {
		AuthedClient authed = loginAsNewUser("course-theme-codes");

		ResponseEntity<String> response = authed.get(THEMES, String.class);

		List<String> codes = JsonPath.read(response.getBody(), "$.data[*].code");
		assertThat(codes).containsAll(NAMED_IN_THE_TICKET);
	}

	@Test
	@DisplayName("테마 한 줄의 칸 이름과 타입이 약속대로다")
	void aThemeKeepsItsFieldNamesAndTypes() {
		AuthedClient authed = loginAsNewUser("course-theme-shape");

		ResponseEntity<String> response = authed.get(THEMES, String.class);

		List<Map<String, Object>> themes = JsonPath.read(response.getBody(), "$.data");
		Map<String, Object> first = themes.get(0);

		assertThat(first).containsKeys("code", "label", "boostedFeatures", "scoreEmphasis",
				"placesPerDay", "touristPenalty");
		assertThat(first.get("code")).isInstanceOf(String.class);
		assertThat(first.get("label")).isInstanceOf(String.class);
		// 배열 자리에 null 이 오면 화면이 그 자리에서 깨진다.
		assertThat(first.get("boostedFeatures")).isInstanceOf(List.class);
		assertThat(first.get("placesPerDay")).isInstanceOf(Integer.class);
	}

	@Test
	@DisplayName("설정 파일의 순서가 응답의 순서다 — 화면이 다시 정렬하지 않아도 된다")
	void theOrderFollowsTheConfiguration() {
		AuthedClient authed = loginAsNewUser("course-theme-order");

		ResponseEntity<String> response = authed.get(THEMES, String.class);

		List<String> codes = JsonPath.read(response.getBody(), "$.data[*].code");
		assertThat(codes.get(0)).isEqualTo("ZERO_WON");
		assertThat(codes.subList(0, NAMED_IN_THE_TICKET.size())).isEqualTo(NAMED_IN_THE_TICKET);
	}

	@Test
	@DisplayName("코드가 대문자와 밑줄만 쓴다 — 화면이 그대로 열쇠로 쓴다")
	void codesUseUpperSnakeCase() {
		AuthedClient authed = loginAsNewUser("course-theme-format");

		ResponseEntity<String> response = authed.get(THEMES, String.class);

		List<String> codes = JsonPath.read(response.getBody(), "$.data[*].code");
		assertThat(codes).allSatisfy((code) -> assertThat(code).matches("[A-Z][A-Z_]*"));
	}

	@Test
	@DisplayName("자격증명 없이 부르면 목록을 못 본다")
	void anonymousCallsAreRejected() {
		ResponseEntity<String> response = this.rest.getForEntity(THEMES, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}
}
