package com.gabolle.backend.place.loader;

/**
 * 관광공사 분류를 <b>앱이 보내는 낱말</b>로 옮긴다 — S15P21E201-854.
 *
 * <h2>🔴 앱이 보내는 낱말만 낸다</h2>
 * {@code BaselineCandidateTranslator} 가 사용자의 {@code CATEGORY} 답을 {@code place.category} 와
 * <b>글자 그대로</b> 비교한다. 여기서 다른 낱말을 내면 그 갈래를 고른 사용자의 후보가 조용히
 * 0 건이 된다 — {@code SbizPlaceLoader} 주석이 같은 함정을 적어 뒀다. 앱이 쓰는 여섯은
 * {@code SEA_BEACH}·{@code CITY}·{@code CAFE_HEALING}·{@code CULTURE_TEMPLE}·{@code FOOD}·
 * {@code NATURE_WALK} 다.
 *
 * <h2>무엇을 근거로 옮기나 — 원천이 스스로 매긴 대분류</h2>
 * 이름으로 짐작하지 않고 {@code cat1} 을 쓴다. 2026-09-11 부산 656곳 실측 분포다.
 *
 * <table border="1">
 * <caption>cat1 → 앱 낱말</caption>
 * <tr><th>cat1</th><th>뜻</th><th>곳</th><th>넣는 값</th></tr>
 * <tr><td>A01</td><td>자연</td><td>25</td><td>{@code SEA_BEACH} (해수욕장만) · 나머지 {@code NATURE_WALK}</td></tr>
 * <tr><td>A02</td><td>인문(문화·예술·역사)</td><td>162</td><td>{@code CULTURE_TEMPLE}</td></tr>
 * <tr><td>A04</td><td>쇼핑</td><td>47</td><td>{@code CITY}</td></tr>
 * <tr><td>A03</td><td>레포츠</td><td>29</td><td>🔴 <b>비운다</b></td></tr>
 * <tr><td>B02</td><td>숙박</td><td>65</td><td>🔴 <b>비운다</b></td></tr>
 * <tr><td>A05</td><td>음식</td><td>328</td><td>적재 자체를 안 한다 — {@link TourApiPlaceReader}</td></tr>
 * </table>
 *
 * <h2>🔴 레포츠와 숙박을 비우는 이유</h2>
 * 앱의 여섯 낱말 중 그것을 가리키는 것이 없다. 실측한 레포츠 29곳에는 캠핑장·서핑학교와
 * 아이스링크·실탄사격장이 <b>같은 대분류에</b> 섞여 있다 — 통째로 {@code NATURE_WALK} 에 넣으면
 * "자연 산책" 을 고른 사용자에게 실내 사격장이 섞이고, 그때 사용자는 추천이 고장 난 것으로 본다.
 * 비워 두면 그 갈래에서 안 나올 뿐이고, <b>장소로는 존재해서</b> 숙소 지정·필수 방문지 지정에는
 * 쓸 수 있다. 비운 칸을 그럴듯한 값으로 채우지 않는다는 이 저장소의 규칙과 같은 자리다.
 *
 * <p>더 잘 나누려면 {@code cat3} 단위로 봐야 한다(레포츠 안에 「무장애숲길」·「문탠로드」·
 * 「구름산책로」 같은 걷는 길이 실제로 있다). 그것은 <b>채점 기준</b>이라 장효준과 상의할 일로
 * 두었다({@code S15P21E201-106} 계열) — 여기서 혼자 정하면 그 결정이 코드에 박히고 곧 계약이 된다.
 *
 * <h2>🔴 바다가 두 곳뿐이다 — 이것이 사실이다</h2>
 * 자연 25곳 중 해수욕장 대분류({@code A01011200})는 <b>둘</b>이다(감지해변 · 부산 송도해수욕장).
 * 해운대·광안리가 이 목록에 없다 — 수집이 빠뜨린 것이 아니라 관광공사의 부산 「관광지」 목록
 * 138곳을 전부 받은 결과가 그렇다(쪽수 138/138).
 *
 * <p>그래서 {@code SEA_BEACH} 를 고른 사용자의 후보는 0 건에서 <b>두 곳</b>이 된다. 적지만
 * 0 과 2 는 다르다. 해안 산책로·항구·등대·섬 6곳을 여기 넣으면 숫자는 늘지만 「해변」 을 고른
 * 사람에게 등대가 나온다 — 넓히는 것도 채점 기준 결정이라 같이 미뤘다.
 */
final class TourApiCategory {

	/** 자연 안에서 해수욕장·해변. 실측에서 감지해변·부산 송도해수욕장 둘이 여기다. */
	private static final String CAT3_BEACH = "A01011200";

	private TourApiCategory() {
	}

	/**
	 * 앱이 보내는 갈래 낱말. 옮길 낱말이 없으면 {@code null} 이다 — <b>호출자는 그것을 정상으로
	 * 다뤄야 한다.</b>
	 */
	static String of(String cat1, String cat3) {
		if (cat1 == null) {
			return null;
		}
		return switch (cat1) {
			case "A01" -> CAT3_BEACH.equals(cat3) ? "SEA_BEACH" : "NATURE_WALK";
			case "A02" -> "CULTURE_TEMPLE";
			case "A04" -> "CITY";
			// A03 레포츠 · B02 숙박 — 앱의 낱말에 대응하는 것이 없다. 위 javadoc 참고.
			default -> null;
		};
	}
}
