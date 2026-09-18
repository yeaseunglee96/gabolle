package com.gabolle.backend.place.api;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 지금 장소가 있는 갈래 목록 — S15P21E201-896.
 *
 * <p>표식 기준 갈래({@link PlaceFacetResponse})와 <b>다른 것</b>이다. 저쪽은
 * {@code place_feature} 표식이고 이쪽은 {@code place.category} 칼럼이다. 추천 후보를 좁힐 때
 * 실제로 비교되는 값은 이쪽이라, 취향 화면이 고를 수 있는 갈래를 정하는 근거도 이쪽이어야 한다.
 * 두 낱말을 같은 이름으로 합치지 않는 이유는 {@link PlaceQueryController} 클래스 주석과 같다.
 */
public record PlaceCategoryResponse(List<CategoryItem> categories, OffsetDateTime generatedAt) {

	/**
	 * @param code {@code place.category} 에 실제로 들어 있는 값. 서버가 목록을 정하지 않는다 —
	 *     적재된 것을 그대로 낸다
	 * @param placeCount 그 값을 가진 장소 수. 지역·반경과 무관한 전체 수다. 화면이 갈래를
	 *     보여줄지 말지 정하는 데 쓰는 값이라 위치에 따라 흔들리면 안 된다
	 */
	public record CategoryItem(String code, long placeCount) {
	}
}
