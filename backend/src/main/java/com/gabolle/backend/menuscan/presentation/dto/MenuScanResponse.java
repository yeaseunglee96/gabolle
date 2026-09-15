package com.gabolle.backend.menuscan.presentation.dto;

import java.util.List;

/**
 * 메뉴판에서 <b>읽은 글자</b> — S15P21E201-1025.
 *
 * <h2>🔴 이 응답에는 「없다」를 말할 방법이 없다. 그것이 설계의 전부다</h2>
 *
 * 티켓 {@code -86} 은 «메뉴판을 찍으면 알레르기 주의를 볼 수 있다» 이다. 모델이 사진에서
 * 「새우」를 못 읽었는데 화면이 <b>「갑각류 없음」</b> 이라고 말하면 <b>사람이 다친다.</b>
 * 2026-09-16 에 고친 {@code -996}(안 알아본 것을 「확인됨」으로 그리던 알레르기 버그)과
 * <b>글자 하나 다르지 않다.</b>
 *
 * <p>그래서 이 응답에는 {@code safe}·{@code hasAllergen}·{@code allergenFree} 같은 칸이
 * <b>아예 없다.</b> 있으면 언젠가 누군가 그린다.
 *
 * @param lines 읽은 줄. {@code allergenWords} 가 비었다는 것은 <b>「그 줄에서 못 찾았다」</b>
 *     이지 <b>「없다」가 아니다</b>
 * @param unreadLineCount 🔴 <b>사진에 글자가 있는데 못 읽은 줄 수.</b> 「전부 읽었다」와
 *     「일부만 읽었다」가 화면에서 갈려야 해서 있다.
 *     <p>🔴 <b>이 값이 0 이어도 「전부 안전」이 아니다.</b> 읽은 글자 안에서 못 찾았을 뿐이다
 * @param evidenceStatus 언제나 {@code ESTIMATED} 다. 사진에서 읽은 값에 {@code VERIFIED} 를
 *     <b>붙이지 않는다</b> — 이 저장소의 {@code place_feature.evidence_status} 와 같은 낱말을
 *     써서, 화면이 이미 가진 «추정값은 다르게 그린다» 규칙에 그대로 얹힌다
 */
public record MenuScanResponse(List<Line> lines, int unreadLineCount, String evidenceStatus) {

	/** 사진에서 읽은 값은 언제나 추정이다. 이 칸에 다른 값이 들어갈 길을 두지 않는다. */
	public static final String ESTIMATED = "ESTIMATED";

	public static MenuScanResponse of(List<Line> lines, int unreadLineCount) {
		return new MenuScanResponse(lines, Math.max(unreadLineCount, 0), ESTIMATED);
	}

	/**
	 * @param text 그 줄에서 읽은 글자 그대로
	 * @param allergenWords 그 줄에서 <b>보인</b> 알레르기 관련 낱말. 비어 있으면 «못 찾았다»
	 */
	public record Line(String text, List<String> allergenWords) {
	}
}
