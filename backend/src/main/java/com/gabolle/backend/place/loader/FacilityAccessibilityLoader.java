package com.gabolle.backend.place.loader;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.domain.PlaceEvidenceStatus;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlaceRepository;

/**
 * 장애인편의시설 실태조사에서 확인한 접근성 표식을 붙인다 — S15P21E201-1365.
 *
 * <h2>{@link AccessibilityLoader} 와 무엇이 다른가</h2>
 * 같은 표({@code place_feature})의 같은 갈래({@code ACCESSIBILITY_TAG})에 들어간다.
 * 마이그레이션이 없다. 다른 것은 셋이다.
 *
 * <ol>
 * <li><b>출처가 다르다.</b> 저기는 관광공사 무장애 자료, 여기는 국가 실태조사다.
 * {@code source_type} 으로 갈린다 — 나중에 "이 값이 어디서 왔나" 를 그것으로 되찾는다</li>
 * <li><b>상가 장소에도 붙는다.</b> 관광공사분만 다루는 저기와 달리 여기는 출처가 둘이라
 * 장소 id 계산식이 줄마다 다르다 ({@link FacilityAccessibilityRow#placeId()})</li>
 * <li><b>{@code source_id} 에 시설 고유번호를 적는다.</b> 장소 열쇠가 아니라 실태조사
 * 기록의 열쇠다. 표시가 틀렸다는 제보가 오면 <b>어느 조사 기록에서 왔는지</b> 그것으로 찾는다</li>
 * </ol>
 *
 * <h2>증거 등급은 VERIFIED 뿐이다 — 고를 수 없다</h2>
 * DB 가 접근성에 {@code ESTIMATED} 를 거부한다({@code ck_place_feature_safety_never_estimated}).
 * "조금 덜 확실함" 으로 적는 길이 없다. 그래서 {@link FacilityAccessibility} 가 통과시키는
 * 항목을 좁게 잡았다 — 등급을 못 낮추면 <b>문턱을 올리는 수밖에 없다.</b>
 *
 * <h2>이미 있는 것은 안 덮는다</h2>
 * 표식 id 를 장소를 만든 적재기의 계산식으로 만들기 때문에, 관광공사 무장애 자료로 이미
 * 붙어 있는 표식과 <b>같은 id</b> 가 나온다. 그래서 두 번 붙는 대신 "이미 있어 넘김" 으로
 * 세어진다. 먼저 들어간 값이 이긴다.
 */
@Component
@Profile({ "db", "dev" })
public class FacilityAccessibilityLoader {

	/** 접근성 표식이 사는 자리. {@link AccessibilityLoader#FEATURE_TYPE} 과 같아야 한다. */
	public static final String FEATURE_TYPE = AccessibilityLoader.FEATURE_TYPE;

	/** 이 값이 국가 장애인편의시설 실태조사에서 왔다는 표시. */
	public static final String SOURCE_TYPE = "BF_FACILITY";

	private final PlaceRepository placeRepository;

	private final PlaceFeatureRepository placeFeatureRepository;

	public FacilityAccessibilityLoader(PlaceRepository placeRepository,
			PlaceFeatureRepository placeFeatureRepository) {
		this.placeRepository = placeRepository;
		this.placeFeatureRepository = placeFeatureRepository;
	}

	@Transactional
	public Result saveChunk(List<FacilityAccessibilityRow> rows, String datasetVersion,
			OffsetDateTime collectedAt) {
		Set<UUID> knownPlaces = new HashSet<>();
		this.placeRepository.findAllById(rows.stream().map(FacilityAccessibilityRow::placeId).toList())
				.forEach((place) -> knownPlaces.add(place.getPlaceId()));

		List<UUID> featureIds = new ArrayList<>();
		for (FacilityAccessibilityRow row : rows) {
			for (String code : row.codes()) {
				featureIds.add(row.featureIdOf(FEATURE_TYPE, code));
			}
		}
		Set<UUID> existing = new HashSet<>();
		this.placeFeatureRepository.findAllById(featureIds)
				.forEach((feature) -> existing.add(feature.getPlaceFeatureId()));

		List<PlaceFeature> features = new ArrayList<>();
		int noPlace = 0;
		int alreadyThere = 0;
		for (FacilityAccessibilityRow row : rows) {
			UUID placeId = row.placeId();
			if (!knownPlaces.contains(placeId)) {
				noPlace++;
				continue;
			}
			for (String code : row.codes()) {
				UUID featureId = row.featureIdOf(FEATURE_TYPE, code);
				if (!existing.add(featureId)) {
					alreadyThere++;
					continue;
				}
				features.add(PlaceFeature.imported(featureId, placeId, FEATURE_TYPE, code,
						"true", PlaceEvidenceStatus.VERIFIED,
						SOURCE_TYPE, row.facilityId(),
						null, datasetVersion, collectedAt));
			}
		}
		if (!features.isEmpty()) {
			this.placeFeatureRepository.saveAll(features);
		}
		return new Result(features.size(), noPlace, alreadyThere);
	}

	/**
	 * @param inserted     새로 붙인 표식
	 * @param noPlace      붙일 장소가 없어 넘긴 줄. 관광공사분은 목록에만 있고 장소로 안 들어간
	 *                     것이 많아 <b>이 수가 크게 나오는 것이 정상이다</b>
	 * @param alreadyThere 이미 있어 넘긴 표식. 관광공사 무장애 자료로 먼저 붙은 것들이다
	 */
	public record Result(int inserted, int noPlace, int alreadyThere) {

		public Result plus(Result other) {
			return new Result(this.inserted + other.inserted, this.noPlace + other.noPlace,
					this.alreadyThere + other.alreadyThere);
		}

		@Override
		public String toString() {
			return "새로 붙인 %d · 붙일 장소가 없어 넘긴 %d · 이미 있어 넘긴 %d"
					.formatted(this.inserted, this.noPlace, this.alreadyThere);
		}
	}
}
