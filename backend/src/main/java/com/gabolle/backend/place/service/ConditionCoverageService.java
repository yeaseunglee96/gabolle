package com.gabolle.backend.place.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.api.ConditionCoverageResponse;
import com.gabolle.backend.place.domain.UserPlaceCodeMap;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.repository.UserPlaceCodeMapRepository;

/**
 * 어느 문항을 판정할 장소 자료가 지금 있는지 센다 — S15P21E201-1508.
 *
 * <p><b>왜 필요한가.</b> 여행 조건 화면은 <i>"AI가 아래 조건을 임의로 완화하지 않습니다"</i>
 * 라고 적어 두고 조건을 묻는데, 그 조건을 판정할 장소 값이 한 건도 없는 문항이 있다. 묻고서
 * 못 지키는 약속이고, <b>그 상태가 아무 오류도 내지 않는다</b>(S15P21E201-1044).
 *
 * <p>🔴 <b>문항 목록을 여기에 적지 않는다.</b> {@code user_place_code_map} 에서 읽는다 —
 * 그 표가 「어느 문항이 어느 표식을 보는가」의 정본이고, {@code UserPlaceCodeMapRepository}
 * 가 <i>"자바에 갈래 목록을 두면 정본이 둘이 되고, 마이그레이션이 9번째 차원을 넣어도 응답은
 * 여덟 개만 나온다"</i> 고 못 박아 뒀다. 여기서 또 적으면 <b>새 문항이 생긴 날 화면이 그
 * 문항만 조용히 빠뜨린다.</b>
 *
 * <p>덕분에 {@code S15P21E201-1044} 의 끝의 정의 — <i>"장소 자료가 들어오면 코드 수정 없이
 * 표시가 사라진다"</i> — 가 저절로 지켜진다. 세는 값이 DB 라서 그렇다.
 */
@Service
@Profile({ "db", "dev" })
public class ConditionCoverageService {

	/**
	 * 대조표에 없는데 채점기가 실제로 판정에 쓰는 표식 — 문항 키({@code 종류/코드})마다.
	 *
	 * <p>🔴 왜 따로 있나. 이동 조건(휠체어·유아차·큰 짐)은 접근성 표식이 없는 곳을 장소 경사({@code SLOPE_PERCENT})로
	 * 가른다({@code BaselineCandidateScorer.evaluateSlope} — 상한을 넘으면 「반드시」는 빼고 「되도록」은 경고). 그런데
	 * 채점기는 그 표식을 대조표를 거치지 않고 이름으로 읽어서, 대조표만 세던 이 응답에는 이동 조건의 판정 자료가
	 * 접근성(몇 곳)·계단(0곳)뿐으로 보였다. 실제로는 거의 모든 장소의 경사로 가르고 있는데 화면은 「판정할 자료가
	 * 거의 없다」고 말하게 된다.
	 *
	 * <p>대조표에 줄을 더하지 않은 것은, 대조표를 읽는 채점기의 갈래(HARD_FILTER·FLAG_COMPARE)가 새 줄을 다른 뜻으로
	 * 읽을 수 있어서다 — 세는 쪽을 맞추려고 판정을 건드리지 않는다. 채점기가 경사를 안 읽게 되면 여기서도 뺀다.
	 */
	private static final Map<String, List<String>> SCORER_ONLY_FEATURES =
			Map.of("CONSTRAINT/MOBILITY", List.of("SLOPE_PERCENT"));

	private final UserPlaceCodeMapRepository codeMapRepository;

	private final PlaceFeatureRepository featureRepository;

	private final PlaceRepository placeRepository;

	public ConditionCoverageService(UserPlaceCodeMapRepository codeMapRepository,
			PlaceFeatureRepository featureRepository, PlaceRepository placeRepository) {
		this.codeMapRepository = codeMapRepository;
		this.featureRepository = featureRepository;
		this.placeRepository = placeRepository;
	}

