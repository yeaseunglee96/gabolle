package com.gabolle.backend.place.loader;

import java.util.Set;

import com.gabolle.backend.place.domain.AccommodationCategories;

/**
 * 관광공사 분류를 앱이 보내는 낱말로 옮긴다.
 *
 * <p>후보 필터가 사용자의 {@code CATEGORY} 답을 {@code place.category} 와 글자 그대로 비교한다.
 * 여기서 다른 낱말을 내면 그 갈래를 고른 사용자의 후보가 조용히 0 건이 된다. 앱이 쓰는 여섯은
 * {@code SEA_BEACH}·{@code CITY}·{@code CAFE_HEALING}·{@code CULTURE_TEMPLE}·{@code FOOD}·
 * {@code NATURE_WALK} 다.
 *
 * <p>이름으로 짐작하지 않고 원천이 스스로 매긴 {@code cat1} 을 쓴다. 자연은
 * {@code NATURE_WALK}(해수욕장만 {@code SEA_BEACH}), 인문은 {@code CULTURE_TEMPLE}, 쇼핑은
 * {@code CITY} 다. 음식은 장소 적재 자체를 안 한다.
 *
 * <p>레포츠는 비운다. 앱의 여섯 낱말 중 그것을 가리키는 것이 없고, 레포츠 대분류에는
 * 캠핑장·서핑학교와 실탄사격장이 함께 들어 있어 통째로 {@code NATURE_WALK} 에 넣으면 "자연
 * 산책" 을 고른 사용자에게 실내 사격장이 섞인다. 비워 두면 그 갈래에서 안 나올 뿐이고, 장소로는
 * 존재해서 필수 방문지 지정에는 쓸 수 있다.
 *
 * <p>🔴 다만 레포츠 안의 <b>걷기길</b>({@link #CAT3_TRAIL})만은 내보낸다. 2026-09-21 에 29곳을
 * 소분류로 다 세어 보니 <b>길 그 자체인 곳이 7곳</b>(갈맷길·해파랑길·문탠로드·구포무장애숲길·
 * 송도 구름산책로·해안누리길 몰운대길·미포정거장) 들어 있었고, 대분류에 묶여 같이 묻혀
 * 있었다 (S15P21E201-1386). 새 판단이 아니다 — {@link TourApiExploreFacet} 이 같은 소분류를
 * 이미 「산책」으로 가르고 있었고, 취향 축에서만 안 갈랐던 것이다.
 *
 * <p>야영장·캠핑 10곳은 <b>일부러 비운 채로 둔다.</b> 「낮에 들르는 곳」이 아니라 「자는 곳」이라
 * 숙소로 봐야 할 수도 있는데, 그것은 제품이 정할 말이라 별도로 다룬다. 나머지 12곳(골프장·
 * 아이스링크·실탄사격장 …)은 실내이거나 걷는 것이 아니라 계속 비운다. 더 잘게 나누려면
 * 소분류마다 사람이 판정해야 하는데 그것은 채점 기준을 정하는 일이다.
 *
 * <p>🔴 숙박도 2026-09-21 까지 같이 비어 있었고, 그동안 숙소 목록이 0건이었다. 이름·주소·좌표는
 * 65곳 전부 이미 들어와 있었고 이 칸 하나만 비어 있었다. 지금은
 * {@link AccommodationCategories} 가 정한 낱말을 낸다.
 *
 * <p>숙소 낱말은 위의 여섯과 <b>다른 사전</b>이다. 앱은 취향으로 이것을 보내지 않고, 숙소 지정만
 * {@code PlaceRepository.findByCategoryIn} 으로 따로 찾는다. 그래서 일반 후보 조회는 이 낱말을
 * 빼야 한다 — 안 빼면 갈래를 안 좁힌 요청에서 호텔이 관광지처럼 일정에 섞인다. 그 제외는
 * {@code PlaceCandidateQueryService} 가 한다.
 *
 * <p>해수욕장 소분류에 드는 곳은 몇 안 된다. 수집이 빠뜨린 것이 아니라 관광공사 「관광지」
 * 목록을 전부 받은 결과가 그렇다. 해안 산책로·항구·등대·섬까지 넓히면 숫자는 늘지만 「해변」 을
 * 고른 사람에게 등대가 나온다.
 *
 * <p>다만 바닷가를 걷는 길 둘은 {@link #COASTAL_WALK_CONTENT_IDS} 로 콕 집어 넣는다. 그 소분류
 * ({@code A01010500})에는 지질공원 성격이 큰 곳이 함께 있어 {@code cat3} 로는 못 가른다.
 */
final class TourApiCategory {

	/** 자연 안에서 해수욕장·해변. */
	private static final String CAT3_BEACH = "A01011200";

	/** 숙박 대분류. 호텔·모텔·게스트하우스·펜션이 전부 여기 하나로 들어온다. */
	private static final String CAT1_LODGING = "B02";

	/**
	 * 레포츠 안에서 걷기길. 갈맷길·해파랑길·문탠로드·해안누리길처럼 <b>길 그 자체</b>인 곳이다.
	 * 대분류는 레포츠라 비우는 것이 기본인데, 이 소분류만은 걷는 곳이라 예외로 내보낸다.
	 */
	private static final String CAT3_TRAIL = "A03022700";

	/**
	 * 숙소 낱말. 여섯과 달리 앱이 보내는 값이 아니라 조회 쪽이 정한 값이라 글자가 어긋나면
	 * 숙소 목록이 조용히 0건으로 돌아간다. 시험이 {@link AccommodationCategories#CODES} 와
	 * 같은지를 확인한다.
	 */
	private static final String LODGING = "LODGING";

	/**
	 * 소분류 {@code A01010500} 안에서 「바닷가를 걷는 길」인 것만. 같은 소분류의 나머지는
	 * 지질공원 성격이 커서 일부러 뺐다.
	 */
	private static final Set<String> COASTAL_WALK_CONTENT_IDS = Set.of("252561", "2822343");

	private TourApiCategory() {
	}

	/**
	 * 갈래 낱말. 옮길 낱말이 없으면 {@code null} 이고, 호출자는 그것을 정상으로 다뤄야 한다.
	 * 내는 값은 앱이 보내는 여섯이거나 {@link AccommodationCategories#CODES} 의 숙소 낱말이다.
	 * {@code contentId} 는 {@link #COASTAL_WALK_CONTENT_IDS} 콕 집기에만 쓴다.
	 */
	static String of(String contentId, String cat1, String cat3) {
		if (COASTAL_WALK_CONTENT_IDS.contains(contentId)) {
			return "SEA_BEACH";
		}
		if (cat1 == null) {
			return null;
		}
		// 걷기길이 먼저다. 대분류만 보면 산책로가 실탄사격장과 같은 갈래가 된다.
		if (CAT3_TRAIL.equals(cat3)) {
			return "NATURE_WALK";
		}
		return switch (cat1) {
			case "A01" -> CAT3_BEACH.equals(cat3) ? "SEA_BEACH" : "NATURE_WALK";
			case "A02" -> "CULTURE_TEMPLE";
			case "A04" -> "CITY";
			case CAT1_LODGING -> LODGING;
			// 레포츠의 나머지 — 앱의 낱말에 대응하는 것이 없다.
			default -> null;
		};
	}
}
