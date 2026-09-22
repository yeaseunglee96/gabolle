package com.gabolle.backend.recommendation.domain;

/**
 * 추천 Job 한 건의 상태. 상태 전이는 단방향이다.
 *
 * 여기에 {@code FALLBACK} 이 없는 것이 계약이다 — 대체 경로로 만들었다는 것은 상태가 아니라
 * {@link FallbackMode} 가 나타낸다. 둘을 한 칸에 섞으면 성공했는데 기준선 결과였던 경우를
 * 표현할 자리가 사라진다.
 */
public enum JobStatus {
	/** 접수됐고 아직 시작하지 않았다. */
	PENDING,
	RUNNING,
	/** 결과를 만들어 반환했다. fallback 으로 만들었어도 여기다. */
	SUCCEEDED,
	/** 결과를 만들지 못했다. error_code 가 반드시 함께 남는다. */
	FAILED,
	/** 사용자가 취소했다. */
	CANCELLED,
	/** 유효 시간이 지났다. */
	EXPIRED;

	/**
	 * 더 이상 바뀌지 않는 상태인가. 진행률을 밀어 보내는 통로가 언제 연결을 닫을지를 이 값으로
	 * 정한다 — 진행률 100 으로 판단하지 않는 것은 실패·취소·만료도 끝인데 100 이 아니어서다.
	 */
	public boolean isTerminal() {
		return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == EXPIRED;
	}
}
