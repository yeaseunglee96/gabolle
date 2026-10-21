package com.gabolle.backend.placesuggestion.application;

import java.text.Normalizer;
import java.util.Locale;

/**
 * 두 장소 이름이 같은 곳을 가리키는가 — 중복 제안 거르기(S15P21E201-2031).
 *
 * <p>띄어쓰기·문장부호·대소문자를 지운 뒤, 같거나 한쪽이 다른 쪽을 품거나(「광안리해수욕장」 ⊃ 「광안리」 는 아님 — 짧은
 * 쪽이 두 글자 이하면 품기로 치지 않는다), 편집 거리가 짧은 쪽 길이의 1/4 이하면 같다고 본다. 위치(150m)와 함께
 * 쓰므로 이름만으로 판정하지 않는다.
 */
public final class NameSimilarity {

	private NameSimilarity() {
	}

	public static boolean similar(String a, String b) {
		String x = normalize(a);
		String y = normalize(b);
		if (x.isEmpty() || y.isEmpty()) {
			return false;
		}
		if (x.equals(y)) {
			return true;
		}
		String shorter = x.length() <= y.length() ? x : y;
		String longer = shorter == x ? y : x;
		if (shorter.length() >= 3 && longer.contains(shorter)) {
			return true;
		}
		return distance(x, y) * 4 <= shorter.length();
	}

	static String normalize(String s) {
		if (s == null) {
			return "";
		}
		String n = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		return n.replaceAll("[\\s\\p{Punct}·（）]", "");
	}

	private static int distance(String a, String b) {
		int[] prev = new int[b.length() + 1];
		int[] cur = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) {
			prev[j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			cur[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
			}
			int[] t = prev;
			prev = cur;
			cur = t;
		}
		return prev[b.length()];
	}
}
