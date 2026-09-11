package com.gabolle.backend.place.loader;

import java.util.ArrayList;
import java.util.List;

/**
 * 관광공사 분류 → 로컬 탐색 여덟 갈래 — S15P21E201-474.
 *
 * <h2>왜 이것이 없으면 화면이 빈다</h2>
 * {@code /explore} 아코디언은 {@code place_feature} 의 {@code INTEREST_TAG} 표식으로 갈래를
 * 가른다({@link com.gabolle.backend.place.domain.InterestTagCode}). 그런데 지금까지 그 자리에
 * 값을 쓰는 적재기가 둘뿐이고 둘 다 <b>다른 어휘</b>를 쓴다 — 상가정보는 {@code FOOD}와
 * {@code CAFE_HEALING}, 관광공사 장소 적재는 {@code SEA_BEACH}·{@code CITY}·
 * {@code CULTURE_TEMPLE}·{@code NATURE_WALK} 다. 여덟 갈래와 겹치는 값이 하나도 없어서
 * 운영에서 여덟 줄이 전부 0건이었다(2026-09-11 진미리 실사용 확인).
 *
 * <h2>원천이 스스로 말한 것만 옮긴다</h2>
 * 이름으로 맞히지 않는다. {@code InterestTagCode} 주석이 같은 것을 적어 뒀다 — "남포동 야시장"
 * 이라는 이름에 야시장이 들어 있다는 이유로 갈래를 정하면 이름에 그 말이 없는 야시장은 영원히
 * 안 나온다. 여기서 쓰는 것은 원천의 콘텐츠 유형({@code contenttypeid})과 분류 코드
 * ({@code cat1}·{@code cat3})뿐이다.
 *
 * <table border="1">
 * <caption>2026-09-11 수집본 실측</caption>
 * <tr><th>갈래</th><th>근거</th><th>곳</th></tr>
 * <tr><td>{@code TRADITIONAL_MARKET}</td><td>{@code A04010100} 5일장 · {@code A04010200} 상설시장</td><td>33</td></tr>
 * <tr><td>{@code NATURE}</td><td>{@code cat1=A01} 자연 (산책로 제외)</td><td>22</td></tr>
 * <tr><td>{@code ACTIVITY}</td><td>{@code cat1=A03} 레포츠 (걷기길 제외)</td><td>22</td></tr>
 * <tr><td>{@code FESTIVAL}</td><td>{@code contenttypeid=15} 행사·공연·축제</td><td>14</td></tr>
 * <tr><td>{@code WALK}</td><td>{@code A03022700} 걷기길 · {@code A01010500} 해안 산책로</td><td>10</td></tr>
 * <tr><td>{@code NIGHT_VIEW}</td><td>{@code A02050600} 전망대</td><td>5</td></tr>
 * <tr><td>{@code NIGHT_MARKET}</td><td>신호가 없다</td><td>0</td></tr>
 * <tr><td>{@code SOUVENIR_SHOP}</td><td>신호가 없다</td><td>0</td></tr>
 * </table>
 *
 * <h2>걷기길이 레포츠보다 먼저다</h2>
 * 원천은 문탠로드·송도 구름산책로·해안누리길을 레포츠({@code A03}) 아래 걷기길
 * ({@code A03022700})로 둔다. 대분류만 보면 그것들이 실탄사격장과 같은 갈래가 된다. 걷는
 * 길을 고른 사람에게 사격장을 주지 않으려고 세분류를 먼저 본다. 자연({@code A01}) 아래의
 * 해안 산책로({@code A01010500}) 도 같은 자리다.
 *
 * <h2>두 갈래는 비운다</h2>
 * 야시장과 기념품샵은 원천에 그것을 가리키는 코드도 유형도 없다. 수집본 656곳의 이름을 훑어도
 * 야시장이나 기념품이 들어간 것이 하나도 없다 — 부산의 대표 야시장이 관광지 목록이 아니라
 * 시장의 일부로 등록돼 있기 때문으로 보인다. 그럴듯한 것으로 채우지 않는다. 그 두 줄은 화면에
 * 접힌 채로 0건이 되고, 그것이 지금 우리가 아는 사실이다.
 *
 * <h2>전망대를 야경으로 옮긴 것은 번역이 아니라 판단이다</h2>
 * 나머지 다섯은 원천의 분류를 글자 그대로 옮긴 것인데 이 하나만 다르다. 전망대는 낮에도
 * 가는 곳이고 원천은 "야경" 이라고 말한 적이 없다. 그런데도 옮긴 이유는 부산에서 야경을
 * 보러 가는 곳이 사실상 이 전망대들이고(황령산 전망대가 그 목록에 있다), 틀렸을 때의 대가가
 * 작기 때문이다 — 야경 줄에 전망대가 뜨는 것은 사람이 목록을 보면 바로 보이고 그 행만 지우면
 * 되돌아간다. 장소를 잇는 데 이름을 쓰는 것과는 위험의 성격이 다르다.
 */
public final class TourApiExploreFacet {

	/** 행사·공연·축제. 원천의 콘텐츠 유형이 곧 이 갈래다. */
	private static final String CONTENT_TYPE_FESTIVAL = "15";

	/** 레포츠 아래 걷기길 — 문탠로드·해안누리길이 여기 있다. */
	private static final String CAT3_TRAIL = "A03022700";

	/** 자연 아래 해안 산책로 — 절영해안산책로·그린레일웨이가 여기 있다. */
	private static final String CAT3_COAST_WALK = "A01010500";

	/** 쇼핑 아래 5일장. */
	private static final String CAT3_PERIODIC_MARKET = "A04010100";

	/** 쇼핑 아래 상설시장 — 국제시장·구서오시게시장이 여기 있다. */
	private static final String CAT3_PERMANENT_MARKET = "A04010200";

	/** 인문 아래 전망대. */
	private static final String CAT3_OBSERVATORY = "A02050600";

	private TourApiExploreFacet() {
	}

	/**
	 * 이 장소에 붙일 갈래 코드들. 해당하는 것이 없으면 빈 목록이다.
	 *
	 * <p>한 장소가 둘 이상을 받을 수 있다. 축제는 콘텐츠 유형에서, 나머지는 분류 코드에서
	 * 오므로 겹치는 것이 가능하다 — 실측에서는 겹친 곳이 없었지만 막지 않는다.
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
