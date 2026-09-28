package com.gabolle.backend.itinerary.domain;

import java.util.Set;

/**
 * 일정에서 뺄 때의 이유 코드 (S15P21E201-1689). 계획서(bigData/docs/RECOMMENDATION-DATA-COLLECTION-P0.md 8.2)의 운영 사유 어휘를
 * 그대로 쓴다. 앱은 이 가운데 다섯을 쓴다 — 가 봤어요 {@code ALREADY_VISITED} · 취향 아님 {@code NOT_INTERESTED} · 멀어요
 * {@code TOO_FAR} · 문 닫음 {@code CLOSED} · 그냥 {@code OTHER}. 건너뛰면 칸을 비운다.
 *
 * <p>코드로 받는 까닭 — 이 이유는 뺀 장소 기록과 빼기 이벤트에 남아 나중에 세어진다. 자유 글자면 「멀어요」·「멀어서」·
 * 「too far」가 따로 세진다. 목록에 없는 값은 400 이다. 비어 있으면(이미 나간 앱은 이 칸을 안 보낸다) 받는다.
 */
public final class RemovalReasons {

	public static final Set<String> CODES = Set.of(
			"NOT_INTERESTED", "ALREADY_VISITED", "WRONG_CATEGORY", "TOO_CROWDED", "TOO_TOURISTY", "TOO_EXPENSIVE",
			"TOO_FAR", "CLOSED", "BAD_WEATHER", "NO_TIME", "TRANSPORT_PROBLEM", "ACCESSIBILITY_PROBLEM",
			"DATA_INCORRECT", "OTHER");

	private RemovalReasons() {
	}

	/** 비어 있거나 목록에 있는 코드면 참. */
	public static boolean isKnownOrEmpty(String reason) {
		return reason == null || reason.isBlank() || CODES.contains(reason);
	}
}
