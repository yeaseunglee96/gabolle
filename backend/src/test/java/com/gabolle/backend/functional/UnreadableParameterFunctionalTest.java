package com.gabolle.backend.functional;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.functional.support.AuthedClient;
import com.gabolle.backend.functional.support.FunctionalJourneyTest;

/**
 * 한글 검색어를 CP949 로 인코딩한 요청 — 400 은 그대로, 본문은 우리 봉투, 기록은 WARN 한 줄 (S15P21E201-1762).
 *
 * <p>진짜 포트로 띄운다 — 글자 풀기는 톰캣이 매개변수를 읽을 때 일어나서 MockMvc 로는 못 본다. 고치기 전(2026-09-26 이 PC
 * 로컬에서 잼): 세 주소 모두 400 · 본문 {@code {"timestamp","status":400,"error":"Bad Request","path"}} · 요청마다
 * {@code Servlet.service() for servlet [dispatcherServlet] … threw exception} ERROR 가 스택째 한 벌.
 */
@ExtendWith(OutputCaptureExtension.class)
class UnreadableParameterFunctionalTest extends FunctionalJourneyTest {

	private static final ParameterizedTypeReference<ApiResponse<Void>> ENVELOPE = new ParameterizedTypeReference<>() {
	};

	/** 「해운대」를 CP949 로 인코딩한 것 — 운영에 실제로 온 모양이다. UTF-8 로는 풀리지 않는다. */
	private static final String CP949_HAEUNDAE = "%C7%D8%BF%EE%B4%EB";

	@Test
	@DisplayName("🔴 CP949 검색어 — 400 · 우리 봉투 · INVALID_REQUEST, 톰캣 ERROR 대신 WARN 한 줄")
	void cp949QueryIsBadRequestInOurEnvelopeWithOneWarning(CapturedOutput output) {
		AuthedClient user = loginAsNewUser("bad-encoding");

		for (String path : java.util.List.of("/api/v1/places?query=" + CP949_HAEUNDAE,
				"/api/v1/origins?query=" + CP949_HAEUNDAE)) {
			int before = output.getAll().length();

			ResponseEntity<ApiResponse<Void>> response = user.getVerbatim(path, new HttpHeaders(), ENVELOPE);

			assertThat(response.getStatusCode()).as(path).isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat(response.getBody().error().code()).isEqualTo("INVALID_REQUEST");
			assertThat(response.getBody().error().message()).isEqualTo("요청을 읽지 못했어요. 다시 시도해 주세요.");
			assertThat(response.getBody().meta().requestId()).isNotBlank();
			String logs = output.getAll().substring(before);
			assertThat(logs).as(path).doesNotContain("Servlet.service()").doesNotContain(" ERROR ");
			// 기록 문장의 「—」는 이 PC 의 윈도 콘솔에서 「?」로 바뀐다 — 인코딩에 안 흔들리는 뒷부분으로 센다.
			String route = path.substring(0, path.indexOf('?'));
			assertThat(logs).as(path).containsOnlyOnce("path=" + route + " cause=MalformedInputException");
			assertThat(logs).as(path).contains(" WARN ");
		}
	}

	@Test
	@DisplayName("영어 사용자(Accept-Language: en)에게는 영어 문장 — 404·405 봉투와 같은 규칙")
	void englishSpeakersGetAnEnglishSentence() {
		AuthedClient user = loginAsNewUser("bad-encoding-en");
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9");

		ResponseEntity<ApiResponse<Void>> response = user.getVerbatim("/api/v1/places?query=" + CP949_HAEUNDAE,
				headers, ENVELOPE);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().error().code()).isEqualTo("INVALID_REQUEST");
		assertThat(response.getBody().error().message()).isEqualTo("We couldn't read the request. Please try again.");
	}
}
