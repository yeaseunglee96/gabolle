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
 * 상가정보 한 덩어리를 {@code place} · {@code place_feature} 로 넣는다.
 *
 * <p>이 자료로 채우는 것은 {@code CATEGORY_TAG} 와 {@code CUISINE_TAG} 뿐이다. 분위기·인기·
 * 로컬성·조용함·그늘·경사는 출처가 없어 비우고, 안전과 접근성은 업종 이름에서 추정하면 안 되며
 * DB 도 막는다({@code ck_place_feature_safety_never_estimated}).
 *
 * <p>비운 칸을 그럴듯한 값으로 채우지 않는다. 없는 것을 만들어 놓고 "추정" 이라 적으면 그건
 * 창작이고, 그 창작을 모델이 배운다. 비어 있으면 그 항이 0 점이 되는 것이 아니라 빠질 뿐이다.
 *
 * <p>{@code place.category} 는 후보 필터가 보는 한 칸이라 갈래를 하나만 담는다. 카페는
 * {@code CAFE_HEALING}, 나머지 음식점은 {@code FOOD} 다 — 「카페·힐링」만 고른 사용자의 후보가
 * 0건이 되는 것보다 음식점 추천에 카페가 섞이는 쪽이 나쁘다는 판단이다. 카페에 붙는
 * {@code CATEGORY_TAG:FOOD} 는 이제 안 쓰이지만 지우지 않는다. 그 갈래의 장소가 아예 없어
 * 후보가 0건인 것은 사실 그대로다.
 */
@Component
@Profile({ "db", "dev" })
public class SbizPlaceLoader {

	/** {@code place.source_type}. 어디서 온 행인지 되짚을 때 이 값으로 찾는다. */
	public static final String SOURCE_TYPE = "SBIZ";

	/**
	 * {@code place.name_ko} 는 VARCHAR(200), {@code address} 는 VARCHAR(300) 이다. 넘치면 DB 가
	 * 거절해 그 덩어리 전체가 롤백된다 — 한 행 때문에 덩어리 전부가 사라진다.
	 */
	private static final int NAME_MAX = 200;

	private static final int ADDRESS_MAX = 300;

	private final PlaceRepository placeRepository;

	private final PlaceFeatureRepository placeFeatureRepository;

	private final SamePlaceGuard samePlaceGuard;

	public SbizPlaceLoader(PlaceRepository placeRepository, PlaceFeatureRepository placeFeatureRepository,
			SamePlaceGuard samePlaceGuard) {
		this.placeRepository = placeRepository;
		this.placeFeatureRepository = placeFeatureRepository;
		this.samePlaceGuard = samePlaceGuard;
	}

	/**
	 * 한 덩어리를 넣고 실제로 넣은 장소 수를 돌려준다. 이미 있는 장소는 건너뛰고 고치지 않는다 —
	 * 같은 파일을 두 번 돌려도 행이 두 배가 되지 않게 하는 것이 여기서 지키는 전부고, 다음 분기
	 * 파일로 갱신하는 규칙(폐업·이전·상호 변경)은 별개의 결정이다.
	 *
	 * <p>{@code datasetVersion} 이 없으면 이 장소로 만든 추천이 {@code VERSION_UNRESOLVED} 로
	 * 실패한다.
	 *
	 * <p>🔴 번호가 달라도 <b>같은 곳이 이미 있으면 넣지 않는다</b>({@link SamePlaceGuard}, S15P21E201-1620) — 이름이 같고
	 * 가까운 장소다. 상가 자료에는 같은 가게가 번호 둘로 있기도 하다(운영 「광안다이닝」 두 줄이 0m). 애매한 짝(체인
	 * 30~150m)도 넣지 않고 {@code report} 에 사람 확인으로 남긴다.
	 */
	@Transactional
	public int saveChunk(List<SbizRow> rows, String datasetVersion, OffsetDateTime collectedAt) {
		return saveChunk(rows, datasetVersion, collectedAt, new SamePlaceReport());
	}

