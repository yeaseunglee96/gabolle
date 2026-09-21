package com.gabolle.backend.collection.domain;

/**
 * 컬렉션과 그 항목이 함께 쓰는 글자 칸 규칙. DB 가 거부할 값을 도메인에서 먼저 거부해
 * 400 으로 답한다 — 안 그러면 {@code DataIntegrityViolationException} 이 500 으로 나간다.
 *
 * <p>길이는 글자 수({@code codePointCount})로 센다. 이모지 하나는 자바 문자열에서 두 칸이지만
 * {@code varchar(100)} 은 한 글자로 세므로, 자바 길이로 재면 멀쩡한 값이 거부된다.
 *
 * <p>상한 값 자체는 여기 없다. 각 엔티티가 자기 열 폭을 상수로 들고 있고 이 도우미는 받아서
 * 쓰기만 한다 — 두 곳에 적으면 열을 넓히는 날 한쪽만 고쳐진다.
 */
final class TextFields {

	private TextFields() {
	}

	/**
	 * 비울 수 없는 한 줄짜리 칸. 앞뒤 공백은 떼고, 줄바꿈과 제어문자는 거부한다 — 이름이 두
	 * 줄이면 목록 카드가 밀려 그 아래 전부가 어긋난다.
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
	 * 안 적어도 되는 한 줄짜리 칸. 빈 문자열은 {@code null} 로 맞춘다 — 「안 적었다」와
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
	 * 안 적어도 되는 여러 줄 칸 — 설명과 메모. 줄바꿈과 탭은 허용하고 나머지 제어문자는
	 * 거부한다.
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
