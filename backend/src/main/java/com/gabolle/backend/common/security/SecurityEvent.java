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

	/**
	 * 🔴 <b>이미 잠긴</b> 계정으로 로그인을 또 시도했다.
	 *
	 * <p>{@link #AUTH_ACCOUNT_LOCKED} 는 잠기는 <b>순간 한 번만</b> 남는다. 그 뒤로 계속
	 * 두드리는 시도는 어디에도 안 남아서, 잠금이 공격을 실제로 막고 있는지 아니면 공격자가
	 * 포기했는지 구분할 수 없었다. 잠긴 뒤의 시도 횟수가 그 구분의 유일한 근거다.
	 */
	AUTH_LOCKED_ACCOUNT_ATTEMPT("이미 잠긴 계정으로 로그인을 시도했다"),

	/**
	 * 허용 목록에 없는 redirect URI 로 소셜 로그인을 요청했다.
	 *
	 * <p>이것은 오타가 아니라 대개 공격이다 — 성공하면 우리 서버가 발급한 표를 남의 주소로
	 * 보내게 된다. 형식 오류(400)와 같은 층에서 거부되므로 상태 코드만 보는 로깅에는 안 잡혔다.
	 */
	AUTH_OAUTH_REDIRECT_REJECTED("허용 목록에 없는 redirect URI 로 소셜 로그인을 요청했다"),

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
