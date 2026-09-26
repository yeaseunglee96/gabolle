package com.gabolle.backend.functional;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;

import com.gabolle.backend.auth.api.AuthUserResponse;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.functional.support.AuthedClient;
import com.gabolle.backend.functional.support.FunctionalJourneyTest;
import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;

/**
 * 🔴 S15P21E201-1724 — 진행률 스트림이 끝날 때 연결이 끝 표시로 닫힌다.
 *
 * <p>SSE(연결을 열어 둔 채 알림을 밀어 보내는 방식)가 끝나면 서블릿 컨테이너가 같은 요청을 ASYNC 로
 * 한 번 더 들여보낸다. 그 차례에 인가가 막히면 응답이 이미 나가는 중이라 401 도 못 쓰고, 서버는 ERROR 를
 * 남기며 청크 응답의 끝 표시 없이 연결을 끊는다. 받는 쪽은 마지막 알림까지 받은 뒤 「응답이 중간에
 * 끊겼다」는 입출력 오류를 만난다. 운영 nginx 는 이것을 {@code upstream prematurely closed} 로 적는다.
 *
 * <p>MockMvc 로는 컨테이너의 재진입도 연결 끊김도 원리상 못 본다 — 진짜 소켓으로 읽는다.
 */
class AsyncDispatchIdentityFunctionalTest extends FunctionalJourneyTest {

	@Autowired
	private RecommendationJobRepository jobRepository;

	@Test
	@DisplayName("완료 기준 — 끝난 작업의 진행률 스트림은 마지막 알림을 보내고 끊김 없이 닫힌다")
	void aFinishedJobStreamEndsWithoutBreakingTheConnection() {
		AuthedClient owner = loginAsNewUser("async-dispatch");
		UUID ownerId = owner.get("/api/v1/auth/me", new ParameterizedTypeReference<ApiResponse<AuthUserResponse>>() {
		}).getBody().data().userId();

		// 끝난 작업에 붙으면 서버는 한 건을 보내고 바로 닫는다 — 스트림이 끝나는 순간을 기다림 없이 만든다.
		RecommendationJob job = RecommendationJob.start(UUID.randomUUID(), UUID.randomUUID(), ownerId,
				JobType.ITINERARY_GENERATION, OffsetDateTime.now());
		job.markFailed("ENGINE_NO_CANDIDATES", null, OffsetDateTime.now(), false, false);
		this.jobRepository.save(job);
		try {
			ResponseEntity<String> response;
			try {
				response = owner.get("/api/v1/jobs/" + job.getJobId() + "/progress", String.class);
			}
			catch (RestClientException broken) {
				throw new AssertionError("""
						진행률 스트림이 끝 표시 없이 끊겼다. 스트림이 끝날 때의 ASYNC 재진입이 인가에 막혔을 수
						있다 — SecurityConfig 의 dispatcherTypeMatchers(ASYNC) 를 본다: %s"""
					.formatted(broken.getMessage()), broken);
			}

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).contains("\"status\":\"FAILED\"");
		}
		finally {
			this.jobRepository.deleteById(job.getJobId());
		}
	}

	@Test
	@DisplayName("고친 뒤에도 진행률 스트림은 자격증명 없이 못 연다")
	void theStreamStillRequiresCredentials() {
		ResponseEntity<String> response = this.rest.getForEntity("/api/v1/jobs/" + UUID.randomUUID() + "/progress",
				String.class);

		// 연 것은 재진입 차례뿐이다. 첫 차례는 그대로 막혀야 한다.
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("AUTHENTICATION_REQUIRED");
	}
}
