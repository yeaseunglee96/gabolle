package com.gabolle.backend.facility.loader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 원천 글을 화면에 보일 수 있게 다듬는다 (S15P21E201-2052). 공공 자료의 글에는 줄바꿈·겹공백·HTML 글자 코드({@code &#xD;} 같은
 * 것)가 섞여 있다. 값을 바꾸는 것이 아니라 <b>모양만</b> 정리한다.
 */
final class FacilityText {

	private static final Pattern ENTITY = Pattern.compile("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);");

	private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0]+");

	/** 글자나 숫자가 하나도 없는 글(「.」「-」「/」 …)은 내용이 아니다. */
	private static final Pattern HAS_CONTENT = Pattern.compile("[\\p{L}\\p{N}]");

	private FacilityText() {
	}

	/** 글자 코드를 풀고 공백(줄바꿈 포함)을 한 칸으로 줄인 뒤 다듬는다. 비었으면 {@code null}. */
	static String clean(String raw) {
		if (raw == null) {
			return null;
		}
		String text = WHITESPACE.matcher(decodeEntities(raw)).replaceAll(" ").trim();
		return text.isEmpty() ? null : text;
	}

	/** 메모 같은 자유 글 — 내용(글자·숫자)이 없으면 {@code null}. 길면 {@code max} 글자에서 자른다. */
	static String cleanNote(String raw, int max) {
		String text = clean(raw);
		if (text == null || !HAS_CONTENT.matcher(text).find()) {
			return null;
		}
		return cut(text, max);
	}

	/**
	 * 주소. 「-」 하나는 주소가 없다는 뜻이고, 번지 자리에 「-」만 적힌 끝(「남구 대연동 -」)은 번지를 모른다는 뜻이라 떼어 낸다.
	 */
	static String cleanAddress(String raw, int max) {
		String text = clean(raw);
		if (text == null) {
			return null;
		}
		text = text.replaceFirst("\\s+[-.]$", "");
		if (!HAS_CONTENT.matcher(text).find()) {
			return null;
		}
		return cut(text, max);
	}

	static String cut(String value, int max) {
		if (value == null) {
			return null;
		}
		return value.length() <= max ? value : value.substring(0, max).trim();
	}

	/** {@code &lt;} {@code &gt;} {@code &amp;} {@code &quot;} {@code &apos;} {@code &nbsp;} 와 숫자 코드({@code &#xD;} {@code &#13;})를 푼다. */
	static String decodeEntities(String raw) {
		if (raw.indexOf('&') < 0) {
			return raw;
		}
		Matcher matcher = ENTITY.matcher(raw);
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			String replacement = decode(matcher.group(1));
			matcher.appendReplacement(out, Matcher.quoteReplacement(replacement != null ? replacement : matcher.group()));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	private static String decode(String body) {
		if (body.startsWith("#")) {
			try {
				int code = body.length() > 1 && (body.charAt(1) == 'x' || body.charAt(1) == 'X')
						? Integer.parseInt(body.substring(2), 16)
						: Integer.parseInt(body.substring(1));
				return Character.isValidCodePoint(code) ? new String(Character.toChars(code)) : null;
			}
			catch (NumberFormatException broken) {
				return null;
			}
		}
		return switch (body) {
			case "lt" -> "<";
			case "gt" -> ">";
			case "amp" -> "&";
			case "quot" -> "\"";
			case "apos" -> "'";
			case "nbsp" -> " ";
			default -> null;
		};
	}
}
