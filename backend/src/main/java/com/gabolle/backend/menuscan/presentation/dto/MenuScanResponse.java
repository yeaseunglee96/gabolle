package com.gabolle.backend.menuscan.presentation.dto;

import java.util.List;

/**
 * 메뉴판에서 읽은 글자.
 *
 * <p>이 응답에는 «없다»를 말할 방법이 없다. {@code safe}·{@code hasAllergen}·{@code allergenFree}
 * 같은 칸을 아예 두지 않는다 — 있으면 언젠가 누군가 그리고, 모델이 못 읽은 낱말이 «없음»으로 보이면
 * 사람이 다친다.
 *
 * @param lines 읽은 줄. {@code allergenWords} 가 비었다는 것은 «그 줄에서 못 찾았다»이지
 *     «없다»가 아니다
 * @param unreadLineCount 사진에 글자가 있는데 못 읽은 줄 수. «전부 읽었다»와 «일부만 읽었다»가
 *     화면에서 갈려야 해서 있다. 이 값이 0 이어도 «전부 안전»이 아니다
 * @param evidenceStatus 언제나 {@code ESTIMATED}. 사진에서 읽은 값에 {@code VERIFIED} 를 붙이지
 *     않는다 — {@code place_feature.evidence_status} 와 같은 낱말이라 화면의 «추정값은 다르게
 *     그린다» 규칙에 그대로 얹힌다
 */
public record MenuScanResponse(List<Line> lines, int unreadLineCount, String evidenceStatus) {

	/** 사진에서 읽은 값은 언제나 추정이다. 이 칸에 다른 값이 들어갈 길을 두지 않는다. */
	public static final String ESTIMATED = "ESTIMATED";

	public static MenuScanResponse of(List<Line> lines, int unreadLineCount) {
		return new MenuScanResponse(lines, Math.max(unreadLineCount, 0), ESTIMATED);
	}

	/**
	 * @param text 그 줄에서 읽은 글자 그대로
	 * @param name 그 줄의 음식 이름만, 사진에 적힌 말 그대로. 번역하지 않는다. 음식 줄이 아니면
	 *     빈 문자열이다({@code null} 이 아니다). 원문으로 남는 이유는 둘이다 — 사용자가 직원에게
	 *     보여 주며 가리킬 값이고, 음식 그림을 언어와 무관하게 하나로 모을 열쇠다
	 * @param price 그 줄에 보이는 가격을 적힌 그대로. 숫자로 바꾸지 않는다 — 통화·표기가 가게마다
	 *     다르고, 바꾸는 순간 바꾼 값이 맞다고 주장하는 것이 된다. 안 보이면 빈 문자열
	 * @param translatedName 음식 이름만 옮긴 값 — 가격이 안 들어간다. {@code translatedText} 에는
	 *     가격이 섞여 있어, 가격 칸을 따로 그리는 화면이 같은 값을 두 번 그리게 된다. 음식 줄이
	 *     아니면 빈 문자열
	 * @param translatedText 앱이 요청한 언어로 옮긴 값. 요청 언어가 한국어이거나 언어를 안 보낸
	 *     요청이면 {@code text} 와 같은 값이 온다
	 * @param allergenWords 그 줄에서 보인 알레르기 관련 낱말. 비어 있으면 «못 찾았다»
	 */
	public record Line(String text, String name, String price, String translatedName,
			String translatedText, List<String> allergenWords) {
	}
}
