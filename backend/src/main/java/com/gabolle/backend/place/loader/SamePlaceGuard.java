package com.gabolle.backend.place.loader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.service.GeoDistance;

/**
 * 적재기가 새 장소를 넣기 전에 이미 있는 같은 곳을 찾는다 (S15P21E201-1620). 판정은 {@link SamePlaceRule} 이 하고, 여기서는
 * 견줄 장소를 모은다.
 *
 * <p>덩어리 하나를 한 번에 본다 — 후보마다 DB 를 물으면 질의 수가 줄 수만큼 는다. 견주는 대상은 둘이다.
 * <ul>
 *   <li>DB 에 이미 있는 장소 — 상태를 가리지 않는다({@link PlaceRepository#findNamedSpotsWithin})</li>
 *   <li>같은 덩어리에서 앞서 받아들인 후보 — 한 파일 안에 같은 곳이 두 줄로 있을 수 있다(오픈스트리트맵의 점과
 *       테두리, 상가의 같은 가게 두 번호)</li>
 * </ul>
 */
@Component
@Profile({ "db", "dev" })
public class SamePlaceGuard {

	/** 1km 를 위도로. 경도 쪽은 위도에 따라 넓힌다. */
	private static final double WIDE_LAT_DEG = SamePlaceRule.WIDE_M / 111_000.0;

	private final PlaceRepository placeRepository;

	public SamePlaceGuard(PlaceRepository placeRepository) {
		this.placeRepository = placeRepository;
	}

	/** 넣으려는 장소 하나. */
	public record Candidate(UUID placeId, String name, String category, double lat, double lng, String sourceType,
			String sourceId) {
	}

	/** 견준 상대와 판정. */
	public record Verdict(SamePlaceRule.Kind kind, UUID existingPlaceId, String existingName, String existingCategory,
			String existingSource, double distanceM) {
	}

	/**
	 * @return 후보와 같은 차례의 판정. 넣어도 되면 그 자리가 {@code null} 이다
	 */
	public List<Verdict> screen(List<Candidate> candidates) {
		List<Verdict> verdicts = new ArrayList<>(candidates.size());
		if (candidates.isEmpty()) {
			return verdicts;
		}
		Map<String, List<Spot>> byName = new HashMap<>();
		for (Spot spot : existingAround(candidates)) {
			byName.computeIfAbsent(SamePlaceRule.nameKey(spot.name()), (key) -> new ArrayList<>()).add(spot);
		}
		for (Candidate candidate : candidates) {
			String key = SamePlaceRule.nameKey(candidate.name());
			List<Spot> sameName = key.isEmpty() ? List.of() : byName.getOrDefault(key, List.of());
			Verdict verdict = strongest(candidate, sameName);
			verdicts.add(verdict);
			if (verdict == null && !key.isEmpty()) {
				// 받아들였다 — 같은 덩어리의 뒤 줄은 이것과도 견준다.
				byName.computeIfAbsent(key, (k) -> new ArrayList<>()).add(new Spot(candidate.placeId(),
						candidate.name(), candidate.category(), candidate.lat(), candidate.lng(), candidate.sourceType()));
			}
		}
		return verdicts;
	}

	/** 「같다」가 「사람 확인」보다 먼저고, 같은 종류면 가까운 쪽이다. */
	private static Verdict strongest(Candidate candidate, List<Spot> sameName) {
		Verdict best = null;
		for (Spot spot : sameName) {
			double distance = GeoDistance.meters(candidate.lat(), candidate.lng(), spot.lat(), spot.lng());
			SamePlaceRule.Kind kind = SamePlaceRule.judge(candidate.name(), candidate.category(), spot.name(),
					spot.category(), distance);
			if (kind == null) {
				continue;
			}
			Verdict verdict = new Verdict(kind, spot.placeId(), spot.name(), spot.category(), spot.sourceType(), distance);
			if (best == null || kind.ordinal() < best.kind().ordinal()
					|| (kind == best.kind() && distance < best.distanceM())) {
				best = verdict;
			}
		}
		return best;
	}

	/** 후보들을 덮는 경계상자를 1km 넓혀 그 안의 장소를 한 번에 읽는다. */
	private List<Spot> existingAround(List<Candidate> candidates) {
		double minLat = Double.MAX_VALUE;
		double maxLat = -Double.MAX_VALUE;
		double minLng = Double.MAX_VALUE;
		double maxLng = -Double.MAX_VALUE;
		for (Candidate c : candidates) {
			minLat = Math.min(minLat, c.lat());
			maxLat = Math.max(maxLat, c.lat());
			minLng = Math.min(minLng, c.lng());
			maxLng = Math.max(maxLng, c.lng());
		}
		double lngDeg = WIDE_LAT_DEG / Math.cos(Math.toRadians(Math.max(Math.abs(minLat), Math.abs(maxLat))));
		List<Spot> spots = new ArrayList<>();
		for (PlaceRepository.NamedSpot s : this.placeRepository.findNamedSpotsWithin(minLat - WIDE_LAT_DEG,
				maxLat + WIDE_LAT_DEG, minLng - lngDeg, maxLng + lngDeg)) {
			if (s.getLat() != null && s.getLng() != null) {
				spots.add(new Spot(s.getPlaceId(), s.getNameKo(), s.getCategory(), s.getLat(), s.getLng(),
						s.getSourceType()));
			}
		}
		return spots;
	}

	private record Spot(UUID placeId, String name, String category, double lat, double lng, String sourceType) {
	}
}