	/** {@link #saveChunk(List, String, OffsetDateTime)} 에 같은 곳 판정을 모을 자리를 준다 — 실행기가 적재 끝에 찍는다. */
	@Transactional
	public int saveChunk(List<SbizRow> rows, String datasetVersion, OffsetDateTime collectedAt,
			SamePlaceReport report) {
		List<UUID> ids = rows.stream().map(row -> placeIdOf(row.storeId())).toList();
		Set<UUID> existing = new HashSet<>();
		this.placeRepository.findAllById(ids).forEach(place -> existing.add(place.getPlaceId()));

		List<SbizRow> fresh = new ArrayList<>(rows.size());
		for (SbizRow row : rows) {
			// 이미 있거나(DB) 이 덩어리 안에서 중복된 상가업소번호면 건너뛴다.
			if (existing.add(placeIdOf(row.storeId()))) {
				fresh.add(row);
			}
		}
		// 견주는 이름은 저장될 이름 그대로다 — 지점명을 붙이고 세미콜론을 자른 뒤.
		List<SamePlaceGuard.Candidate> candidates = fresh.stream()
				.map(row -> new SamePlaceGuard.Candidate(placeIdOf(row.storeId()),
						PlaceNames.primary(row.displayName()), categoryOf(row), row.lat(), row.lng(), SOURCE_TYPE,
						row.storeId()))
				.toList();
		List<SamePlaceGuard.Verdict> verdicts = this.samePlaceGuard.screen(candidates);

		List<Place> places = new ArrayList<>(fresh.size());
		List<PlaceFeature> features = new ArrayList<>(fresh.size() * 2);
		for (int i = 0; i < fresh.size(); i++) {
			SbizRow row = fresh.get(i);
			UUID placeId = placeIdOf(row.storeId());
			if (verdicts.get(i) != null) {
				report.record(candidates.get(i), verdicts.get(i));
				continue;
			}
			Set<String> categoryTags = AppFoodVocabulary.categoryTags(row.subCategory());
			String category = categoryOf(row);
			// 이름의 세미콜론(옛 이름·다른 이름)은 한 이름만 — PlaceNames(S15P21E201-1637)
			places.add(Place.imported(placeId, cut(PlaceNames.primary(row.displayName()), NAME_MAX), category,
					cut(row.address(), ADDRESS_MAX), row.lat(), row.lng(),
					SOURCE_TYPE, row.storeId(), collectedAt,
					// 원천에 "이 사실이 언제 관측됐나" 칸이 없다. 지어내지 않고 비운다.
					null, datasetVersion));

			// 앱이 보내는 낱말만 넣는다. 채점이 글자 그대로 비교하므로 다른 낱말을 적으면 그
			// 항이 조용히 0 이 된다 — AppFoodVocabulary 참조. 갈래는 CATEGORY_TAG 이고,
			// 탐색 쪽 낱말(INTEREST_TAG)을 여기 적으면 조회표 외래키가 거부한다.
			for (String tag : categoryTags) {
				features.add(feature(placeId, row.storeId(), "CATEGORY_TAG", tag, collectedAt, datasetVersion));
			}
			for (String cuisine : AppFoodVocabulary.cuisineTags(row.subCategory(), row.name())) {
				features.add(feature(placeId, row.storeId(), "CUISINE_TAG", cuisine, collectedAt, datasetVersion));
			}
		}
		if (places.isEmpty()) {
			return 0;
		}
		this.placeRepository.saveAll(places);
		this.placeFeatureRepository.saveAll(features);
		return places.size();
	}

	/** 업종에 카페 표식이 있으면 카페, 아니면 음식점 — 이 적재기가 넣는 갈래는 둘뿐이다. */
	private static String categoryOf(SbizRow row) {
		return AppFoodVocabulary.categoryTags(row.subCategory()).contains("CAFE_HEALING") ? "CAFE_HEALING" : "FOOD";
	}

	private static PlaceFeature feature(UUID placeId, String storeId, String featureType, String featureKey,
			OffsetDateTime collectedAt, String datasetVersion) {
		return PlaceFeature.imported(
				featureIdOf(storeId, featureType, featureKey), placeId, featureType, featureKey,
				// 태그형의 값은 "이 표식이 있다" 하나뿐이다.
				"true",
				// 업종 칸에서 옮긴 것이지 가게에 직접 확인한 것이 아니다.
				PlaceEvidenceStatus.ESTIMATED,
				SOURCE_TYPE, storeId, null, datasetVersion, collectedAt);
	}

	/**
	 * 상가업소번호에서 언제나 같은 장소 아이디를 만든다. 무작위 UUID 를 쓰면 같은 파일을 두 번
	 * 돌릴 때 같은 가게가 두 행이 되고, 그러면 후보 수가 부풀어 백분위가 좋아 보이며 정답이
	 * 한 행에만 붙어 나머지 한 행이 오답으로 학습된다.
	 */
	public static UUID placeIdOf(String storeId) {
		return UUID.nameUUIDFromBytes(("gabolle:place:SBIZ:" + storeId).getBytes(StandardCharsets.UTF_8));
	}

	static UUID featureIdOf(String storeId, String featureType, String featureKey) {
		return UUID.nameUUIDFromBytes(
				("gabolle:place_feature:SBIZ:" + storeId + ":" + featureType + ":" + featureKey)
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
