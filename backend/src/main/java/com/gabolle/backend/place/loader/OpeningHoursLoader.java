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
 * 정규화한 영업시간을 장소에 붙인다 — S15P21E201-852.
 *
 * <h2>🔴 잇는 단계가 필요 없다 — 그것이 이 적재가 지금 가능한 이유다</h2>
 * 영업시간 출력의 키는 관광공사 {@code contentid} 다. 그리고 {@link TourApiPlaceLoader} 가
 * 장소 id 를 <b>바로 그 값에서</b> 계산한다({@code "gabolle:place:TOURAPI:" + contentid}).
 * 그래서 두 자료가 같은 열쇠를 쓰고, 이름·좌표로 짝을 찾는 단계
 * ({@code bigData/process/place-link.mjs})가 여기서는 필요하지 않다.
 *
 * <p>그 단계가 필요한 것은 <b>상가정보 2,355곳</b>에 영업시간을 붙일 때다. 그쪽은 열쇠가
 * 상가업소번호이고 관광공사 자료에 그 번호가 없다. 그 입력 둘이 저장소에 없어 아직 못 돈다 —
 * 이 적재가 그것을 기다리지 않는다는 것이 요점이다.
 *
 * <h2>장소가 없는 줄은 넘긴다</h2>
 * 정규화 출력에는 음식 328곳도 들어 있다. 그 장소는 일부러 적재하지 않았으므로
 * ({@code S15P21E201-854}) 붙일 자리가 없다. 🔴 <b>조용히 넘기지 않고 세어 올린다</b> — 그
 * 숫자가 갑자기 커지면 장소 적재를 안 돌렸거나 수집분이 어긋난 것이다.
 *
 * <h2>증거 등급은 {@code ESTIMATED}</h2>
 * 공공기관이 공표한 값이라 {@code VERIFIED} 로 볼 여지도 있다. 그런데 그 값은 업소가 기관에
 * 신고한 자기 보고를 모은 것이고 <b>언제 확인됐는지 원천이 말해 주지 않는다.</b> 틀렸을 때의
 * 대가도 비대칭이다 — 잘못된 {@code VERIFIED} 는 사람을 닫힌 문 앞에 보낸다.
 *
 * <h2>두 번 돌려도 행이 안 는다</h2>
 * 피처 id 를 {@code contentid} 와 갈래에서 계산하고, 이미 있는 id 는 건드리지 않는다.
 * 🔴 <b>고치지도 않는다.</b> 값을 갱신할지는 별개의 결정이다 — 새 수집분이 옛 값을 덮어야
 * 하는지, 덮으면 언제 확인된 값인지를 무엇으로 아는지가 먼저 정해져야 한다.
 */
@Component
@Profile({ "db", "dev" })
public class OpeningHoursLoader {

	private final PlaceRepository placeRepository;

	private final PlaceFeatureRepository placeFeatureRepository;

	public OpeningHoursLoader(PlaceRepository placeRepository,
			PlaceFeatureRepository placeFeatureRepository) {
		this.placeRepository = placeRepository;
		this.placeFeatureRepository = placeFeatureRepository;
	}

	/**
	 * 한 덩어리를 넣는다.
	 *
	 * @param datasetVersion 어느 수집분인가. {@code place_feature.source_version} 에 남는다
	 * @return 무엇을 넣고 무엇을 넘겼나
	 */
	@Transactional
	public Result saveChunk(List<OpeningHoursRow> rows, String datasetVersion, OffsetDateTime collectedAt) {
		Set<UUID> knownPlaces = new HashSet<>();
		this.placeRepository
				.findAllById(rows.stream().map((row) -> TourApiPlaceLoader.placeIdOf(row.contentId())).toList())
				.forEach((place) -> knownPlaces.add(place.getPlaceId()));

		Set<UUID> existingFeatures = new HashSet<>();
		this.placeFeatureRepository
				.findAllById(rows.stream().map((row) -> featureIdOf(row.contentId(), row.featureType())).toList())
				.forEach((feature) -> existingFeatures.add(feature.getPlaceFeatureId()));

		List<PlaceFeature> features = new ArrayList<>(rows.size());
		int noPlace = 0;
		int alreadyThere = 0;
		for (OpeningHoursRow row : rows) {
			UUID placeId = TourApiPlaceLoader.placeIdOf(row.contentId());
			if (!knownPlaces.contains(placeId)) {
				noPlace++;
				continue;
			}
			UUID featureId = featureIdOf(row.contentId(), row.featureType());
			if (!existingFeatures.add(featureId)) {
				alreadyThere++;
				continue;
			}
			features.add(PlaceFeature.imported(featureId, placeId, row.featureType(),
					// 🔴 키를 비운다. ck_place_feature_key_shape 가 태그형 여섯만 키를
					//    요구하고 나머지는 반드시 비어 있어야 한다고 정해 뒀다.
					null,
					row.value(), PlaceEvidenceStatus.ESTIMATED,
					TourApiPlaceLoader.SOURCE_TYPE, row.contentId(),
					// 원천에 "이 사실이 언제 관측됐나" 칸이 없다. 지어내지 않고 비운다.
					null, datasetVersion, collectedAt));
		}
		if (!features.isEmpty()) {
			this.placeFeatureRepository.saveAll(features);
		}
		return new Result(features.size(), noPlace, alreadyThere);
	}

	/**
	 * @param inserted     새로 넣은 행
	 * @param noPlace      붙일 장소가 없어 넘긴 줄 (음식 328곳이 여기 들어온다)
	 * @param alreadyThere 이미 있어 넘긴 줄
	 */
	public record Result(int inserted, int noPlace, int alreadyThere) {

		public Result plus(Result other) {
			return new Result(this.inserted + other.inserted, this.noPlace + other.noPlace,
					this.alreadyThere + other.alreadyThere);
		}

		@Override
		public String toString() {
			return "새로 넣은 %d · 붙일 장소가 없어 넘긴 %d · 이미 있어 넘긴 %d"
					.formatted(this.inserted, this.noPlace, this.alreadyThere);
		}
	}

	/**
	 * 피처 id 를 {@code contentid} 와 갈래에서 계산한다 — 두 번 돌려도 같은 값이 나온다.
	 *
	 * <p>🔴 {@link TourApiPlaceLoader#featureIdOf} 와 같은 앞머리를 쓰되 갈래가 들어가므로
	 * {@code INTEREST_TAG} 행과 겹치지 않는다. 겹치면 갈래 표식이 영업시간으로 덮인다.
	 */
	public static UUID featureIdOf(String contentId, String featureType) {
		// 🔴 장소 적재와 같은 계산을 쓰고 키 자리만 비운다. INTEREST_TAG 행은 키가 반드시
		//    있으므로(ck_place_feature_key_shape) 그쪽과 겹칠 수 없다. 계산을 한 곳에 두면
		//    "겹치지 않는다" 를 한 자리에서 확인할 수 있다.
		return TourApiPlaceLoader.featureIdOf(contentId, featureType, "");
	}
}
