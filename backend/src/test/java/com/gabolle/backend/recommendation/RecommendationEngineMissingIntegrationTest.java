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
import com.gabolle.testslice.RecommendationSliceApplication;
import com.gabolle.backend.recommendation.support.TestDatabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 추천 엔진 구현이 <b>없는</b> 배포에서도 애플리케이션이 뜨는가.
 *
 * <p>🔴 이 테스트가 있는 이유. 운영 코드에는 {@code RecommendationEnginePort} 구현이 없다 —
 * 실제 추천 서버 계약이 아직 없어서 지어내지 않았다. 그런데 전에는 그 결정의 대가를 남의
 * 도메인이 치렀다: 회원·여행 담당이 DB 를 쓰려고 {@code db} 프로필로 올리는 순간
 * <b>애플리케이션 전체가 못 떴다.</b>
 *
 * <p>그래서 이 클래스는 fake 엔진을 <b>일부러 붙이지 않는다.</b> 확인하는 것은 둘이다 —
 * 컨텍스트가 뜨는가, 그리고 추천을 실제로 부르면 조용히 빈 결과가 아니라 기록된 실패가 되는가.
 */
@SpringBootTest(classes = RecommendationSliceApplication.class, properties = {
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
		// 🔴 S15P21E201-554 가 외래키를 붙였다. 임의 UUID 로는 Job 이 저장되지 않는다.
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
