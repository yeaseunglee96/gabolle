package com.gabolle.backend.recommendation.domain;

/**
 * 이 결과가 <b>무엇을 재료로</b> 만들어졌는가 (S15P21E201-555).
 *
 * <p>🔴 {@link FallbackMode} 와 <b>다른 질문에 답한다.</b> 둘을 하나로 합치려다 말았는데,
 * 합치면 답할 수 없는 것이 생긴다.
 *
 * <table border="1">
 * <caption>두 값이 각각 답하는 것</caption>
 * <tr><th></th><th>묻는 것</th><th>예</th></tr>
 * <tr><td>{@code SourceMode}</td><td><b>무엇을</b> 보여줬나</td>
 *     <td>개인화 추천이었나, 편집자가 고른 목록이었나</td></tr>
 * <tr><td>{@link FallbackMode}</td><td><b>왜</b> 그것을 보여줬나</td>
 *     <td>모델이 매겼나, 규칙이 매겼나, 대체했나</td></tr>
 * </table>
 *
 * <p>이 둘이 갈리는 경우가 실제로 있다. 신규 계정에게 Editor's Pick 을 보여주는 것은
 * <b>정상 경로</b>다 — 아무것도 실패하지 않았고, 그 사용자에 대해 아는 것이 없으니 그것이
 * 맞는 답이다. 반면 엔진이 죽어서 Pick 을 보여준 것은 <b>사고</b>다. 화면에 나간 것은 둘 다
 * 같은 Pick 이지만, 뒤에서 봐야 하는 숫자는 정반대다 — 앞의 것이 늘어나는 것은 사용자가
 * 늘었다는 뜻이고, 뒤의 것이 늘어나는 것은 서비스가 고장났다는 뜻이다.
 *
 * <p>{@code SourceMode} 하나만 남기면 그 둘이 한 칸에 섞여 <b>장애율을 잴 수 없다.</b>
 * {@link FallbackMode} 하나만 남기면 "Pick 을 본 사람이 몇 명인가" 를 셀 수 없다.
 * 그래서 S15P21E201-555 의 작업 내용도 <i>"sourceMode·fallbackMode 로 구분한다"</i> 라고
 * 둘을 함께 적었고, 완료 기준 <i>"글로벌 Pick 과 개인화 추천을 분석에서 구분할 수 있다"</i>
 * 가 요구하는 것이 이 칸이다.
 */
public enum SourceMode {

	/**
	 * 이 사용자의 취향·제약으로 후보를 만들고 점수를 매겼다. 정상적인 추천이다.
	 */
	PERSONALIZED,

	/**
	 * 편집자가 미리 고른 목록({@code editorial_pick})을 그대로 보여줬다.
	 *
	 * <p>🔴 순위는 편집자가 정한 {@code pick_rank} 이고 점수로 다시 매기지 않는다. 다만
	 * <b>하드 제약은 그대로 적용된다</b> — Pick 이라고 해서 알레르기가 있는 식당을 통과시키지
	 * 않는다. 같은 {@code CandidateAssembler} 를 지나므로 그 판정이 자동으로 걸린다.
	 */
	EDITORIAL_PICK
}
