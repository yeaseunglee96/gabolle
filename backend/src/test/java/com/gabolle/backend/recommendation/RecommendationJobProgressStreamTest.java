package com.gabolle.backend.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.recommendation.application.BlockingConstraintAnalyzer;
import com.gabolle.backend.recommendation.application.JobProgressBroker;
import com.gabolle.backend.recommendation.application.JobProgressReporter;
import com.gabolle.backend.recommendation.application.RecommendationJobRunner;
import com.gabolle.backend.recommendation.domain.JobStage;
import com.gabolle.backend.recommendation.domain.JobStatus;
import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.presentation.RecommendationJobController;
import com.gabolle.backend.recommendation.presentation.RecommendationJobExceptionHandler;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;

/**
 * 진행률 통로가 실제로 무엇을 흘려보내는가. 응답 본문을 본다 — 상태 코드만 보는 검사는
 * 연결은 열렸는데 아무것도 안 나간 경우를 통과시킨다.
 *
 * DB 없이 도는 슬라이스다. 작업 조회와 저장소는 mock 이고, 진행률을 밀어 보내는 쪽은 이
 * 검사가 보려는 경로라 진짜 객체를 쓴다.
 */
class RecommendationJobProgressStreamTest {

	private RecommendationJobRunner runner;

	private JobProgressBroker broker;

	private JobProgressReporter reporter;

	private MockMvc mockMvc;

	private final UUID ownerId = UUID.randomUUID();

	private final UUID jobId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		this.runner = mock(RecommendationJobRunner.class);
		this.broker = new JobProgressBroker();
		this.reporter = new JobProgressReporter(mock(RecommendationJobRepository.class), this.broker);

