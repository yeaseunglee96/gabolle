package com.gabolle.backend.place.loader;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.PlaceEvidenceStatus;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlaceRepository;

/**
 * 관광공사 비음식 장소를 넣는다 — S15P21E201-854.
 *
 * <h2>왜 이 적재가 필요했나</h2>
 * 사용자가 관심사를 바다·음식·자연으로 골랐는데 맞는 것이 음식 하나뿐이었다(2026-09-10 운영
 * 실측, {@code interest 0.333}). 추천 코드 탓이 아니다 — {@code SbizPlaceLoader} 주석이
 * 적어 뒀듯 상가정보로 채울 수 있는 갈래는 {@code FOOD} 하나이고 <b>나머지 다섯 갈래의 장소를
 * 아직 아무도 안 넣었다.</b> 이 적재가 그 빈칸을 메운다.
 *
 * <h2>🔴 id 를 관광공사 식별자에서 계산한다 — 상가업소 것과 겹치지 않는다</h2>
 * {@link SbizPlaceLoader#placeIdOf} 는 {@code "gabolle:place:SBIZ:" + 상가업소번호} 를 해싱한다.
 * 여기는 {@code "gabolle:place:TOURAPI:" + contentid} 다. <b>앞머리가 달라서 두 계산이 같은 값을
 * 낼 수 없고</b>, 그것을 검사가 못 박는다({@code TourApiPlaceIdTest}). 겹치면 서로 다른 장소가
 * 한 행이 되고 그 뒤의 모든 계산이 조용히 틀린다.
 *
 * <h2>무엇을 채우고 무엇을 비워 두나</h2>
 * <table border="1">
 * <caption>이 적재가 건드리는 것</caption>
 * <tr><th>칸</th><th>자료</th><th>채우나</th></tr>
 * <tr><td>{@code place.category}</td><td>원천 대분류 {@code cat1}</td>
 *     <td>🟢 옮길 낱말이 있는 것만 — {@link TourApiCategory}</td></tr>
 * <tr><td>{@code CATEGORY_TAG:<갈래>}</td><td>같은 판정</td><td>🟢 갈래가 있는 것만</td></tr>
 * <tr><td>{@code CUISINE_TAG}·{@code ATMOSPHERE_TAG}·{@code POPULARITY_SCORE}</td>
 *     <td>🔴 이 자료에 없다</td><td>비운다</td></tr>
 * <tr><td>{@code ACCESSIBILITY_TAG}·{@code STAIRS_PRESENT}</td>
 *     <td>🔴 안전·접근성. 추정하면 안 되고 DB 도 막는다
 *     ({@code ck_place_feature_safety_never_estimated})</td><td>비운다. 무장애 자료가 따로 있다</td></tr>
 * <tr><td>영업시간</td><td>상세 단계에 있다</td><td>비운다 — {@code S15P21E201-852}</td></tr>
 * <tr><td>{@code place.photo_url}</td><td>{@code firstimage}</td>
 *     <td>🟡 저작권 유형이 {@code Type1} 인 것만 (S15P21E201-146). 사진 있는 546곳 중
 *     대부분(468곳, 86%)이 {@code Type3}(제3자 저작물, 재사용 전 저작권자 허락 필요)라 비운다 —
 *     아래 {@link #FREE_TO_USE_COPYRIGHT_TYPE} 참고</td></tr>
 * </table>
 *
 * <p>🔴 <b>갈래가 없는 장소도 넣는다.</b> 레포츠 29곳·숙박 65곳이 그렇다. 갈래로는 안 나오지만
 * 장소로는 존재해서 숙소 지정({@code accommodationPlaceId})과 필수 방문지 지정에 쓸 수 있다.
 * 갈래를 억지로 붙이는 것보다 비워 두는 것이 낫다는 판단은 {@link TourApiCategory} 에 적었다.
 *
 * <h2>이미 있는 장소는 건너뛴다 — 고치지 않는다</h2>
 * {@link SbizPlaceLoader#saveChunk} 와 같은 규칙이다. 같은 파일을 두 번 돌려도 행이 두 배가
 * 되지 않게 하는 것이 여기서 지키는 전부다. 갱신(이름이 바뀌었을 때 어떻게 하나)은 별개의
 * 결정이라 미리 정하지 않는다.
 */
@Component
@Profile({ "db", "dev" })
public class TourApiPlaceLoader {

	/** 출처. {@code place.source_type} 과 {@code place_feature.source_type} 에 같이 들어간다. */
	public static final String SOURCE_TYPE = "TOURAPI";

	private static final int NAME_MAX = 200;

	private static final int ADDRESS_MAX = 300;

	/**
	 * 관광공사 사진의 저작권 유형(cpyrhtDivCd) 중 자유 이용이 확인된 값 — S15P21E201-146.
	 *
	 * <p>{@code Type1}(공공누리 제1유형)은 출처를 표시하면 자유 이용이 된다. {@code Type3}은
	 * 제3자 저작물이라 재사용 전 저작권자의 별도 허락이 필요하다 — 그래서 이것만 쓴다. 실측
	 * (2026-09-15): 사진 있는 546곳 중 Type1 은 78곳(14%), Type3 이 468곳(86%)이다.
	 */
	private static final String FREE_TO_USE_COPYRIGHT_TYPE = "Type1";

