package com.gabolle.backend.common.privacy;

/**
 * 이메일을 응답이나 로그에 남길 때 가린다 — {@code traveler@example.com} → {@code t***@example.com}.
 *
 * <p>같은 폴더의 {@link SensitivePayloadGuard} 는 추천 로그에 개인정보가 <b>들어가는 것을
 * 거부</b>한다. 하지만 거부만으로는 안 되는 자리가 있다. 사용자에게 "어느 계정에 붙는 표인지"
 * 를 알려 줘야 하거나, 운영자에게 "배포 설정의 어느 줄이 문제인지" 를 알려 줘야 할 때다.
 * 그때 쓰는 것이 이 규칙이다 — 값을 지우지 않고, 사람이 자기 계정을 알아볼 만큼만 남긴다.
 *
 * <p>🔴 가리는 폭이 이 정도인 이유. 로컬 부분(@ 앞)을 첫 글자만 남기는 것은 이 저장소가 이미
 * 소셜 로그인 응답에서 쓰던 규칙이고, 그대로 옮겨 왔다. 도메인(@ 뒤)은 남긴다 — 회사 도메인
 * 하나만으로는 특정 개인을 지목할 수 없고, 남기지 않으면 오타를 낸 운영자가 자기 설정에서
 * 어느 줄이 문제인지 찾을 수 없다.
 *
 * <p>이것은 익명화가 아니다. 이미 그 사람의 이메일을 아는 사람에게는 가려진 값도 대조된다.
 * 그래서 이 규칙은 <b>로그·오류 메시지에 원문을 남기지 않기 위한 것</b>이고, 권한 확인을
 * 대신하지 않는다.
 */
public final class EmailMasker {

	private EmailMasker() {
	}

	/**
	 * @param email 원문 이메일
	 * @return 가린 값. 로컬 부분이 한 글자면 {@code *@...} 다. {@code null} 이거나 {@code @} 가 없으면
	 *         {@code null} — 이메일이 아닌 값을 이메일처럼 보이게 만들지 않는다.
	 */
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
