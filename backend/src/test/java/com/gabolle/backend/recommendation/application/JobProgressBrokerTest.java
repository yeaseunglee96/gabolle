package com.gabolle.backend.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.gabolle.backend.recommendation.application.JobProgressBroker.JobProgressSnapshot;
import com.gabolle.backend.recommendation.domain.JobStage;
import com.gabolle.backend.recommendation.domain.JobStatus;

/**
 * 진행률이 제 작업을 보는 사람에게만 가는가, 그리고 연결이 쌓이지 않는가. Spring 도 DB 도
 * 필요 없이 나간 건수와 닫혔는지만 세는 연결({@link RecordingEmitter})을 붙인다.
 */
class JobProgressBrokerTest {

	private JobProgressBroker broker;

	@BeforeEach
	void setUp() {
		this.broker = new JobProgressBroker();
	}

	/**
	 * 나간 건수와 닫힘을 기억하는 연결. {@link SseEmitter} 가 실제로 흘려보내는 핸들러는
	 * 패키지 밖에서 붙일 수 없어 공개 메서드를 덮어 무엇을 보내려 했는지를 센다. 줄 모양은
	 * {@code RecommendationJobProgressStreamTest} 가 본다.
	 */
	static class RecordingEmitter extends SseEmitter {

		final List<Object> sent = new ArrayList<>();

		final AtomicBoolean completed = new AtomicBoolean(false);

		/** 참이면 보내기가 실패한다 — 화면을 닫은 브라우저를 흉내낸다. */
		boolean broken;

		RecordingEmitter() {
			super(1000L);
		}

		@Override
		public void send(SseEventBuilder builder) throws IOException {
			if (this.broken) {
				throw new IOException("연결이 끊겼다");
			}
			this.sent.add(builder);
		}

		@Override
		public void complete() {
			this.completed.set(true);
		}
	}

	private static JobProgressSnapshot running(UUID jobId, JobStage stage) {
		return new JobProgressSnapshot(jobId, JobStatus.RUNNING, stage.name(), stage.percent(), null);
	}

	@Test
	@DisplayName("동시에 두 작업이 돌아도 각자 자기 작업의 진행률만 받는다")
	void progressGoesOnlyToStreamsOfTheSameJob() {
		UUID mine = UUID.randomUUID();
		UUID theirs = UUID.randomUUID();
		RecordingEmitter myStream = new RecordingEmitter();
		RecordingEmitter theirStream = new RecordingEmitter();
		this.broker.register(mine, myStream);
		this.broker.register(theirs, theirStream);

		this.broker.publish(running(mine, JobStage.RANKING));

		assertThat(myStream.sent).hasSize(1);
		assertThat(theirStream.sent).isEmpty();
	}

	@Test
	@DisplayName("끝 상태를 보내면 그 연결이 닫힌다")
	void terminalSnapshotClosesTheStream() {
		UUID jobId = UUID.randomUUID();
		RecordingEmitter stream = new RecordingEmitter();
		this.broker.register(jobId, stream);

		this.broker.publish(new JobProgressSnapshot(jobId, JobStatus.SUCCEEDED,
				JobStage.COMPLETED.name(), 100, null));

		assertThat(stream.sent).hasSize(1);
		assertThat(stream.completed).isTrue();
	}

	@Test
	@DisplayName("실패도 끝이라 연결이 닫힌다 — 사유 코드가 함께 나간다")
	void failureAlsoClosesTheStream() {
		UUID jobId = UUID.randomUUID();
		RecordingEmitter stream = new RecordingEmitter();
		this.broker.register(jobId, stream);

		this.broker.publish(new JobProgressSnapshot(jobId, JobStatus.FAILED,
				JobStage.CANDIDATE_GENERATION.name(), 20, "ENGINE_UNAVAILABLE"));

		assertThat(stream.completed).isTrue();
	}

	@Test
	@DisplayName("사건 이름이 상태에 따라 갈린다")
	void eventNameFollowsTheStatus() {
		UUID jobId = UUID.randomUUID();
		assertThat(running(jobId, JobStage.RANKING).eventName()).isEqualTo("progress");
		assertThat(new JobProgressSnapshot(jobId, JobStatus.SUCCEEDED, "COMPLETED", 100, null).eventName())
				.isEqualTo("completed");
		assertThat(new JobProgressSnapshot(jobId, JobStatus.FAILED, "RANKING", 70, "X").eventName())
				.isEqualTo("failed");
	}

	@Test
	@DisplayName("보내다 끊긴 연결은 목록에서 빠진다 — 죽은 연결이 쌓이지 않는다")
	void brokenStreamsAreForgotten() {
		UUID jobId = UUID.randomUUID();
		RecordingEmitter stream = new RecordingEmitter();
		this.broker.register(jobId, stream);
		assertThat(this.broker.streamCount(jobId)).isEqualTo(1);
		stream.broken = true;

		this.broker.publish(running(jobId, JobStage.RANKING));

		assertThat(this.broker.streamCount(jobId)).isZero();
	}

	@Test
	@DisplayName("끊긴 연결 때문에 추천 계산이 실패하지 않는다")
	void aBrokenStreamNeverBreaksTheCaller() {
		UUID jobId = UUID.randomUUID();
		RecordingEmitter stream = new RecordingEmitter();
		this.broker.register(jobId, stream);
		stream.broken = true;

		// 예외가 위로 새면 이 자리를 부르는 추천 계산이 통째로 실패한다.
		this.broker.publish(running(jobId, JobStage.RANKING));
	}

	@Test
	@DisplayName("한 작업의 연결이 상한을 넘으면 가장 오래된 것을 닫는다")
	void oldestStreamIsClosedBeyondTheCap() {
		UUID jobId = UUID.randomUUID();
		List<RecordingEmitter> streams = new ArrayList<>();
		for (int i = 0; i < JobProgressBroker.MAX_STREAMS_PER_JOB + 1; i++) {
			RecordingEmitter stream = new RecordingEmitter();
			streams.add(stream);
			this.broker.register(jobId, stream);
		}

		assertThat(this.broker.streamCount(jobId)).isEqualTo(JobProgressBroker.MAX_STREAMS_PER_JOB);
		assertThat(streams.get(0).completed).isTrue();
		assertThat(streams.get(streams.size() - 1).completed).isFalse();
	}

	@Test
	@DisplayName("보는 사람이 없는 작업의 진행률은 조용히 버려진다")
	void publishingWithoutListenersIsHarmless() {
		this.broker.publish(running(UUID.randomUUID(), JobStage.RANKING));
	}
}