	/** {@code place.photo_source} 에 넣는 출처 표기 — {@code photo_url} 과 반드시 짝이다. */
	private static final String PHOTO_SOURCE_LABEL = "한국관광공사 공공누리 제1유형";

	private final PlaceRepository placeRepository;

	private final PlaceFeatureRepository placeFeatureRepository;

	public TourApiPlaceLoader(PlaceRepository placeRepository, PlaceFeatureRepository placeFeatureRepository) {
		this.placeRepository = placeRepository;
		this.placeFeatureRepository = placeFeatureRepository;
	}

	/**
	 * 한 덩어리를 넣는다.
	 *
	 * @param datasetVersion 어느 수집분인가. 🔴 이 값이 없으면 이 장소로 만든 추천이
	 *     {@code VERSION_UNRESOLVED} 로 실패한다
	 * @return 실제로 넣은 장소 수
	 */
	@Transactional
	public int saveChunk(List<TourApiPlaceRow> rows, String datasetVersion, OffsetDateTime collectedAt) {
		List<UUID> ids = rows.stream().map(row -> placeIdOf(row.contentId())).toList();
		Set<UUID> existing = new HashSet<>();
		this.placeRepository.findAllById(ids).forEach(place -> existing.add(place.getPlaceId()));

		List<Place> places = new ArrayList<>(rows.size());
		List<PlaceFeature> features = new ArrayList<>(rows.size());
		for (TourApiPlaceRow row : rows) {
			UUID placeId = placeIdOf(row.contentId());
			if (!existing.add(placeId)) {
				// 이미 있거나(DB) 이 덩어리 안에서 중복된 contentid 다.
				continue;
			}
			String category = TourApiCategory.of(row.contentId(), row.cat1(), row.cat3());
			boolean freeToUsePhoto = row.firstImage() != null
					&& FREE_TO_USE_COPYRIGHT_TYPE.equals(row.copyrightType());
			places.add(Place.imported(placeId, cut(row.title(), NAME_MAX), category,
					cut(row.address(), ADDRESS_MAX), row.lat(), row.lng(),
					SOURCE_TYPE, row.contentId(), collectedAt,
					// 🔴 원천에 "이 사실이 언제 관측됐나" 칸이 없다. 지어내지 않고 비운다 —
					//    수집분 자체는 datasetVersion 이 말해 준다.
					null, datasetVersion,
					freeToUsePhoto ? row.firstImage() : null,
					freeToUsePhoto ? PHOTO_SOURCE_LABEL : null));

			// 🔴 갈래는 CATEGORY_TAG 다 (S15P21E201-904). TourApiCategory 가 내는 여섯
			//    낱말은 온보딩 취향의 사전이고, 탐색 아코디언의 여덟 낱말과 다른 사전이다.
			//    두 사전이 INTEREST_TAG 한 서랍에 같이 있던 것을 갈랐다.
			if (category != null) {
				features.add(feature(placeId, row.contentId(), "CATEGORY_TAG", category,
						collectedAt, datasetVersion));
			}
		}
		if (places.isEmpty()) {
			return 0;
		}
		this.placeRepository.saveAll(places);
		this.placeFeatureRepository.saveAll(features);
		return places.size();
	}

	private static PlaceFeature feature(UUID placeId, String contentId, String featureType, String featureKey,
			OffsetDateTime collectedAt, String datasetVersion) {
		return PlaceFeature.imported(
				featureIdOf(contentId, featureType, featureKey), placeId, featureType, featureKey,
				// 태그형의 값은 "이 표식이 있다" 하나뿐이다.
				"true",
				// 🔴 ESTIMATED — 원천의 분류 칸에서 옮긴 것이지 장소에 직접 확인한 것이 아니다.
				//    VERIFIED 로 적으면 나중에 아무도 이 값을 의심하지 않는다.
				PlaceEvidenceStatus.ESTIMATED,
				SOURCE_TYPE, contentId, null, datasetVersion, collectedAt);
	}

	/**
	 * 관광공사 식별자에서 <b>언제나 같은</b> 장소 아이디를 만든다.
	 *
	 * <p>🔴 앞머리 {@code TOURAPI} 가 {@link SbizPlaceLoader} 의 {@code SBIZ} 와 다른 것이
	 * 이 메서드의 요점이다. 두 적재가 만나는 지점이 그 한 가지 사실이고, 검사가 그것을 못 박는다.
	 */
	public static UUID placeIdOf(String contentId) {
		return UUID.nameUUIDFromBytes(("gabolle:place:TOURAPI:" + contentId).getBytes(StandardCharsets.UTF_8));
	}

	static UUID featureIdOf(String contentId, String featureType, String featureKey) {
		return UUID.nameUUIDFromBytes(
				("gabolle:place_feature:TOURAPI:" + contentId + ":" + featureType + ":" + featureKey)
						.getBytes(StandardCharsets.UTF_8));
	}

	private static String cut(String value, int max) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
	}
}
