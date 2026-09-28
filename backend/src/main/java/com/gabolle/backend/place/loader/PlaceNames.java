package com.gabolle.backend.place.loader;

import java.util.Arrays;
import java.util.List;

/**
 * 원천 이름에 세미콜론으로 붙은 여러 이름을 하나로 — S15P21E201-1637(사용자 결정 2026-09-25).
 *
 * <p>🔴 왜. 오픈스트리트맵은 옛 이름·다른 이름을 세미콜론으로 이어 적고(「선모텔;코리아나모텔」), 상가 자료에도 같은
 * 모양이 섞여 들어왔다(「오즈;Oddz」). 그대로 화면에 나갔다.
 *
 * <p>규칙: 앞 이름을 쓴다. 단 앞 이름이 <b>한 글자</b>면 뒤 이름을 쓴다 — 「구;경포횟집」의 「구」는 「예전(舊)」이라는
 * 머리말이지 이름이 아니다. 두 가게가 한 점에 합쳐진 것(마트+성당)은 이 규칙으로 못 푼다 — 적재기가 따로 거른다.
 */
public final class PlaceNames {

	private PlaceNames() {
	}

	/** 세미콜론이 없거나 {@code null} 이면 그대로. */
	public static String primary(String raw) {
		if (raw == null || raw.indexOf(';') < 0) {
			return raw;
		}
		List<String> parts = Arrays.stream(raw.split(";")).map(String::strip).filter(part -> !part.isEmpty()).toList();
		if (parts.isEmpty()) {
			return raw.strip();
		}
		String first = parts.get(0);
		if (first.codePointCount(0, first.length()) == 1 && parts.size() > 1) {
			return parts.get(1);
		}
		return first;
	}
}
