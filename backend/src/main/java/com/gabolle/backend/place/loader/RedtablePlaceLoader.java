package com.gabolle.backend.place.loader;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
 * 부산 레드테이블(부산광역시가 공공데이터포털에 연 식당 목록 API) 식당을 {@code place} 에 넣는다 (S15P21E201-1897).
 * 처음 쓰는 곳은 설문에서 부산 사람들이 추천했는데 상가정보(SBIZ)에 없던 식당·카페·술집이다.
 *
 * <p>{@link SbizPlaceLoader} 를 그대로 본떴다 — 번호에서 언제나 같은 장소 id, 이미 있으면 건너뛰고 안 고침, 같은 곳이
 * 이미 있으면({@link SamePlaceGuard}) 넣지 않고 보고에 남김. 채우는 것은 {@code place.category} 와 같은 낱말의
 * {@code CATEGORY_TAG} 하나뿐이다.
 *
 * <p><b>갈래 옮김표</b> ({@link #categoryOf}). 두 갈래만 낸다.
 * <ul>
 *   <li>{@code CAFE_HEALING} — 업태가 커피숍·다방·전통찻집·떡카페·아이스크림·제과점영업일 때. 업태가 비었으면 허가가
 *       제과점영업·휴게음식점(술을 못 파는 허가 — 대개 카페다)이거나 이름에 커피·카페·다실·제과·베이커리가 있을 때</li>
 *   <li>{@code FOOD} — 나머지 전부(한식·일식·분식·호프/통닭·회집·경양식 …). 술집도 여기다 — 앱에 술집 갈래가 없다</li>
 * </ul>
 * 업태가 적혀 있으면 이름보다 업태를 믿는다 — 「진수밥상」은 설문에서 카페로 왔지만 업태가 한식이라 {@code FOOD} 다.
 *
 * <p>🔴 <b>사진은 넣지 않는다.</b> 레드테이블 사진({@code RSTR_IMG_URL})을 다시 써도 되는지, 출처를 어떻게 적어야 하는지가
 * 이 저장소 어디에도 적혀 있지 않다(관광공사 사진은 공공누리 제1유형만 쓴다 — {@link TourApiPlaceLoader}). 식당이 찍은
 * 사진일 수 있어 조건을 모르고 쓰면 안 된다. 주소는 파일에 남겨 두었으니 조건을 확인한 뒤 따로 붙인다.
 */
@Component
@Profile({ "db", "dev" })
public class RedtablePlaceLoader {

	/** {@code place.source_type}. DB 에 이 칸의 허용 목록(CHECK)은 없다 — 2026-09-30 마이그레이션 전부 확인. */
	public static final String SOURCE_TYPE = "REDTABLE";

	private static final int NAME_MAX = 200;

	private static final int ADDRESS_MAX = 300;

	private static final Set<String> CAFE_BUSINESS_TYPES = Set.of("커피숍", "다방", "전통찻집", "떡카페", "아이스크림",
			"제과점영업");

	private static final List<String> CAFE_NAME_HINTS = List.of("커피", "카페", "cafe", "coffee", "다실", "제과", "베이커리");

	private final PlaceRepository placeRepository;

	private final PlaceFeatureRepository placeFeatureRepository;

	private final SamePlaceGuard samePlaceGuard;

	public RedtablePlaceLoader(PlaceRepository placeRepository, PlaceFeatureRepository placeFeatureRepository,
			SamePlaceGuard samePlaceGuard) {
		this.placeRepository = placeRepository;
		this.placeFeatureRepository = placeFeatureRepository;
		this.samePlaceGuard = samePlaceGuard;
	}

	/**
	 * 한 덩어리를 넣고 새로 넣은 장소 수를 돌려준다. 이미 있는 장소(같은 번호)는 건너뛰고 고치지 않는다 — 두 번 돌려도
	 * 행이 안 는다. 번호가 달라도 같은 곳이 이미 있으면 넣지 않고 {@code report} 에 남긴다.
	 */
	@Transactional
	public int saveChunk(List<RedtablePlaceRow> rows, String datasetVersion, OffsetDateTime collectedAt,
			SamePlaceReport report) {
		List<UUID> ids = rows.stream().map(row -> placeIdOf(row.rstrId())).toList();
		Set<UUID> existing = new HashSet<>();
		this.placeRepository.findAllById(ids).forEach(place -> existing.add(place.getPlaceId()));

		List<RedtablePlaceRow> fresh = new ArrayList<>(rows.size());
		for (RedtablePlaceRow row : rows) {
			if (existing.add(placeIdOf(row.rstrId()))) {
				fresh.add(row);
			}
		}
		List<SamePlaceGuard.Candidate> candidates = fresh.stream()
				.map(row -> new SamePlaceGuard.Candidate(placeIdOf(row.rstrId()), PlaceNames.primary(row.name()),
						categoryOf(row), row.lat(), row.lng(), SOURCE_TYPE, row.rstrId()))
				.toList();
		List<SamePlaceGuard.Verdict> verdicts = this.samePlaceGuard.screen(candidates);

		List<Place> places = new ArrayList<>(fresh.size());
		List<PlaceFeature> features = new ArrayList<>(fresh.size());
		for (int i = 0; i < fresh.size(); i++) {
			RedtablePlaceRow row = fresh.get(i);
			if (verdicts.get(i) != null) {
				report.record(candidates.get(i), verdicts.get(i));
				continue;
			}
			UUID placeId = placeIdOf(row.rstrId());
			String category = categoryOf(row);
			places.add(Place.imported(placeId, cut(PlaceNames.primary(row.name()), NAME_MAX), category,
					cut(row.address(), ADDRESS_MAX), row.lat(), row.lng(), SOURCE_TYPE, row.rstrId(), collectedAt,
					// 원천에 "이 사실이 언제 관측됐나" 칸이 없다. 지어내지 않고 비운다.
					null, datasetVersion));
			features.add(PlaceFeature.imported(featureIdOf(row.rstrId(), "CATEGORY_TAG", category), placeId,
					"CATEGORY_TAG", category, "true",
					// 업태 칸에서 옮긴 것이지 가게에 직접 확인한 것이 아니다.
					PlaceEvidenceStatus.ESTIMATED, SOURCE_TYPE, row.rstrId(), null, datasetVersion, collectedAt));
		}
		if (places.isEmpty()) {
			return 0;
		}
		this.placeRepository.saveAll(places);
		this.placeFeatureRepository.saveAll(features);
		return places.size();
	}

	/** 갈래 옮김표 — 클래스 설명 참고. */
	static String categoryOf(RedtablePlaceRow row) {
		String business = row.businessType();
		if (business != null) {
			return CAFE_BUSINESS_TYPES.contains(business) ? "CAFE_HEALING" : "FOOD";
		}
		if ("제과점영업".equals(row.licenseType()) || "휴게음식점".equals(row.licenseType())) {
			return "CAFE_HEALING";
		}
		String name = row.name().toLowerCase(Locale.ROOT);
		return CAFE_NAME_HINTS.stream().anyMatch(name::contains) ? "CAFE_HEALING" : "FOOD";
	}

	/** 레드테이블 번호에서 언제나 같은 장소 id — 앞머리 {@code REDTABLE} 이 다른 출처와 겹치지 않게 한다. */
	public static UUID placeIdOf(String rstrId) {
		return UUID.nameUUIDFromBytes(("gabolle:place:REDTABLE:" + rstrId).getBytes(StandardCharsets.UTF_8));
	}

	static UUID featureIdOf(String rstrId, String featureType, String featureKey) {
		return UUID.nameUUIDFromBytes(("gabolle:place_feature:REDTABLE:" + rstrId + ":" + featureType + ":"
				+ featureKey).getBytes(StandardCharsets.UTF_8));
	}

	private static String cut(String value, int max) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
	}
}
