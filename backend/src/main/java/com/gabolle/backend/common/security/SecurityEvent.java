package com.gabolle.backend.common.security;

/**
 * 보안 관측(S15P21E201-682)이 구조화된 로그로 남기는 사건의 종류.
 *
 * <p>{@link SecurityEventLogger} 가 이 값을 로그 줄의 {@code event=} 필드에 그대로 싣는다. 값 이름을
 * 바꾸면 그 이름으로 로그를 걸러 보던 대시보드·알림 규칙이 조용히 끊기므로, 이름은 한 번 정하면
 * 되도록 유지한다.
 */
public enum SecurityEvent {

	/** 로그인 시도에서 비밀번호가 틀렸다. */
	AUTH_LOGIN_FAILURE("비밀번호가 틀렸다"),

	/** 연속 실패 횟수가 임계치에 닿아 계정이 잠겼다 (S15P21E201-421). */
	AUTH_ACCOUNT_LOCKED("연속 실패로 잠겼다"),

	/** 토큰이 없거나 유효하지 않아 401이 나갔다. */
	AUTH_TOKEN_REJECTED("토큰이 없거나 유효하지 않아 401이 나갔다"),

	/** 인증은 됐는데 권한이 없어 403이 나갔다. */
	AUTHZ_DENIED("인증은 됐는데 권한이 없어 403이 나갔다"),

	/**
	 * 🔴 이미 쓴 갱신 표가 <b>유예 시간 안에</b> 다시 와서 도난이 아니라 정상 경쟁으로
	 * 처리했다 (S15P21E201-723). 세션을 폐기하지 않고 새 표를 다시 발급했다.
	 *
	 * <p>이것이 잦아지면 클라이언트가 갱신을 중복 발사하고 있다는 신호다 — 오류는 아니지만
	 * 봐야 하는 값이라 {@link #AUTH_TOKEN_REJECTED} 와 섞지 않고 따로 둔다.
	 */
	AUTH_REFRESH_RACE("이미 쓴 갱신 표가 유예 시간 안에 다시 왔다 — 정상 경쟁으로 처리했다");

	private final String description;

	SecurityEvent(String description) {
		this.description = description;
	}

	public String getDescription() {
		return this.description;
	}
}
