package com.gabolle.backend.common.security;

/**
 * 보안 관측이 구조화된 로그로 남기는 사건의 종류. 값 이름이 로그 줄의 {@code event=} 필드에 그대로
 * 실리므로, 이름을 바꾸면 그 이름으로 걸러 보던 대시보드·알림 규칙이 조용히 끊긴다.
 */
public enum SecurityEvent {

	/** 로그인 시도에서 비밀번호가 틀렸다. */
	AUTH_LOGIN_FAILURE("비밀번호가 틀렸다"),

	/** 연속 실패 횟수가 임계치에 닿아 계정이 잠겼다. 잠기는 순간 한 번만 남는다. */
	AUTH_ACCOUNT_LOCKED("연속 실패로 잠겼다"),

	/**
	 * 이미 잠긴 계정으로 로그인을 또 시도했다. {@link #AUTH_ACCOUNT_LOCKED} 는 잠기는 순간에만 남으므로,
	 * 잠금이 공격을 막고 있는지 공격자가 포기했는지는 이 값으로만 구분한다.
	 */
	AUTH_LOCKED_ACCOUNT_ATTEMPT("이미 잠긴 계정으로 로그인을 시도했다"),

	/**
	 * 허용 목록에 없는 redirect URI 로 소셜 로그인을 요청했다. 오타가 아니라 대개 공격이다 — 성공하면
	 * 우리 서버가 발급한 표를 남의 주소로 보내게 된다.
	 */
	AUTH_OAUTH_REDIRECT_REJECTED("허용 목록에 없는 redirect URI 로 소셜 로그인을 요청했다"),

	/** 토큰이 없거나 유효하지 않아 401이 나갔다. */
	AUTH_TOKEN_REJECTED("토큰이 없거나 유효하지 않아 401이 나갔다"),

	/** 인증은 됐는데 권한이 없어 403이 나갔다. */
	AUTHZ_DENIED("인증은 됐는데 권한이 없어 403이 나갔다"),

	/**
	 * 이미 쓴 갱신 표가 유예 시간 안에 다시 와서 도난이 아니라 정상 경쟁으로 처리했다 — 세션을 폐기하지
	 * 않고 새 표를 다시 발급했다. 잦아지면 클라이언트가 갱신을 중복 발사한다는 신호다.
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
