package com.gabolle.backend.common.privacy;

/**
 * 이메일을 응답이나 로그에 남길 때 가린다 — {@code traveler@example.com} → {@code t***@example.com}.
 * 도메인을 남기는 것은 오타를 낸 운영자가 자기 설정에서 어느 줄이 문제인지 찾게 하기 위해서다.
 *
 * <p>익명화가 아니다. 이미 그 사람의 이메일을 아는 사람에게는 가려진 값도 대조되므로, 로그에
 * 원문을 남기지 않기 위한 것일 뿐 권한 확인을 대신하지 않는다.
 */
public final class EmailMasker {

	private EmailMasker() {
	}

	/** 입력이 {@code null} 이거나 {@code @} 가 없으면 {@code null} 을 돌려준다 — 이메일이 아닌 값을 이메일처럼 보이게 만들지 않는다. */
	public static String mask(String email) {
		if (email == null || !email.contains("@")) {
			return null;
		}
		int at = email.indexOf('@');
		String local = email.substring(0, at);
		String head = local.length() > 1 ? local.substring(0, 1) + "***" : "*";
		return head + email.substring(at);
	}
}