		this.mockMvc = MockMvcBuilders
				.standaloneSetup(new RecommendationJobController(this.runner, this.broker,
						// 막은 조건 분석기. 이 검사는 진행률만 보므로 실패한 작업이 없고,
						// 그래서 분석기가 하는 일도 없다.
						mock(BlockingConstraintAnalyzer.class)))
				.setControllerAdvice(new RecommendationJobExceptionHandler())
				.build();
	}

	private static Authentication principal(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
	}

	private RecommendationJob job() {
		return RecommendationJob.start(this.jobId, UUID.randomUUID(), this.ownerId,
				JobType.ITINERARY_GENERATION, OffsetDateTime.now());
	}

	/** 이미 절반쯤 진행된 작업 — 화면이 뒤늦게 접속하거나 다시 붙는 상황이다. */
	private RecommendationJob halfwayJob() {
		RecommendationJob job = job();
		job.markRunning(JobStage.CANDIDATE_GENERATION);
		job.markStage(JobStage.RANKING);
		return job;
	}

	private MvcResult subscribe(UUID actingUserId) throws Exception {
		return this.mockMvc.perform(get("/api/v1/jobs/{jobId}/progress", this.jobId)
						.principal(principal(actingUserId)))
				.andExpect(request().asyncStarted())
				.andReturn();
	}

	/**
	 * 컨테이너가 비동기 요청을 마무리하는 것을 흉내낸다. {@code emitter.complete()} 는
	 * 뒷정리 콜백을 부르지 않는다 — {@code onCompletion} 은 서블릿 컨테이너가 비동기 요청을
	 * 끝낼 때 불리므로, MockMvc 에서는 이렇게 한 번 태워 줘야 같은 일이 벌어진다.
	 */
	private void finishAsync(MvcResult result) throws Exception {
		this.mockMvc.perform(asyncDispatch(result));
	}

	@Test
	@DisplayName("접속하면 지금 진행률을 즉시 받는다 — 0%로 되돌아가지 않는다")
	void subscribingSendsTheCurrentProgressAtOnce() throws Exception {
		when(this.runner.findJob(this.jobId.toString())).thenReturn(Optional.of(halfwayJob()));

		MvcResult result = subscribe(this.ownerId);

		String body = result.getResponse().getContentAsString();
		assertThat(body).contains("event:progress");
		assertThat(body).contains("\"percent\":" + JobStage.RANKING.percent());
		assertThat(body).contains("\"stage\":\"RANKING\"");
		// 끊긴 지점부터 이어지는지를 본다. 다시 붙은 화면이 0 을 받으면 사용자는 진행이
		// 처음부터 다시 도는 것으로 본다.
		assertThat(body).doesNotContain("\"percent\":0");
	}

	@Test
	@DisplayName("단계가 넘어가면 열린 연결로 새 진행률이 밀려온다")
	void advancingTheStagePushesToTheOpenStream() throws Exception {
		RecommendationJob job = halfwayJob();
		when(this.runner.findJob(this.jobId.toString())).thenReturn(Optional.of(job));
		MvcResult result = subscribe(this.ownerId);

		this.reporter.advance(job, JobStage.ROUTE_OPTIMIZATION);

		String body = result.getResponse().getContentAsString();
		assertThat(body).contains("\"percent\":" + JobStage.RANKING.percent());
		assertThat(body).contains("\"percent\":" + JobStage.ROUTE_OPTIMIZATION.percent());
		assertThat(body).contains("\"stage\":\"ROUTE_OPTIMIZATION\"");
	}

	@Test
	@DisplayName("완료 신호가 나가고 연결이 닫힌다")
	void completionIsSentAndTheStreamCloses() throws Exception {
		RecommendationJob job = halfwayJob();
		when(this.runner.findJob(this.jobId.toString())).thenReturn(Optional.of(job));
		MvcResult result = subscribe(this.ownerId);

		job.markCompleted(OffsetDateTime.now(), OffsetDateTime.now(), null, null);
		this.reporter.publishCurrent(job);

		String body = result.getResponse().getContentAsString();
		assertThat(body).contains("event:completed");
		assertThat(body).contains("\"percent\":100");
		// 끝난 작업의 연결을 붙들고 있을 이유가 없다.
		finishAsync(result);
		assertThat(this.broker.streamCount(this.jobId)).isZero();
	}

	@Test
	@DisplayName("실패하면 사유가 나가고 연결이 닫힌다")
	void failureIsSentAndTheStreamCloses() throws Exception {
		RecommendationJob job = halfwayJob();
		when(this.runner.findJob(this.jobId.toString())).thenReturn(Optional.of(job));
		MvcResult result = subscribe(this.ownerId);

		job.markFailed("ENGINE_UNAVAILABLE", JobStage.CANDIDATE_GENERATION, OffsetDateTime.now(), false, true);
		this.reporter.publishCurrent(job);

		String body = result.getResponse().getContentAsString();
		assertThat(body).contains("event:failed");
		assertThat(body).contains("ENGINE_UNAVAILABLE");
		finishAsync(result);
		assertThat(this.broker.streamCount(this.jobId)).isZero();
	}

	@Test
	@DisplayName("이미 끝난 작업에 접속하면 한 건만 받고 바로 닫힌다")
	void subscribingToAFinishedJobClosesImmediately() throws Exception {
		RecommendationJob job = halfwayJob();
		job.markCompleted(OffsetDateTime.now(), OffsetDateTime.now(), null, null);
		when(this.runner.findJob(this.jobId.toString())).thenReturn(Optional.of(job));

		MvcResult result = subscribe(this.ownerId);

		assertThat(result.getResponse().getContentAsString()).contains("event:completed");
		// 등록 자체를 하지 않는다 — 다시 내보낼 것이 없는 작업을 기다리게 두지 않는다.
		assertThat(this.broker.streamCount(this.jobId)).isZero();
	}

	@Test
	@DisplayName("남의 작업 번호로는 진행률을 볼 수 없다 — 없는 작업과 같은 404")
	void othersJobIsHiddenBehindTheSame404() throws Exception {
		when(this.runner.findJob(this.jobId.toString())).thenReturn(Optional.of(halfwayJob()));

		this.mockMvc.perform(get("/api/v1/jobs/{jobId}/progress", this.jobId)
						.principal(principal(UUID.randomUUID())))
				.andExpect(status().isNotFound());

		// 거절된 요청이 연결을 남기면 남의 작업 진행률이 그 연결로 흘러간다.
		assertThat(this.broker.streamCount(this.jobId)).isZero();
	}

	@Test
	@DisplayName("없는 작업 번호도 404")
	void unknownJobIs404() throws Exception {
		when(this.runner.findJob(this.jobId.toString())).thenReturn(Optional.empty());

		this.mockMvc.perform(get("/api/v1/jobs/{jobId}/progress", this.jobId)
						.principal(principal(this.ownerId)))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("동시에 두 작업이 돌면 각자 자기 작업의 진행률만 받는다")
	void twoJobsDoNotLeakIntoEachOther() throws Exception {
		RecommendationJob mine = halfwayJob();
		when(this.runner.findJob(this.jobId.toString())).thenReturn(Optional.of(mine));
		MvcResult myStream = subscribe(this.ownerId);

		// 다른 사용자의 다른 작업이 단계를 넘어간다.
		RecommendationJob theirs = RecommendationJob.start(UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), JobType.ITINERARY_GENERATION, OffsetDateTime.now());
		theirs.markRunning(JobStage.CANDIDATE_GENERATION);
		this.reporter.advance(theirs, JobStage.ROUTE_OPTIMIZATION);

		String body = myStream.getResponse().getContentAsString();
		assertThat(body).doesNotContain("ROUTE_OPTIMIZATION");
		assertThat(body).doesNotContain(theirs.getJobId().toString());
	}

	@Test
	@DisplayName("응답이 이벤트 스트림 형식으로 나간다")
	void theResponseIsAnEventStream() throws Exception {
		when(this.runner.findJob(this.jobId.toString())).thenReturn(Optional.of(halfwayJob()));

		MvcResult result = subscribe(this.ownerId);

		assertThat(result.getResponse().getContentType()).contains("text/event-stream");
	}

	@Test
	@DisplayName("작업이 아직 대기 중이면 진행률 0으로 시작한다")
	void pendingJobStartsAtZero() throws Exception {
		RecommendationJob pending = job();
		when(this.runner.findJob(this.jobId.toString())).thenReturn(Optional.of(pending));

		MvcResult result = subscribe(this.ownerId);

		String body = result.getResponse().getContentAsString();
		assertThat(body).contains("event:progress");
		assertThat(body).contains("\"percent\":0");
		assertThat(body).contains("\"status\":\"PENDING\"");
	}

	@Test
	@DisplayName("상태가 " + "PENDING" + "이어도 연결은 열려 있다")
	void pendingJobKeepsTheStreamOpen() throws Exception {
		when(this.runner.findJob(this.jobId.toString())).thenReturn(Optional.of(job()));

		subscribe(this.ownerId);

		assertThat(this.broker.streamCount(this.jobId)).isEqualTo(1);
	}
}
