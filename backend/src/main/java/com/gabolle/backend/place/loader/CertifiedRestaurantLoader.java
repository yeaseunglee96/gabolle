package com.gabolle.backend.place.loader;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.domain.CertifiedRestaurant;
import com.gabolle.backend.place.domain.CertifiedRestaurant.Source;
import com.gabolle.backend.place.domain.CurationStatus;
import com.gabolle.backend.place.domain.InterestTagCode;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.PlaceEvidenceStatus;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlaceRepository;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공공기관이 인증한 부산 식당을 {@code place} 와 {@code place_feature} 에 넣는다 (S15P21E201-2051).
 * {@link RedtablePlaceLoader} 를 본떴다 — 번호에서 언제나 같은 장소 id, 두 번 돌려도 행이 안 늚, 같은 곳이 이미 있으면
 * ({@link SamePlaceGuard}) 새로 만들지 않음. 다른 점은 <b>장소보다 표식이 본체</b>라는 것이다.
 *
 * <h4>줄마다 하는 일</h4>
 * <ol>
 *   <li>{@code existingPlaceId} 가 있다 → 그 장소는 이미 DB 에 있다. <b>장소는 새로 안 만들고 표식만 단다.</b> 그 번호의
 *       장소가 없거나(다른 DB) 추천에서 빠진 곳(숨김·사용자가 고른 곳)이면 안 달고 센다. 합쳐진 줄이면 남은 줄에 단다</li>
 *   <li>없다 → 번호에서 계산한 id({@link #placeIdOf})로 새 장소를 만든다. 갈래 {@code FOOD}, 출처
 *       {@link CertifiedRestaurant#SOURCE_TYPE}, 공공 사진이 있으면 사진과 출처 문구 그대로</li>
 *   <li>새로 만들기 전에 같은 이름이 150m 안에 이미 있는지 본다. <b>같은 곳이면</b> 새로 만들지 않고 그 장소에 표식을
 *       단다(수집 쪽 짝짓기가 놓친 것을 여기서 주워 담는다). 체인 가게나 해변·공원처럼 사람이 봐야 하는 짝은 넣지도 달지도
 *       않고 {@link SamePlaceReport} 에 남긴다</li>
 * </ol>
 *
 * <h4>다는 표식</h4> 장소마다 {@link CertifiedRestaurant} 에 적힌 셋이다 — 맛집 갈래({@code INTEREST_TAG}), 구·동
 * ({@link CertifiedRestaurant#AREA_FEATURE_TYPE}), 인증한 목록마다 한 줄({@link CertifiedRestaurant#SOURCE_FEATURE_TYPE}).
 * 새 장소에는 갈래 표식({@code CATEGORY_TAG} = {@code FOOD})이 하나 더 붙는다. 표식 id 도 장소와 종류·열쇠에서 계산하고,
 * 이미 있는 표식은 건드리지 않는다. 한 장소에는 구·동이 하나뿐이다 — 이미 있으면 다른 줄이 다른 곳을 말해도 먼저 있던 것을 둔다
 * (세어서 {@link Result#areaConflicts()} 로 알린다).
 *
 * <p>모든 표식의 {@code source_type} 은 {@link CertifiedRestaurant#SOURCE_TYPE}, {@code source_version} 은 수집분 버전이라
 * 되돌릴 때 이 둘로 걸린다 — 이미 있던 장소에 단 표식까지 한 번에 지워진다.
 */
@Component
@Profile({ "db", "dev" })
public class CertifiedRestaurantLoader {

	public static final String SOURCE_TYPE = CertifiedRestaurant.SOURCE_TYPE;

	/** 새 장소의 갈래 — 앱의 여섯 갈래 가운데 음식. */
	static final String CATEGORY = "FOOD";

	private static final String CATEGORY_TAG = "CATEGORY_TAG";

	private static final int NAME_MAX = 200;

	private static final int ADDRESS_MAX = 300;

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	private final PlaceRepository placeRepository;

	private final PlaceFeatureRepository placeFeatureRepository;

	private final SamePlaceGuard samePlaceGuard;

	public CertifiedRestaurantLoader(PlaceRepository placeRepository, PlaceFeatureRepository placeFeatureRepository,
			SamePlaceGuard samePlaceGuard) {
		this.placeRepository = placeRepository;
		this.placeFeatureRepository = placeFeatureRepository;
		this.samePlaceGuard = samePlaceGuard;
	}

	/**
	 * 한 덩어리를 넣는다. 두 번 돌려도 행이 안 는다 — 장소는 번호에서 계산한 id 로 찾고, 표식은 (장소, 종류, 열쇠) 가
	 * 같은 것이 있으면 안 넣는다. 한 덩어리는 한 트랜잭션이라 중간에 죽으면 그 덩어리만 되돌려지고, 다시 돌리면 이어진다.
	 */
	@Transactional
	public Result saveChunk(List<CertifiedRestaurantRow> rows, String datasetVersion, OffsetDateTime collectedAt,
			SamePlaceReport report) {
		Tally tally = new Tally();

		// 1. 이미 있는 장소를 한 번에 읽는다 — 줄마다 묻지 않는다.
		Set<UUID> wanted = new HashSet<>();
		for (CertifiedRestaurantRow row : rows) {
			wanted.add(placeIdOf(row.sourceId()));
			if (row.existingPlaceId() != null) {
				wanted.add(row.existingPlaceId());
			}
		}
		Map<UUID, Place> known = loadWithSurvivors(wanted, new HashMap<>());

		// 2. 줄마다 표식을 달 장소를 정한다. 새로 만들 줄은 따로 모은다.
		Map<UUID, Target> targets = new LinkedHashMap<>();
		List<CertifiedRestaurantRow> toCreate = new ArrayList<>();
		for (CertifiedRestaurantRow row : rows) {
			if (row.existingPlaceId() != null) {
				Place place = known.get(row.existingPlaceId());
				if (place == null) {
					tally.missingExisting.add(row.sourceId());
					continue;
				}
				Place usable = usable(known, place);
				if (usable == null) {
					tally.notUsable++;
					continue;
				}
				targets.computeIfAbsent(usable.getPlaceId(), id -> new Target()).add(row);
				tally.markedExisting++;
				continue;
			}
			Place own = known.get(placeIdOf(row.sourceId()));
			if (own == null) {
				toCreate.add(row);
				continue;
			}
			// 지난 적재가 만든 장소 — 다시 돌릴 때다. 표식이 빠졌다면 채운다.
			Place usable = usable(known, own);
			if (usable == null) {
				tally.notUsable++;
				continue;
			}
			targets.computeIfAbsent(usable.getPlaceId(), id -> new Target()).add(row);
			tally.alreadyThere++;
		}

		// 3. 새로 만들 줄은 같은 곳이 이미 있는지 먼저 본다.
		List<CertifiedRestaurantRow> created = screenNewPlaces(toCreate, known, targets, report, tally);

		// 4. 새 장소와 그 갈래 표식.
		List<Place> places = new ArrayList<>(created.size());
		List<PlaceFeature> features = new ArrayList<>();
		Set<UUID> createdIds = new HashSet<>();
		for (CertifiedRestaurantRow row : created) {
			UUID placeId = placeIdOf(row.sourceId());
			createdIds.add(placeId);
			places.add(Place.imported(placeId, cut(PlaceNames.primary(row.name()), NAME_MAX), CATEGORY,
					cut(row.address(), ADDRESS_MAX), row.lat(), row.lng(), SOURCE_TYPE, row.sourceId(), collectedAt,
					// 원천에 「이 사실이 언제 관측됐나」 칸이 없다. 지어내지 않고 비운다.
					null, datasetVersion, PhotoUrlScheme.secure(row.photoUrl()), row.photoSource()));
			features.add(PlaceFeature.imported(featureIdOf(placeId, CATEGORY_TAG, CATEGORY), placeId, CATEGORY_TAG,
					CATEGORY, "true",
					// 인증 목록이 식당이라고 한 것에서 옮긴 갈래다. 가게에 직접 확인한 것이 아니다.
					PlaceEvidenceStatus.ESTIMATED, SOURCE_TYPE, row.sourceId(), null, datasetVersion, collectedAt));
			if (row.photoUrl() != null) {
				tally.withPhoto++;
			}
		}

		// 5. 맛집·구동·인증 목록 표식. 이미 있는 장소의 표식은 읽어서 겹치는 것을 거른다.
		Map<UUID, List<PlaceFeature>> stored = new HashMap<>();
		List<UUID> oldTargets = targets.keySet().stream().filter(id -> !createdIds.contains(id)).toList();
		if (!oldTargets.isEmpty()) {
			for (PlaceFeature feature : this.placeFeatureRepository.findByPlaceIdIn(oldTargets)) {
				stored.computeIfAbsent(feature.getPlaceId(), id -> new ArrayList<>()).add(feature);
			}
		}
		for (Map.Entry<UUID, Target> entry : targets.entrySet()) {
			addMarks(entry.getKey(), entry.getValue(), stored.getOrDefault(entry.getKey(), List.of()), features,
					datasetVersion, collectedAt, tally);
		}

		if (!places.isEmpty()) {
			this.placeRepository.saveAll(places);
		}
		if (!features.isEmpty()) {
			this.placeFeatureRepository.saveAll(features);
		}
		return new Result(places.size(), tally.markedExisting, tally.alreadyThere, tally.markedSamePlace,
				tally.missingExisting.size(), tally.notUsable, tally.featuresAdded, tally.featuresAlready,
				tally.areaConflicts, tally.withPhoto, List.copyOf(tally.missingExisting),
				List.copyOf(tally.sameExamples));
	}

	/**
	 * 새로 만들 줄 가운데 진짜 새로 만들 줄만 남긴다. 같은 곳({@code SAME})이 이미 있고 추천에 쓰는 장소면 그 장소에 표식만
	 * 달도록 {@code targets} 에 올리고, 사람이 볼 짝이거나 쓸 수 없는 장소면 {@code report} 에 남긴다.
	 */
	private List<CertifiedRestaurantRow> screenNewPlaces(List<CertifiedRestaurantRow> toCreate, Map<UUID, Place> known,
			Map<UUID, Target> targets, SamePlaceReport report, Tally tally) {
		if (toCreate.isEmpty()) {
			return List.of();
		}
		List<SamePlaceGuard.Candidate> candidates = toCreate.stream()
				.map(row -> new SamePlaceGuard.Candidate(placeIdOf(row.sourceId()), PlaceNames.primary(row.name()),
						CATEGORY, row.lat(), row.lng(), SOURCE_TYPE, row.sourceId()))
				.toList();
		List<SamePlaceGuard.Verdict> verdicts = this.samePlaceGuard.screen(candidates);

		Set<UUID> candidateIds = new HashSet<>();
		candidates.forEach(candidate -> candidateIds.add(candidate.placeId()));
		Set<UUID> sameIds = new HashSet<>();
		for (SamePlaceGuard.Verdict verdict : verdicts) {
			if (verdict != null && verdict.kind() == SamePlaceRule.Kind.SAME && !candidateIds.contains(verdict.existingPlaceId())) {
				sameIds.add(verdict.existingPlaceId());
			}
		}
		loadWithSurvivors(sameIds, known);

		List<CertifiedRestaurantRow> created = new ArrayList<>(toCreate.size());
		for (int i = 0; i < toCreate.size(); i++) {
			CertifiedRestaurantRow row = toCreate.get(i);
			SamePlaceGuard.Verdict verdict = verdicts.get(i);
			if (verdict == null) {
				created.add(row);
				targets.computeIfAbsent(placeIdOf(row.sourceId()), id -> new Target()).add(row);
				continue;
			}
			if (verdict.kind() == SamePlaceRule.Kind.SAME) {
				UUID sameId = verdict.existingPlaceId();
				if (candidateIds.contains(sameId)) {
					// 같은 덩어리에서 앞서 받아들인 줄과 같은 곳이다 — 그 장소에 표식을 합친다.
					targets.computeIfAbsent(sameId, id -> new Target()).add(row);
					tally.markedSamePlace++;
					tally.noteSame(row, verdict);
					continue;
				}
				Place same = known.get(sameId);
				Place usable = (same == null) ? null : usable(known, same);
				if (usable != null) {
					targets.computeIfAbsent(usable.getPlaceId(), id -> new Target()).add(row);
					tally.markedSamePlace++;
					tally.noteSame(row, verdict);
					continue;
				}
			}
			// 사람이 볼 짝이거나, 같은 곳이 추천에서 빠진 곳(합쳐진·숨긴·사용자가 고른 곳)이다 — 새로 만들면 합치기·숨김이
			// 없던 일이 되므로 넣지 않고 남긴다.
			report.record(candidates.get(i), verdict);
		}
		return created;
	}

	/** 한 장소에 달 표식을 {@code features} 에 더한다. 이미 있는 것은 더하지 않는다. */
	private void addMarks(UUID placeId, Target target, List<PlaceFeature> stored, List<PlaceFeature> features,
			String datasetVersion, OffsetDateTime collectedAt, Tally tally) {
		String sourceId = target.firstSourceId();

		// 맛집 갈래.
		if (addIfAbsent(features, stored, placeId, InterestTagCode.FEATURE_TYPE, CertifiedRestaurant.EXPLORE_KEY,
				"true",
				// 인증 목록에 오른 것은 기관이 확인해 준 사실이다.
				PlaceEvidenceStatus.VERIFIED, sourceId, datasetVersion, collectedAt)) {
			tally.featuresAdded++;
		}
		else {
			tally.featuresAlready++;
		}

		// 구·동 — 한 장소에 하나. 이미 있으면 열쇠가 달라도 새로 달지 않는다.
		CertifiedRestaurant.Area area = target.firstArea();
		String areaKey = area.key();
		boolean hasAnyArea = stored.stream()
				.anyMatch(feature -> CertifiedRestaurant.AREA_FEATURE_TYPE.equals(feature.getFeatureType()));
		if (hasAnyArea) {
			tally.featuresAlready++;
			boolean sameArea = stored.stream().anyMatch(feature -> CertifiedRestaurant.AREA_FEATURE_TYPE
					.equals(feature.getFeatureType()) && areaKey.equals(feature.getFeatureKey()));
			if (!sameArea) {
				tally.areaConflicts++;
			}
		}
		else {
			Map<String, String> value = new LinkedHashMap<>();
			value.put("gu", area.gu());
			value.put("dong", area.dong());
			features.add(PlaceFeature.imported(featureIdOf(placeId, CertifiedRestaurant.AREA_FEATURE_TYPE, areaKey),
					placeId, CertifiedRestaurant.AREA_FEATURE_TYPE, areaKey, MAPPER.writeValueAsString(value),
					// 좌표를 역지오코딩해 얻은 법정동이다 — 기관이 적은 주소가 아니라 우리가 계산한 값이다.
					PlaceEvidenceStatus.ESTIMATED, SOURCE_TYPE, sourceId, null, datasetVersion, collectedAt));
			tally.featuresAdded++;
		}

		// 인증한 목록마다 한 줄.
		for (Source source : target.sources()) {
			Map<String, String> value = new LinkedHashMap<>();
			value.put("source", source.fileLabel());
			if (addIfAbsent(features, stored, placeId, CertifiedRestaurant.SOURCE_FEATURE_TYPE, source.name(),
					MAPPER.writeValueAsString(value), PlaceEvidenceStatus.VERIFIED, sourceId, datasetVersion,
					collectedAt)) {
				tally.featuresAdded++;
			}
			else {
				tally.featuresAlready++;
			}
		}
	}

	private boolean addIfAbsent(List<PlaceFeature> features, List<PlaceFeature> stored, UUID placeId, String featureType,
			String featureKey, String value, PlaceEvidenceStatus status, String sourceId, String datasetVersion,
			OffsetDateTime collectedAt) {
		boolean present = stored.stream().anyMatch(
				feature -> featureType.equals(feature.getFeatureType()) && featureKey.equals(feature.getFeatureKey()));
		if (present) {
			return false;
		}
		features.add(PlaceFeature.imported(featureIdOf(placeId, featureType, featureKey), placeId, featureType,
				featureKey, value, status, SOURCE_TYPE, sourceId, null, datasetVersion, collectedAt));
		return true;
	}

	/**
	 * 장소를 읽고, 합쳐진 줄이면 남은 줄도 함께 읽는다 — 수집 쪽이 짝지은 번호가 그 뒤에 합쳐졌을 수 있다. 이미 읽은 것은 다시
	 * 읽지 않는다.
	 */
	private Map<UUID, Place> loadWithSurvivors(Collection<UUID> ids, Map<UUID, Place> into) {
		List<UUID> missing = ids.stream().filter(id -> !into.containsKey(id)).toList();
		if (!missing.isEmpty()) {
			this.placeRepository.findAllById(missing).forEach(place -> into.put(place.getPlaceId(), place));
		}
		Set<UUID> survivors = new HashSet<>();
		for (UUID id : ids) {
			Place place = into.get(id);
			if (place != null && place.getCurationStatus() == CurationStatus.MERGED && place.getMergedInto() != null
					&& !into.containsKey(place.getMergedInto())) {
				survivors.add(place.getMergedInto());
			}
		}
		if (!survivors.isEmpty()) {
			this.placeRepository.findAllById(survivors).forEach(place -> into.put(place.getPlaceId(), place));
		}
		return into;
	}

	/** 표식을 달아도 되는 장소 — 추천에 쓰는 곳({@code CURATED})이다. 합쳐진 줄은 남은 줄을 따라간다. 아니면 {@code null}. */
	private static Place usable(Map<UUID, Place> known, Place place) {
		Place target = place;
		if (target.getCurationStatus() == CurationStatus.MERGED && target.getMergedInto() != null) {
			target = known.get(target.getMergedInto());
		}
		return (target != null && target.getCurationStatus() == CurationStatus.CURATED) ? target : null;
	}

	/** 번호에서 언제나 같은 장소 id — 앞머리가 다른 출처와 겹치지 않게 한다. */
	public static UUID placeIdOf(String sourceId) {
		return UUID.nameUUIDFromBytes(("gabolle:place:" + SOURCE_TYPE + ":" + sourceId).getBytes(StandardCharsets.UTF_8));
	}

	/** 표식 id — 장소와 종류·열쇠에서 계산한다. 같은 장소에 같은 표식은 언제나 같은 id 다. */
	static UUID featureIdOf(UUID placeId, String featureType, String featureKey) {
		return UUID.nameUUIDFromBytes(("gabolle:place_feature:" + SOURCE_TYPE + ":" + placeId + ":" + featureType + ":"
				+ featureKey).getBytes(StandardCharsets.UTF_8));
	}

	private static String cut(String value, int max) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
	}

	/** 한 장소에 달 것 — 이 장소로 모인 줄들. 파일에서 먼저 나온 줄이 구·동과 출처 번호를 정한다. */
	private static final class Target {

		private final List<CertifiedRestaurantRow> rows = new ArrayList<>();

		void add(CertifiedRestaurantRow row) {
			this.rows.add(row);
		}

		String firstSourceId() {
			return this.rows.get(0).sourceId();
		}

		CertifiedRestaurant.Area firstArea() {
			return this.rows.get(0).area();
		}

		/** 줄들이 말한 인증 목록의 합집합 — 선언 순서로 정렬해 늘 같은 순서다. */
		Set<Source> sources() {
			Set<Source> all = new TreeSet<>(Comparator.comparing(Source::ordinal));
			this.rows.forEach(row -> all.addAll(row.sources()));
			return all;
		}
	}

	/** 한 덩어리가 지나며 센 것. */
	private static final class Tally {

		int markedExisting;

		int alreadyThere;

		int markedSamePlace;

		int notUsable;

		int featuresAdded;

		int featuresAlready;

		int areaConflicts;

		int withPhoto;

		final List<String> missingExisting = new ArrayList<>();

		final List<String> sameExamples = new ArrayList<>();

		void noteSame(CertifiedRestaurantRow row, SamePlaceGuard.Verdict verdict) {
			if (this.sameExamples.size() < 10) {
				this.sameExamples.add("%s(%s) = %s(%s) %dm".formatted(row.name(), row.sourceId(), verdict.existingName(),
						verdict.existingSource(), Math.round(verdict.distanceM())));
			}
		}
	}

	/**
	 * 한 덩어리의 결과.
	 *
	 * @param created 새로 만든 장소
	 * @param markedExisting {@code existingPlaceId} 가 가리키는 장소에 표식을 달러 간 줄 수(표식이 이미 있어도 센다)
	 * @param alreadyThere 지난 적재가 만든 장소를 다시 만난 줄 수 — 두 번째 돌릴 때 이 수가 늘고 {@code created} 는 0 이다
	 * @param markedSamePlace 같은 곳이 이미 있어 새로 안 만들고 표식만 단 줄 수
	 * @param missingExisting {@code existingPlaceId} 의 장소가 이 DB 에 없는 줄 수 — 다른 DB 의 번호로 만든 파일이라는 뜻일 수 있다
	 * @param notUsable 장소는 있는데 추천에서 빠진 곳(숨김·사용자가 고른 곳·합쳐졌는데 남은 줄도 못 씀)이라 안 단 줄 수
	 * @param featuresAdded 새로 단 표식 수
	 * @param featuresAlready 이미 있어 안 단 표식 수
	 * @param areaConflicts 이미 단 구·동과 이번 줄의 구·동이 달라 먼저 있던 것을 둔 장소 수
	 * @param withPhoto 사진을 붙여 만든 새 장소 수
	 */
	public record Result(int created, int markedExisting, int alreadyThere, int markedSamePlace, int missingExisting,
			int notUsable, int featuresAdded, int featuresAlready, int areaConflicts, int withPhoto,
			List<String> missingExistingSourceIds, List<String> sameExamples) {

		public static Result empty() {
			return new Result(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of());
		}

		public Result plus(Result other) {
			List<String> missing = new ArrayList<>(this.missingExistingSourceIds);
			missing.addAll(other.missingExistingSourceIds);
			List<String> same = new ArrayList<>(this.sameExamples);
			other.sameExamples.stream().filter(example -> same.size() < 10).forEach(same::add);
			return new Result(this.created + other.created, this.markedExisting + other.markedExisting,
					this.alreadyThere + other.alreadyThere, this.markedSamePlace + other.markedSamePlace,
					this.missingExisting + other.missingExisting, this.notUsable + other.notUsable,
					this.featuresAdded + other.featuresAdded, this.featuresAlready + other.featuresAlready,
					this.areaConflicts + other.areaConflicts, this.withPhoto + other.withPhoto, List.copyOf(missing),
					List.copyOf(same));
		}

		@Override
		public String toString() {
			return ("새 장소 %d곳(사진 %d) · 있던 장소에 표식 %d줄 · 지난 적재의 장소 다시 만남 %d줄 · 같은 곳이라 표식만 %d줄 · "
					+ "번호의 장소가 이 DB 에 없음 %d줄 · 추천에서 빠진 곳이라 안 단 %d줄 · 새 표식 %d · 이미 있던 표식 %d · "
					+ "구·동이 달라 먼저 것을 둠 %d곳").formatted(this.created, this.withPhoto, this.markedExisting,
							this.alreadyThere, this.markedSamePlace, this.missingExisting, this.notUsable,
							this.featuresAdded, this.featuresAlready, this.areaConflicts);
		}
	}
}
