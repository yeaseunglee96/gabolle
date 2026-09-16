package com.gabolle.backend.collection.domain;

/**
 * 컬렉션과 그 항목이 함께 쓰는 글자 칸 규칙 — S15P21E201-1037.
 *
 * <p>규칙은 하나다 — <b>DB 가 거부할 값을 도메인에서 먼저 거부한다.</b>
 *
 * <p>지금까지 이 두 엔티티는 「비었는가」만 보고 「얼마나 긴가」를 안 봤다. 열은
 * {@code varchar} 로 폭이 정해져 있어서, 긴 값은 자바 검사를 전부 통과한 뒤 PostgreSQL 에서
 * 거부되고 그 {@code DataIntegrityViolationException} 을 잡는 자리가 없어 <b>500</b> 으로
 * 나갔다. 400 이어야 할 것이 500 으로 나가면 부르는 쪽은 자기 입력이 문제라는 것을 모르고
 * 재시도한다 — 그리고 서버 오류 그래프에 남 탓할 자국이 쌓인다.
 *
 * <p>길이는 <b>글자 수</b>로 센다({@code codePointCount}). 이모지 하나는 자바 문자열에서
 * 두 칸을 차지하지만 {@code varchar(100)} 은 한 글자로 세므로, 자바 길이로 재면 DB 가 받아
 * 줄 멀쩡한 이름이 거부된다. {@code Trip.rename} 이 같은 이유로 같은 방식을 쓴다.
 *
 * <p>상한 값 자체는 여기 없다. 각 엔티티가 자기 열 폭을 상수로 들고 있고 이 도우미는
 * 받아서 쓰기만 한다 — 폭을 두 곳에 적으면 마이그레이션에서 열을 넓히는 날 한쪽만 고쳐진다.
 */
final class TextFields {

	private TextFields() {
	}

	/**
	 * 비울 수 없는 <b>한 줄짜리</b> 칸. 앞뒤 공백은 떼고, 줄바꿈과 제어문자는 거부한다.
	 *
	 * <p>이름이 두 줄이면 목록 카드가 밀려 그 아래 전부가 어긋난다. 「제목 없음」으로
	 * 대신 채우지 않는 이유는 {@link Collection#of} 의 주석에 있다.
	 */
	static String requiredLine(String value, String label, int maxLength) {
		String trimmed = (value == null) ? "" : value.trim();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException(label + "은(는) 비울 수 없다");
		}
		rejectControlCharacters(trimmed, label, false);
		return withinLength(trimmed, label, maxLength);
	}

	/**
	 * 안 적어도 되는 <b>한 줄짜리</b> 칸. 빈 문자열은 {@code null} 로 맞춘다 — 「안 적었다」와
	 * 「빈 칸을 적었다」는 화면에서 구분할 수 없는 같은 것이다.
	 */
	static String optionalLine(String value, String label, int maxLength) {
		String trimmed = (value == null) ? "" : value.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		rejectControlCharacters(trimmed, label, false);
		return withinLength(trimmed, label, maxLength);
	}

	/**
	 * 안 적어도 되는 <b>여러 줄</b> 칸 — 설명과 메모.
	 *
	 * <p>줄바꿈과 탭은 허용한다. 사용자가 메모를 여러 줄로 적는 것은 정상이고, 그것까지
	 * 막으면 붙여넣기가 통째로 거부된다. 나머지 제어문자는 여전히 거부한다 — 보이지 않는
	 * 글자가 섞이면 같은 메모가 화면마다 다르게 보인다.
	 */
	static String optionalText(String value, String label, int maxLength) {
		String trimmed = (value == null) ? "" : value.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		rejectControlCharacters(trimmed, label, true);
		return withinLength(trimmed, label, maxLength);
	}

	private static void rejectControlCharacters(String trimmed, String label, boolean allowLineBreaks) {
		boolean hasForbidden = trimmed.codePoints().anyMatch((codePoint) -> {
			if (!Character.isISOControl(codePoint)) {
				return false;
			}
			return !(allowLineBreaks && (codePoint == '\n' || codePoint == '\r' || codePoint == '\t'));
		});
		if (hasForbidden) {
			throw new IllegalArgumentException(allowLineBreaks
					? label + "에 보이지 않는 제어문자를 넣을 수 없다"
					: label + "에 줄바꿈이나 제어문자를 넣을 수 없다");
		}
	}

	private static String withinLength(String trimmed, String label, int maxLength) {
		int length = trimmed.codePointCount(0, trimmed.length());
		if (length > maxLength) {
			throw new IllegalArgumentException(
					label + "은(는) " + maxLength + "자를 넘을 수 없다: " + length + "자");
		}
		return trimmed;
	}
}
