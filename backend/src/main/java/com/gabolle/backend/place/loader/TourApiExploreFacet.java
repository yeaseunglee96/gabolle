package com.gabolle.backend.place.loader;

import java.util.ArrayList;
import java.util.List;

/**
 * 관광공사 분류 → 로컬 탐색 여덟 갈래.
 *
 * <p>이름으로 맞히지 않고 원천의 콘텐츠 유형({@code contenttypeid})과 분류 코드
 * ({@code cat1}·{@code cat3})만 본다. 이름에 「야시장」이 들었다는 이유로 갈래를 정하면 이름에
 * 그 말이 없는 야시장은 영원히 안 나온다.
 *
 * <p>걷기길을 레포츠보다 먼저 본다. 원천은 문탠로드·해안누리길을 레포츠 아래 걷기길로 두는데,
 * 대분류만 보면 그것들이 실탄사격장과 같은 갈래가 된다. 자연 아래 해안 산책로도 같은 자리다.
 *
 * <p>야시장과 기념품샵은 비운다. 원천에 그것을 가리키는 코드도 유형도 없다. 그럴듯한 것으로
 * 채우지 않고, 그 두 줄은 화면에 0건으로 남는다.
 *
 * <p>전망대를 야경으로 옮긴 것만 번역이 아니라 판단이다. 원천은 "야경" 이라고 말한 적이 없지만
 * 부산에서 야경을 보러 가는 곳이 사실상 이 전망대들이고, 틀렸을 때의 대가가 작다 — 목록을 보면
 * 바로 보이고 그 행만 지우면 되돌아간다.
 */
public final class TourApiExploreFacet {

	/** 행사·공연·축제. 원천의 콘텐츠 유형이 곧 이 갈래다. */
	private static final String CONTENT_TYPE_FESTIVAL = "15";

	/** 레포츠 아래 걷기길. */
	private static final String CAT3_TRAIL = "A03022700";

	/** 자연 아래 해안 산책로. */
	private static final String CAT3_COAST_WALK = "A01010500";

	/** 쇼핑 아래 5일장. */
	private static final String CAT3_PERIODIC_MARKET = "A04010100";

	/** 쇼핑 아래 상설시장. */
	private static final String CAT3_PERMANENT_MARKET = "A04010200";

	/** 인문 아래 전망대. */
	private static final String CAT3_OBSERVATORY = "A02050600";

	private TourApiExploreFacet() {
	}

	/**
	 * 이 장소에 붙일 갈래 코드들. 해당하는 것이 없으면 빈 목록이고, 한 장소가 둘 이상을 받을 수
	 * 있다 — 축제는 콘텐츠 유형에서, 나머지는 분류 코드에서 오므로 겹칠 수 있다.
	 */
	public static List<String> of(String contentTypeId, String cat1, String cat3) {
		List<String> facets = new ArrayList<>(2);

		if (CONTENT_TYPE_FESTIVAL.equals(contentTypeId)) {
			facets.add("FESTIVAL");
		}

		// 걷는 길이 먼저다. 대분류만 보면 산책로가 실탄사격장과 같은 갈래가 된다.
		if (CAT3_TRAIL.equals(cat3) || CAT3_COAST_WALK.equals(cat3)) {
			facets.add("WALK");
		}
		else if ("A03".equals(cat1)) {
			facets.add("ACTIVITY");
		}
		else if ("A01".equals(cat1)) {
			facets.add("NATURE");
		}

		if (CAT3_PERIODIC_MARKET.equals(cat3) || CAT3_PERMANENT_MARKET.equals(cat3)) {
			facets.add("TRADITIONAL_MARKET");
		}
		if (CAT3_OBSERVATORY.equals(cat3)) {
			facets.add("NIGHT_VIEW");
		}

		return List.copyOf(facets);
	}
}
