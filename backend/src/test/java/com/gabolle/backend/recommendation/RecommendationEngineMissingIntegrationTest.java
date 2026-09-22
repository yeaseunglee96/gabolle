package com.gabolle.backend.recommendation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.recommendation.application.RecommendationCodes;
import com.gabolle.backend.recommendation.application.RecommendationCommand;
import com.gabolle.backend.recommendation.application.RecommendationFailedException;
import com.gabolle.backend.recommendation.application.RecommendationService;
import com.gabolle.backend.recommendation.domain.JobStatus;
import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;
import com.gabolle.backend.recommendation.support.PersonalizationFixture;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.testslice.RecommendationEngineAbsentSliceApplication;
import com.gabolle.backend.recommendation.support.TestDatabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 엔진 빈이 없는 배포에서도 애플리케이션이 뜨는가. 엔진이 기대는 패키지를 안 올리는 배포는
 * 있을 수 있고, 그때 추천이 조용히 빈 결과로 끝나면 안 된다. 그 상태를 만드는 것이
 * {@link RecommendationEngineAbsentSliceApplication} 이다.
 *
 * 확인하는 것은 둘이다 — 컨텍스트가 뜨는가, 그리고 추천을 부르면 기록된 실패가 되는가.
 */
@SpringBootTest(classes = RecommendationEngineAbsentSliceApplication.class, properties = {
		// 인증이 도입한 프로필 방식에 맞춘다 — active 가 있으면 spring.profiles.default(no-db) 는 적용되지 않는다.
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true",
		"gabolle.recommendation.service-version=test-service-0.0.1",
		"gabolle.recommendation.deployment-environment=test"
})
@ExtendWith(PostgresAvailableCondition.class)
class RecommendationEngineMissingIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private RecommendationService recommendationService;

	@Autowired
	private RecommendationJobRepository jobRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("🔴 엔진 구현이 없어도 애플리케이션은 뜬다")
	void theApplicationStartsWithoutARecommendationEngine() {
		assertThat(this.recommendationService).isNotNull();
	}

	@Test
	@DisplayName("엔진이 없을 때 추천을 부르면 조용히 넘어가지 않고 ENGINE_NOT_CONFIGURED 로 기록된다")
	void callingWithoutAnEngineFailsLoudlyAndIsRecorded() {
		// 외래키가 붙어 있어 임의 UUID 로는 Job 이 저장되지 않는다.
		PersonalizationFixture.Ids references = PersonalizationFixture.insert(this.jdbcTemplate);
		RecommendationCommand command = new RecommendationCommand(references.userId(),
				JobType.ITINERARY_GENERATION, references.tripId(), references.tripVersion(),
				references.preferenceSnapshotId(), references.constraintSnapshotId(),
				null, null, null, "app-1.0.0", 5, null);

		assertThatThrownBy(() -> this.recommendationService.recommend(command))
				.isInstanceOf(RecommendationFailedException.class)
				.extracting((thrown) -> ((RecommendationFailedException) thrown).getErrorCode())
				.isEqualTo(RecommendationCodes.ERROR_ENGINE_NOT_CONFIGURED);

		RecommendationJob job = this.jobRepository.findAll().stream()
				.filter((candidate) -> candidate.getUserId().equals(command.userId()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("실패한 요청의 Job 이 저장되지 않았다"));

		assertThat(job.getJobStatus()).isEqualTo(JobStatus.FAILED);
		assertThat(job.getErrorCode()).isEqualTo(RecommendationCodes.ERROR_ENGINE_NOT_CONFIGURED);
		// 엔진을 붙인다고 같은 요청이 지금 성공하지는 않는다 — 배포 설정 문제다.
		assertThat(job.isRetryable()).isFalse();
	}
}
