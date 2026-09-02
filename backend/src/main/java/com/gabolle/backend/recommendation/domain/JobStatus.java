package com.gabolle.backend.recommendation.domain;

/**
 * 추천 Job 한 건의 상태. <b>GB-API-001 5장 JobStatus enum 그대로다.</b> 값을 늘리거나 줄이면
 * 공개 응답 계약이 깨진다.
 *
 * <p>🔴 여기에 {@code FALLBACK} 이 <b>없는 것이 계약이다.</b> "대체 경로로 만들었다" 는
 * 상태가 아니라 {@link FallbackMode} 가 나타낸다. 둘을 한 칸에 섞으면 "성공했는데 기준선
 * 결과였다" 를 표현할 자리가 사라진다 — 그건 실패가 아니라 성공이다.
 *
 * <p>상태 전이는 단방향이다 (GB-API-001 5장).
 */
public enum JobStatus {
	/** 접수됐고 아직 시작하지 않았다. */
	QUEUED,
	RUNNING,
	/** 결과를 만들어 반환했다. fallback 으로 만들었어도 여기다. */
	SUCCEEDED,
	/** 결과를 만들지 못했다. error_code 가 반드시 함께 남는다. */
	FAILED,
	/** 사용자가 취소했다 (JOB-02). */
	CANCELED,
	/** 유효 시간이 지났다. */
	EXPIRED
}
