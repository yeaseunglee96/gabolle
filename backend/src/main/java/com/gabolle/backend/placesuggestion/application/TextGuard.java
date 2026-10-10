package com.gabolle.backend.placesuggestion.application;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 제안 글에 넣으면 안 되는 것 — 욕설, 주소 링크, 메신저 광고(S15P21E201-2031).
 *
 * <p>🔴 이 저장소에는 글을 걸러 주는 공용 장치가 없다(기록 신고는 사후 신고다). 제안은 운영자가 승인하기 전에는 아무에게도
 * 안 보이므로 마지막 문은 사람이다. 여기서는 뻔한 것만 미리 막아 운영자가 읽을 양을 줄인다 — 완벽한 필터가 아니다.
 * 전화번호는 따로 받는 칸이 있으므로 이유·이름 칸의 연락처는 광고로 본다.
 */
final class TextGuard {

	private static final List<String> WORDS = List.of("씨발", "시발", "ㅅㅂ", "병신", "ㅂㅅ", "개새끼", "좆", "존나", "fuck",
			"shit", "bitch", "카톡", "오픈채팅", "텔레그램", "카지노", "바카라");

	private static final Pattern LINK = Pattern.compile("(https?://|www\\.|\\.com\\b|\\.kr\\b|\\.net\\b)");

	private static final Pattern PHONE = Pattern.compile("\\d{2,4}[-\\s.]?\\d{3,4}[-\\s.]?\\d{4}");

	private TextGuard() {
	}

	static boolean blocked(String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		String n = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		String squeezed = n.replaceAll("\\s", "");
		for (String w : WORDS) {
			if (squeezed.contains(w)) {
				return true;
			}
		}
		return LINK.matcher(n).find() || PHONE.matcher(n).find();
	}
}
