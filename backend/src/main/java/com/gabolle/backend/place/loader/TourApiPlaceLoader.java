package com.gabolle.backend.place.loader;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.PlaceEvidenceStatus;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlaceRepository;

/**
 * 관광공사 비음식 장소를 넣는다. 상가정보로 채울 수 있는 갈래는 {@code FOOD} 하나뿐이라,
 * 나머지 다섯 갈래의 장소를 이 적재가 메운다.
 *
 * <p>id 를 {@code "gabolle:place:TOURAPI:" + contentid} 에서 계산한다. 앞머리가
 * {@link SbizPlaceLoader#placeIdOf} 와 달라 두 계산이 같은 값을 낼 수 없고, 겹치면 서로 다른
 * 장소가 한 행이 되어 그 뒤의 모든 계산이 조용히 틀린다.
 *
 * <p>채우는 것은 {@code place.category} 와 {@code CATEGORY_TAG} 뿐이다({@link TourApiCategory}
 * 가 옮길 낱말을 낼 때만). 음식 갈래·분위기·인기는 이 자료에 없고, 접근성과 계단은 추정하면 안
 * 되며 DB 도 막는다. 영업시간은 상세 단계에 있어 다른 적재가 붙인다.
 *
 * <p>사진은 저작권 유형이 자유 이용인 것만 넣는다 — {@link #FREE_TO_USE_COPYRIGHT_TYPE} 참고.
 *
 * <p>갈래가 없는 장소도 넣는다. 갈래로는 안 나오지만 장소로는 존재해서 필수 방문지 지정에
 * 쓸 수 있다.
 *
 * <p>이미 있는 장소는 건너뛰고 고치지 않는다. 같은 파일을 두 번 돌려도 행이 두 배가 되지 않게
 * 하는 것이 여기서 지키는 전부고, 갱신은 별개의 결정이다.
 *
 * <p>🔴 예외가 하나 있다 — <b>비어 있는 갈래는 채운다</b> ({@link Place#fillMissingCategory}).
 * 건너뛰기만 하면 적재 규칙이 바뀌어도 먼저 들어온 행은 영영 안 따라온다. 2026-09-21 에 숙박
 * 65곳이 그랬다 (S15P21E201-1383) — 갈래를 비우기로 한 결정을 뒤집었는데, 자료를 다시 돌려도
 * 「이미 있음」으로 건너뛰어 화면은 그대로 0곳이었다. 채우는 것은 「모름 → 앎」 한 방향뿐이고
 * 이미 있는 값은 안 덮는다.
 */
@Component
@Profile({ "db", "dev" })
public class TourApiPlaceLoader {

	private static final Logger LOGGER = LoggerFactory.getLogger(TourApiPlaceLoader.class);

	/** 출처. {@code place.source_type} 과 {@code place_feature.source_type} 에 같이 들어간다. */
	public static final String SOURCE_TYPE = "TOURAPI";

	private static final int NAME_MAX = 200;

	private static final int ADDRESS_MAX = 300;

	/**
	 * 관광공사 사진의 저작권 유형({@code cpyrhtDivCd}) 중 자유 이용이 확인된 값. 공공누리 제1유형은
	 * 출처를 표시하면 자유 이용이 되고, 제3자 저작물은 재사용 전 저작권자의 별도 허락이 필요하다 —
	 * 그래서 이것만 쓴다. 대부분이 후자라 사진 칸은 비는 것이 정상이다.
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
	 * 한 덩어리를 넣고 <b>새로 넣은</b> 장소 수를 돌려준다. 이미 있던 장소의 빈 갈래를 채운 것은
	 * 이 수에 안 들어간다 — 넣은 것과 고친 것을 한 숫자로 합치면 무엇이 일어났는지 알 수 없다.
	 * 채운 수는 로그로 남긴다. {@code datasetVersion} 이 없으면 이 장소로 만든 추천이
	 * {@code VERSION_UNRESOLVED} 로 실패한다.
	 */
	@Transactional
	public int saveChunk(List<TourApiPlaceRow> rows, String datasetVersion, OffsetDateTime collectedAt) {
		List<UUID> ids = rows.stream().map(row -> placeIdOf(row.contentId())).toList();
		Map<UUID, Place> alreadyInDb = new HashMap<>();
		this.placeRepository.findAllById(ids).forEach(place -> alreadyInDb.put(place.getPlaceId(), place));

		List<Place> places = new ArrayList<>(rows.size());
		List<PlaceFeature> features = new ArrayList<>(rows.size());
		Set<UUID> seenInThisChunk = new HashSet<>();
		int categoriesFilled = 0;
		for (TourApiPlaceRow row : rows) {
			UUID placeId = placeIdOf(row.contentId());
			if (!seenInThisChunk.add(placeId)) {
				// 이 덩어리 안에서 중복된 contentid 다.
				continue;
			}
			String category = TourApiCategory.of(row.contentId(), row.cat1(), row.cat3());

			Place inDb = alreadyInDb.get(placeId);
			if (inDb != null) {
				// 이미 있는 장소는 고치지 않는다. 딱 하나, 비어 있는 갈래만 채운다.
				// 트랜잭션 안에서 읽은 엔티티라 값만 바꾸면 반영된다 — saveAll 이 따로 필요 없다.
				if (category != null && inDb.fillMissingCategory(category)) {
					categoriesFilled++;
					features.add(feature(placeId, row.contentId(), "CATEGORY_TAG", category,
							collectedAt, datasetVersion));
				}
				continue;
			}
			boolean freeToUsePhoto = row.firstImage() != null
					&& FREE_TO_USE_COPYRIGHT_TYPE.equals(row.copyrightType());
			places.add(Place.imported(placeId, cut(row.title(), NAME_MAX), category,
					cut(row.address(), ADDRESS_MAX), row.lat(), row.lng(),
					SOURCE_TYPE, row.contentId(), collectedAt,
					// 원천에 "이 사실이 언제 관측됐나" 칸이 없다. 지어내지 않고 비운다.
					null, datasetVersion,
					// 원천이 같은 호스트를 http 로도 https 로도 준다. 평문은 앱·iOS·웹 어디에서도
					// 안 보이므로 아는 호스트만 https 로 바꾼다.
					freeToUsePhoto ? PhotoUrlScheme.secure(row.firstImage()) : null,
					freeToUsePhoto ? PHOTO_SOURCE_LABEL : null));

			// 갈래는 CATEGORY_TAG 다. TourApiCategory 가 내는 낱말은 온보딩 취향의 사전이고,
			// 탐색 아코디언의 여덟 낱말(INTEREST_TAG)과 다른 사전이다.
			if (category != null) {
				features.add(feature(placeId, row.contentId(), "CATEGORY_TAG", category,
						collectedAt, datasetVersion));
			}
		}
		if (categoriesFilled > 0) {
			// 새로 넣은 수와 따로 남긴다. 합쳐 놓으면 "몇 곳이 살아났나" 를 나중에 못 되짚는다.
			LOGGER.info("이미 있던 장소 {}곳의 비어 있던 갈래를 채웠다", categoriesFilled);
		}
		if (places.isEmpty() && features.isEmpty()) {
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
				// 원천의 분류 칸에서 옮긴 것이지 장소에 직접 확인한 것이 아니다.
				PlaceEvidenceStatus.ESTIMATED,
				SOURCE_TYPE, contentId, null, datasetVersion, collectedAt);
	}

	/**
	 * 관광공사 식별자에서 언제나 같은 장소 아이디를 만든다. 앞머리 {@code TOURAPI} 가
	 * {@link SbizPlaceLoader} 의 {@code SBIZ} 와 다른 것이 요점이다.
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
