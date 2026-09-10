package com.gabolle.backend.batch.application;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 한 배치가 무엇을 했는지 — MLOps Phase 1.
 *
 * <h2>🔴 갈래를 세어서 돌려준다. "성공 N 건" 이 아니다</h2>
 *
 * 부르는 쪽(Airflow)이 이 수를 보고 판단해야 하는 것이 둘 있다.
 *
 * <ul>
 * <li><b>{@code rebuilt} 가 날마다 사람 수만큼 나온다</b> — 접는 규칙이 무언가에 흔들리고
 * 있다는 뜻이다. 설문이 안 바뀌었으면 판은 안 늘어야 한다</li>
 * <li><b>{@code failed} 가 0 이 아니다</b> — 몇 명이 조용히 낡은 채로 남았다는 뜻이다.
 * "배치는 성공" 이라고만 적으면 이 사실이 사라진다</li>
 * </ul>
 *
 * 성공·실패 두 칸으로 뭉치면 둘 다 안 보인다.
 *
 * @param asOf 어느 시각까지를 본 배치인가. 재현할 때 이 값이 있어야 같은 배치를 다시 돌린다
 * @param actions 갈래별 건수
 * @param failedUserIds 실패한 사람. 다음 실행에서 다시 걸리므로 여기서 재시도하지 않는다
 * @param failures 실패 이유 (사람이 읽는 용도. 스택은 로그에 있다)
 */
public record TasteVectorBatchReport(OffsetDateTime asOf, Map<TasteVectorFoldOutcome.Action, Integer> actions,
		List<UUID> failedUserIds, List<String> failures) {

	private static final Logger log = LoggerFactory.getLogger(TasteVectorBatchReport.class);

	/** 실패 이유를 응답에 몇 개까지 실을지. 전부 실으면 응답이 로그가 된다. */
	private static final int MAX_REPORTED_FAILURES = 20;

	public int count(TasteVectorFoldOutcome.Action action) {
		return this.actions.getOrDefault(action, 0);
	}

	public int processed() {
		return this.actions.values().stream().mapToInt(Integer::intValue).sum();
	}

	public int failed() {
		return this.failedUserIds.size();
	}

	static Builder builder(OffsetDateTime asOf) {
		return new Builder(asOf);
	}

	static final class Builder {

		private final OffsetDateTime asOf;

		private final Map<TasteVectorFoldOutcome.Action, Integer> actions = new EnumMap<>(
				TasteVectorFoldOutcome.Action.class);

		private final List<UUID> failedUserIds = new ArrayList<>();

		private final List<String> failures = new ArrayList<>();

		private Builder(OffsetDateTime asOf) {
			this.asOf = asOf;
		}

		void record(TasteVectorFoldOutcome outcome) {
			this.actions.merge(outcome.action(), 1, Integer::sum);
		}

		/**
		 * 한 사람의 실패를 적는다. 배치는 계속 간다.
		 *
		 * <p>🔴 스택 추적은 <b>로그에만</b> 남긴다. 응답에 실으면 내부 클래스 이름과 SQL 이
		 * 그대로 나가는데, 이 API 는 기계가 부르는 것이라 아무도 그것을 읽지 않고
		 * 그냥 Airflow 로그에 쌓인다.
		 */
		void recordFailure(UUID userId, RuntimeException ex) {
			log.error("취향 벡터 접기 실패 user={} asOf={}", userId, this.asOf, ex);
			this.failedUserIds.add(userId);
			if (this.failures.size() < MAX_REPORTED_FAILURES) {
				this.failures.add(userId + ": " + ex.getClass().getSimpleName() + " " + ex.getMessage());
			}
		}

		TasteVectorBatchReport build() {
			return new TasteVectorBatchReport(this.asOf, Map.copyOf(this.actions), List.copyOf(this.failedUserIds),
					List.copyOf(this.failures));
		}
	}
}
