package com.gabolle.backend.recommendation.application;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.gabolle.backend.recommendation.domain.JobStatus;

/**
 * 열려 있는 진행률 통로를 작업 번호별로 들고 있다. SSE(Server-Sent Events — 서버가 연결을
 * 열어 둔 채 한 방향으로 계속 밀어 보내는 방식) 연결 하나를 {@link SseEmitter} 하나가
 * 나타낸다.
 *
 * <p>보내는 자리에서 고르지 않고 자료 구조 자체를 작업 번호로 나눈다 — 고르는 코드는
 * 언젠가 빠뜨리고, 그러면 남의 진행률이 섞여 나간다.
 *
 * <p>누가 그 작업의 주인인지는 여기서 보지 않는다. 그 검사는 연결을 받아들일 때 표현
 * 계층이 한다 — 여기까지 온 연결은 이미 통과한 것이라 두 번 묻지 않는다.
 *
 * <p>알려진 한계: 이 기억은 이 프로세스의 메모리에 있어 서버 한 대를 전제한다. 여러 대로
 * 늘리면 계산은 A 가 하고 연결은 B 가 들고 있는 경우가 생겨 B 의 연결로 아무것도 안 간다.
 * 늘릴 때는 프로세스 사이를 잇는 것이 필요하고, 화면은 그때도 폴링으로 되돌아갈 수 있어야
 * 한다 — {@code GET /api/v1/jobs/{jobId}} 는 없애지 않는다.
 */
@Component
public class JobProgressBroker {

	private static final Logger log = LoggerFactory.getLogger(JobProgressBroker.class);

	/**
	 * 작업 하나가 동시에 가질 수 있는 연결 수의 상한. 재접속을 반복하다 이전 연결이 아직
	 * 정리되지 않으면 연결이 쌓여 스레드와 메모리가 마른다. 넘으면 가장 오래된 것을 닫는다 —
	 * 새 연결을 거절하면 방금 화면을 켠 사용자가 못 보게 된다.
	 */
	static final int MAX_STREAMS_PER_JOB = 4;

	private final Map<UUID, Set<SseEmitter>> streamsByJob = new ConcurrentHashMap<>();

	/**
	 * 이 작업의 진행률을 받을 연결을 등록한다.
	 *
	 * <p>연결이 끝나면(정상 종료 · 시간 초과 · 오류) 스스로 목록에서 빠진다. 빼는 코드를
	 * 부르는 쪽에 맡기면 언젠가 한 갈래에서 안 부르고, 그러면 죽은 연결이 남는다.
	 */
	public void register(UUID jobId, SseEmitter emitter) {
		Set<SseEmitter> streams = this.streamsByJob.computeIfAbsent(jobId, (key) -> new CopyOnWriteArraySet<>());
		streams.add(emitter);

		emitter.onCompletion(() -> remove(jobId, emitter));
		emitter.onTimeout(() -> {
			// 시간 초과는 오류가 아니다. 화면이 다시 붙으면 그 시점의 진행률부터 이어 간다.
			emitter.complete();
			remove(jobId, emitter);
		});
		emitter.onError((error) -> remove(jobId, emitter));

		trimOldest(jobId, streams);
	}

	/**
	 * 이 작업을 보고 있는 연결에 진행률을 밀어 보낸다.
	 *
	 * <p>보내다 실패한 연결은 조용히 버린다. 화면을 닫은 브라우저로 보내면 예외가 나는데,
	 * 그것을 위로 던지면 추천 계산 쪽이 실패한다.
	 *
	 * <p>끝 상태(성공 · 실패)면 보낸 뒤 연결을 닫는다.
	 */
	public void publish(JobProgressSnapshot snapshot) {
		Set<SseEmitter> streams = this.streamsByJob.get(snapshot.jobId());
		if (streams == null || streams.isEmpty()) {
			return;
		}
		for (SseEmitter emitter : streams) {
			send(snapshot, emitter);
		}
	}

	/**
	 * 연결 하나에 지금 상태를 보낸다. 접속 직후에도 이 메서드로 한 번 보내므로, 끊겼다 다시
	 * 붙은 화면은 0%가 아니라 지금까지 올라간 값을 받는다.
	 */
	public void send(JobProgressSnapshot snapshot, SseEmitter emitter) {
		try {
			emitter.send(SseEmitter.event().name(snapshot.eventName()).data(snapshot));
			if (snapshot.status().isTerminal()) {
				emitter.complete();
			}
		}
		catch (IOException | IllegalStateException ex) {
			// 이미 닫힌 연결이다. 흔한 일이고 고칠 것이 없다 — 목록에서만 뺀다.
			log.debug("진행률 전송 실패 — 연결을 정리합니다. jobId={}", snapshot.jobId(), ex);
			remove(snapshot.jobId(), emitter);
		}
	}

	/** 지금 이 작업을 보고 있는 연결 수. 검사와 운영 점검용이다. */
	public int streamCount(UUID jobId) {
		Set<SseEmitter> streams = this.streamsByJob.get(jobId);
		return (streams == null) ? 0 : streams.size();
	}

	private void remove(UUID jobId, SseEmitter emitter) {
		this.streamsByJob.computeIfPresent(jobId, (key, streams) -> {
			streams.remove(emitter);
			// 빈 집합을 남기지 않는다. 작업은 계속 새로 생기므로 그대로 두면 이 지도가
			// 작업 수만큼 자라기만 한다.
			return streams.isEmpty() ? null : streams;
		});
	}

	private void trimOldest(UUID jobId, Set<SseEmitter> streams) {
		while (streams.size() > MAX_STREAMS_PER_JOB) {
			SseEmitter oldest = streams.iterator().next();
			streams.remove(oldest);
			oldest.complete();
			log.debug("한 작업의 진행률 연결이 상한을 넘어 가장 오래된 것을 닫았습니다. jobId={}", jobId);
		}
	}

	/**
	 * 화면으로 나가는 한 건.
	 *
	 * @param jobId   어느 작업인가. 화면이 연결을 재사용하지 않더라도 실어 보낸다 — 로그와
	 *                디버깅에서 이 값이 없으면 어느 작업의 사건인지 알 수 없다
	 * @param status  지금 상태
	 * @param stage   지금 단계
	 * @param percent 0~100
	 * @param code    실패했을 때의 사유 코드. 그 밖에는 {@code null}
	 */
	public record JobProgressSnapshot(UUID jobId, JobStatus status, String stage, int percent, String code) {

		/**
		 * 사건 이름. 화면은 이 이름으로 처리를 가른다.
		 *
		 * <p>진행 중에는 {@code progress}, 끝나면 {@code completed} 또는 {@code failed} 다.
		 * 끝을 진행률 100으로만 알리지 않는 이유는, 실패도 끝이지만 100이 아니기 때문이다.
		 */
		public String eventName() {
			if (this.status == JobStatus.SUCCEEDED) {
				return "completed";
			}
			if (this.status == JobStatus.FAILED) {
				return "failed";
			}
			return "progress";
		}
	}
}
