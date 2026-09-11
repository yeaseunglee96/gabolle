package com.gabolle.backend.auth.service;

public interface EmailSender {

	void sendEmailVerification(String email, String verificationUrl);

	void sendPasswordReset(String email, String resetUrl);

	/**
	 * 운영자가 기록을 지웠다는 것을 작성자에게 알린다 — S15P21E201-794.
	 *
	 * <p>🔴 <b>신고 사유도 신고자도 싣지 않는다.</b> 사용자 수가 적은 서비스에서는 사유 한
	 * 가지만으로도 누가 신고했는지 좁혀지고, 그 추측이 맞든 틀리든 사람 사이의 일이 된다.
	 * 알리는 것은 "지워졌다" 와 "어느 기록인가" 둘이다.
	 *
	 * <p>기각(dismiss)에는 이 메일을 보내지 않는다. 사용자 눈에는 아무 일도 없었던 것이 맞다.
	 *
	 * @param excerpt 어느 기록인지 알아볼 수 있을 만큼의 앞부분. 호출자가 이미 잘라서 준다
	 */
	void sendStoryRemovedByModerator(String email, String excerpt);
}
