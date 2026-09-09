package com.gabolle.backend.recommendation.domain;

/**
 * 추천 Job 한 건의 상태.
 *
 * <p>🔴 <b>2026-09-04 이름 정정 (S15P21E201-192, 고지혁 님 지적).</b> 이 enum 은 원래
 * {@code QUEUED}·{@code CANCELED}(한 글자 L) 였다. 그런데 API 명세서(GABOLLE_API_명세서
 * v1.2, 2026-09-02 18:00 확정)가 실제로 잠근 값은 {@code PENDING}·{@code CANCELLED}
 * (두 글자 L) 다 — 이 파일의 옛 주석이 "GB-API-001 5장 그대로" 라고 적었던 것은 명세서가
 * 갱신되기 전 버전을 보고 쓴 것이었다. 코드를 정본으로 두지 않는다 — 명세서가 이긴다.
 * {@code EXPIRED} 는 명세서 네 종료 상태에 없던 값이지만, 유효 시간이 지난 Job 을 표현할
 * 자리가 따로 필요해 그대로 남긴다(명세서에 추가하는 것은 S15P21E201-572).
 *
 * <p>🔴 여기에 {@code FALLBACK} 이 <b>없는 것이 계약이다.</b> "대체 경로로 만들었다" 는
 * 상태가 아니라 {@link FallbackMode} 가 나타낸다. 둘을 한 칸에 섞으면 "성공했는데 기준선
 * 결과였다" 를 표현할 자리가 사라진다 — 그건 실패가 아니라 성공이다.
 *
 * <p>상태 전이는 단방향이다 (GB-API-001 5장).
 */
public enum JobStatus {
	/** 접수됐고 아직 시작하지 않았다. */
	PENDING,
	RUNNING,
	/** 결과를 만들어 반환했다. fallback 으로 만들었어도 여기다. */
	SUCCEEDED,
	/** 결과를 만들지 못했다. error_code 가 반드시 함께 남는다. */
	FAILED,
	/** 사용자가 취소했다 (JOB-02). */
	CANCELLED,
	/** 유효 시간이 지났다. */
	EXPIRED;

	/**
	 * 더 이상 바뀌지 않는 상태인가 — S15P21E201-193.
	 *
	 * <p>진행률을 밀어 보내는 통로가 언제 연결을 닫아야 하는지를 이 값으로 정한다. 진행률
	 * 100으로 판단하지 않는 이유는 실패·취소·만료도 끝인데 100이 아니기 때문이다. 상태 전이가
	 * 단방향이라(위 주석) 한 번 끝이면 다시 도는 일이 없다.
	 */
	public boolean isTerminal() {
		return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == EXPIRED;
	}
}
