package com.gabolle.backend.place.loader;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 상가정보 상호명에서 "먹고 싶은 부산 음식 8종" 중 이 가게가 <b>실제로 파는 것</b>을 찾는다
 * — S15P21E201-448.
 *
 * <h2>🔴 {@link AppFoodVocabulary} 와 다른 질문에 답한다</h2>
 *
 * {@link AppFoodVocabulary#cuisineTags}가 붙이는 {@code CUISINE_TAG}는 취향 점수 매칭용이다
 * ({@code BaselineCandidateScorer}가 이미 쓰는 자리). 이 클래스는 그것과 <b>다른 서랍</b>
 * ({@code DESIRED_FOOD_TAG})에 넣는다 — "밀면이 먹고 싶다" 를 고르면 그 밀면집이 일정에
 * 들어가야 한다(F-PLN-05)는 검색·필터링 질문이지, 취향 점수가 아니다. 같은 서랍에 두면
 * 두 질문이 섞이고, -448 완료 기준("기존 표식은 건드리지 않는다")을 어기게 된다.
 *
 * <h2>8종 중 6종만 있다 — 나머지 둘은 지어내지 않는다</h2>
 *
 * 8종({@code frontend/src/plan/busanFoodCatalog.ts}): MILMYEON · PORK_SOUP · SSIAT_HOTTEOK ·
 * SEAFOOD · DONGNAE_PAJEON · BOKGUK · BUSAN_EOMUK · NAKGOPSAE.
 *
 * <p>🔴 <b>2026-09-16 실측</b> — 운영에 이미 실린 2,355곳({@code research/data/queue.ndjson},
 * S15P21E201-804)과 TourAPI 음식점 656곳({@code tourapi-busan.ndjson})의 상호명을 대조했다.
 *
 * <table border="1">
 * <caption>이름 매칭 개수 — 완료 기준(5곳 이상)과 대조</caption>
 * <tr><th>음식</th><th>낱말</th><th>2,355곳</th><th>TourAPI 656곳</th><th>판정</th></tr>
 * <tr><td>{@code BOKGUK}(복국)</td><td>"복국"</td><td>5</td><td>2</td>
 *     <td>✅ 2,355곳만으로 5곳 — 여기 있다</td></tr>
 * <tr><td>{@code SSIAT_HOTTEOK}(씨앗호떡)</td><td>"씨앗호떡"·"호떡"</td><td>0·1</td><td>0·1</td>
 *     <td>🔴 부족 — 없다</td></tr>
 * <tr><td>{@code DONGNAE_PAJEON}(동래파전)</td><td>"동래파전"·"파전"</td><td>1·4</td><td>0·0</td>
 *     <td>🔴 부족 — 없다</td></tr>
 * <tr><td>{@code BUSAN_EOMUK}(부산어묵)</td><td>"어묵"</td><td>0</td><td>0</td>
 *     <td>🔴 0곳 — 없다</td></tr>
 * <tr><td>{@code NAKGOPSAE}(낙곱새)</td><td>"낙곱새"·"낙곱"</td><td>0·0</td><td>0·0</td>
 *     <td>🔴 0곳 — 없다</td></tr>
 * </table>
 *
 * <p>씨앗호떡·부산어묵은 노점·전문점 성격이 강해 "식당" 위주로 뽑힌 이 표본에 잘 안 걸리는
 * 것으로 보인다(추측 — 확인 안 됨). {@code MILMYEON}·{@code PORK_SOUP}·{@code SEAFOOD} 는
 * 이미 {@link AppFoodVocabulary}가 이름·소분류로 잘 가르므로, 여기서는 <b>같은 로직을
 * 복제하지 않고</b> {@code BOKGUK} 하나만 더한다.
 *
 * <p>🔴 <b>나머지 넷은 상가정보 전체(53,716행, 서버 전용) 없이는 검증할 수 없다.</b>
 * 5곳을 못 채운 채로 넣으면 "판다고 표시했는데 실제로는 없는" 상태가 되므로, 채우지 않고
 * 이 표에 남긴다 — 다음 사람이 같은 것을 다시 재지 않게.
 */
public final class DesiredFoodVocabulary {

	/** {@code place_feature.feature_type}. */
	public static final String FEATURE_TYPE = "DESIRED_FOOD_TAG";

	/**
	 * 상호명에 이 낱말이 있으면 그 코드다. {@link AppFoodVocabulary#CUISINE_BY_NAME_WORD}와
	 * 같은 방식 — 상호명은 가게가 스스로 무엇을 파는지 적어 둔 것이라 지어낸 신호가 아니다.
	 */
	private static final Map<String, String> BY_NAME_WORD = Map.of(
			"복국", "BOKGUK");

	private DesiredFoodVocabulary() {
	}

	/**
	 * 이 가게에 붙일 {@code DESIRED_FOOD_TAG} 코드들. 없으면 빈 집합이다.
	 *
	 * @param name 상호명. 지점명은 안 본다 — 위치이지 음식이 아니다({@link AppFoodVocabulary}와 같다)
	 */
	public static Set<String> desiredFoodTags(String name) {
		Set<String> tags = new LinkedHashSet<>();
		String storeName = name == null ? "" : name;
		BY_NAME_WORD.forEach((word, code) -> {
			if (storeName.contains(word)) {
				tags.add(code);
			}
		});
		return tags;
	}
}
