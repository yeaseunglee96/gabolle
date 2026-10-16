package com.gabolle.backend.itinerary.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.preference.application.PreferenceJson;
import com.gabolle.backend.recommendation.application.port.ItineraryDraftCommand.PlannedPlace;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 사용자가 고른 음식 취향이 일정에 <b>최소 하나는</b> 들어가게 하는 규칙의 재료 — S15P21E201-2061.
 *
 * <p>🔴 <b>왜.</b> 음식 취향(돼지국밥·밀면·해산물·카페·디저트·시장 먹거리)은 채점에서 «가산점» 일 뿐이다
 * ({@code BaselineCandidateScorer} 의 {@code TAG_MATCH_CUISINE}). 그런데 일정에 앉는 것은 하루 끼니 수(보통 둘)만큼의
 * 밥집이고, 그 자리를 순위 차례로 채우므로 돼지국밥을 골랐어도 앞 순위의 다른 밥집 둘이 자리를 다 차지하면 일정에
 * 돼지국밥집이 하나도 없었다. 사용자가 고른 것이 말없이 빠지는 것이라 가산점만으로는 약속을 못 지킨다.
 *
 * <p>여기는 <b>순수한 계산</b>만 둔다(저장소를 부르지 않는다) — 어느 음식을 골랐나, 후보가 어느 음식인가, 일정이 어느
 * 음식을 덮었나. 어떻게 앉히나는 {@link ItineraryDraftService} 가 한다.
 *
 * <p>🔴 <b>후보에 없는 음식은 지어내지 않는다.</b> 조건(알레르기·식단·이동)을 통과한 후보만 {@code places} 로 들어오므로 여기서
 * 고르는 곳은 전부 통과한 곳이다. 그 음식의 후보가 하나도 없으면(예: 시장 먹거리는 상가 자료에 표식이 없다) 건너뛰고 부르는
 * 쪽이 로그에 남긴다.
 */
final class FoodPreferencePicks {

	/** 음식 취향 답이 담기는 차원 이름 — 채점기가 읽는 것과 같다({@code BaselineCandidateScorer}). */
	static final String DIMENSION = "FOOD_PREFERENCE";

	/** 장소 쪽의 짝 — 음식 종류 표식의 유형. 상가정보의 업종·상호명에서 적재기({@code AppFoodVocabulary})가 붙인다. */
	static final String FEATURE_TYPE = "CUISINE_TAG";

	/**
	 * 음식 취향 코드 가운데 «어떤 음식인가» 가 아니라 «식단» 인 것. 후보를 거르는 조건({@code withTasteDiets})이 이미 처리했고,
	 * 음식 종류 표식으로는 짝지을 수 없어 여기서 «찾을 음식» 으로 세면 늘 후보 없음이 된다.
	 */
	private static final Set<String> DIET_ONLY = Set.of("VEGETARIAN", "HALAL");

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	private FoodPreferencePicks() {
	}

	/**
	 * 이 일정을 짜는 데 쓰는 것.
	 *
	 * @param chosen 사용자가 고른 음식(순서는 고른 차례, 식단 낱말은 뺀다). 안 골랐으면 빈 목록
	 * @param cuisinesByPlace 후보마다 음식 종류 표식 — 표식이 없는 후보는 키가 없다
	 */
	record Picks(List<String> chosen, Map<UUID, Set<String>> cuisinesByPlace) {

		static final Picks NONE = new Picks(List.of(), Map.of());

		boolean none() {
			return this.chosen.isEmpty();
		}

		/** 이 장소가 고른 음식 가운데 덮는 것. 없으면 빈 집합. */
		Set<String> coveredBy(UUID placeId) {
			Set<String> cuisines = this.cuisinesByPlace.get(placeId);
			if (cuisines == null || cuisines.isEmpty()) {
				return Set.of();
			}
			Set<String> covered = new LinkedHashSet<>();
			for (String code : this.chosen) {
				if (cuisines.contains(code)) {
					covered.add(code);
				}
			}
			return covered;
		}
	}

	/**
	 * 여행의 선호 판에서 고른 음식을 읽는다. 읽는 규칙은 채점기와 같은 {@link PreferenceJson} 한 곳이다 — 둘로 갈리면 추천은
	 * 「돼지국밥」으로 받았는데 일정은 「안 골랐음」으로 읽는 일이 생긴다. 안 골랐거나 못 읽으면 빈 목록.
	 *
	 * @param snapshot {@code TripRepository.findLatestSnapshot} 의 결과. {@code null} 이어도 된다
	 */
	static List<String> chosenOf(PreferenceSnapshot snapshot) {
		List<String> chosen = new ArrayList<>();
		for (String code : PreferenceJson.codesFor(snapshot, DIMENSION, MAPPER)) {
			if (!DIET_ONLY.contains(code) && !chosen.contains(code)) {
				chosen.add(code);
			}
		}
		return List.copyOf(chosen);
	}

	/**
	 * 후보의 음식 종류 표식. 「있다고 확인된」 표식만 센다 — 채점기가 쓰는 것과 같은 판정({@link PlaceFeature#indicatesPresence})이라
	 * 순위에 가산점을 준 곳과 여기서 «그 음식» 이라 부르는 곳이 어긋나지 않는다.
	 */
	static Map<UUID, Set<String>> cuisinesOf(Collection<PlaceFeature> features) {
		Map<UUID, Set<String>> out = new HashMap<>();
		if (features == null) {
			return out;
		}
		for (PlaceFeature feature : features) {
			if (FEATURE_TYPE.equals(feature.getFeatureType()) && feature.getFeatureKey() != null
					&& feature.indicatesPresence()) {
				out.computeIfAbsent(feature.getPlaceId(), id -> new LinkedHashSet<>()).add(feature.getFeatureKey());
			}
		}
		return out;
	}

	/**
	 * 고른 음식마다 그 음식인 후보를 <b>들어온 차례 그대로</b>(순위 차례) 모은다. 후보가 없는 음식은 빈 목록으로 키가 남는다 —
	 * 부르는 쪽이 «후보 없음» 을 로그로 남기려면 그 음식이 골라졌다는 사실이 있어야 한다.
	 */
	static Map<String, List<PlannedPlace>> candidatesByCuisine(Picks picks, List<PlannedPlace> places) {
		Map<String, List<PlannedPlace>> out = new LinkedHashMap<>();
		for (String code : picks.chosen()) {
			out.put(code, new ArrayList<>());
		}
		for (PlannedPlace place : places) {
			Set<String> cuisines = picks.cuisinesByPlace().get(place.placeId());
			if (cuisines == null) {
				continue;
			}
			for (String code : picks.chosen()) {
				if (cuisines.contains(code)) {
					out.get(code).add(place);
				}
			}
		}
		return out;
	}

	/** 일정에 들어간 곳들이 덮은 음식(고른 차례). */
	static Set<String> covered(Picks picks, Collection<UUID> placedIds) {
		Set<UUID> placed = new HashSet<>(placedIds);
		Set<String> covered = new LinkedHashSet<>();
		for (String code : picks.chosen()) {
			for (UUID id : placed) {
				Set<String> cuisines = picks.cuisinesByPlace().get(id);
				if (cuisines != null && cuisines.contains(code)) {
					covered.add(code);
					break;
				}
			}
		}
		return covered;
	}
}