	/**
	 * 문항마다 「판정할 자료가 몇 곳에 있나」.
	 *
	 * <p>질의는 셋이다 — 대조표 한 번, 표식 수 한 번, 전체 장소 수 한 번. 문항 수만큼
	 * 반복하지 않는다.
	 */
	@Transactional(readOnly = true)
	public ConditionCoverageResponse describe() {
		List<UserPlaceCodeMap> rows = this.codeMapRepository.findAll();
		if (rows.isEmpty()) {
			// 대조표가 비어 있으면 할 말이 없다. 빈 목록을 주고, 「자료가 없다」고 단정하지
			// 않는다 — 그 둘은 다른 사실이고, 대조표가 빈 것은 배포가 잘못된 것이지
			// 장소 자료가 없는 것이 아니다.
			return new ConditionCoverageResponse(List.of());
		}

		Set<String> featureTypes = new LinkedHashSet<>();
		for (UserPlaceCodeMap row : rows) {
			featureTypes.add(row.getPlaceFeatureType());
		}
		SCORER_ONLY_FEATURES.values().forEach(featureTypes::addAll);

		Map<String, Long> countByFeatureType = new LinkedHashMap<>();
		for (PlaceFeatureRepository.FeatureTypePlaceCount count
				: this.featureRepository.countPlacesByFeatureType(featureTypes)) {
			countByFeatureType.put(count.getFeatureType(), count.getPlaceCount());
		}

		long totalPlaces = this.placeRepository.count();

		// 문항 하나가 표식 둘에 걸리는 경우가 있어(MOBILITY) 코드로 묶는다. 순서는 대조표가
		// 준 차례 그대로다 — 실행마다 바뀌면 화면의 항목 순서가 새로고침할 때마다 달라진다.
		Map<String, ConditionBuilder> byCode = new LinkedHashMap<>();
		for (UserPlaceCodeMap row : rows) {
			String key = row.getUserInputKind().name() + "/" + row.getUserInputCode();
			ConditionBuilder builder = byCode.computeIfAbsent(key,
					unused -> new ConditionBuilder(row.getUserInputKind().name(), row.getUserInputCode()));
			// 🔴 세어진 줄이 없으면 0 곳이다. 질의가 안 돌려준 갈래가 바로 「자료가 한 곳도
			//    없는」 갈래이고, 이 기능이 말하려는 것이 정확히 그 경우다.
			long placeCount = countByFeatureType.getOrDefault(row.getPlaceFeatureType(), 0L);
			builder.features.add(new ConditionCoverageResponse.Feature(
					row.getPlaceFeatureType(), placeCount, totalPlaces));
		}

		// 채점기만 아는 표식을 그 문항 뒤에 붙인다. 대조표에 그 문항이 없으면 붙이지 않는다 — 없는 문항을 지어내지 않는다.
		SCORER_ONLY_FEATURES.forEach((key, extraTypes) -> {
			ConditionBuilder builder = byCode.get(key);
			if (builder == null) {
				return;
			}
			for (String featureType : extraTypes) {
				boolean already = builder.features.stream().anyMatch(f -> f.featureType().equals(featureType));
				if (!already) {
					builder.features.add(new ConditionCoverageResponse.Feature(featureType,
							countByFeatureType.getOrDefault(featureType, 0L), totalPlaces));
				}
			}
		});

		List<ConditionCoverageResponse.Condition> conditions = new ArrayList<>(byCode.size());
		for (ConditionBuilder builder : byCode.values()) {
			conditions.add(new ConditionCoverageResponse.Condition(
					builder.kind, builder.code, List.copyOf(builder.features)));
		}
		return new ConditionCoverageResponse(List.copyOf(conditions));
	}

	/** 문항 하나를 모으는 동안만 쓴다. */
	private static final class ConditionBuilder {

		private final String kind;

		private final String code;

		private final List<ConditionCoverageResponse.Feature> features = new ArrayList<>();

		private ConditionBuilder(String kind, String code) {
			this.kind = kind;
			this.code = code;
		}
	}
}
