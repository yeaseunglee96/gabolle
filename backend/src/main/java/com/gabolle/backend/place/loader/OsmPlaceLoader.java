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
import com.gabolle.backend.place.repository.PlaceRepository;

/**
 * OpenStreetMap 부산 장소를 {@code place} 에 넣는다.
 *
 * <p><b>왜 이 적재기가 생겼나.</b> 기록(피드)에서 열에 아홉이 장소 없이 저장된다 —
 * {@code story.place_id} 가 39건 중 3건이다. 글 작성 화면이 우리 DB 와 카카오 검색 결과를 섞어
 * 보여주는데, <b>카카오에서 고른 곳은 저장할 수 없기 때문이다</b>
 * ({@code bigData/config/sources.json} 의 금지 출처 — *"Local API 응답 별도 저장 금지
 * (카카오 공식 답변)"*). 그 제약은 버그가 아니라 규칙이고, 그래서 <b>저장해도 되는 자료</b>로
 * 우리 표의 폭을 넓힌다.
 *
 * <p>🔴 <b>ODbL 라이선스다. 출처 표기가 필수다.</b> 화면 어딘가에
 * 「© OpenStreetMap contributors」가 있어야 하고, 파생 DB 를 배포하면 같은 라이선스로 열어야
 * 한다. 적재만 하고 표기를 안 하면 라이선스 위반이다 — 그 표기는 화면 몫이라 이 클래스가 못 한다.
 *
 * <p><b>표식({@code place_feature})은 넣지 않는다.</b> 이 자료에 있는 것은 이름·좌표·갈래뿐이고,
 * 태그를 앱의 취향 낱말로 옮기는 것은 별개의 결정이다. 없는 것을 그럴듯하게 채우면 그건 창작이고,
 * 그 창작을 모델이 배운다. 비어 있으면 그 항이 0 점이 되는 것이 아니라 빠질 뿐이다.
 */
@Component
@Profile({ "db", "dev" })
public class OsmPlaceLoader {

	/** {@code place.source_type}. 어디서 온 행인지 되짚을 때 이 값으로 찾는다. */
	public static final String SOURCE_TYPE = "OSM";

	/**
	 * {@code place.name_ko} 는 VARCHAR(200), {@code address} 는 VARCHAR(300) 이다. 넘치면 DB 가
	 * 거절해 그 덩어리 전체가 롤백된다 — 한 행 때문에 덩어리 전부가 사라진다.
	 */
	private static final int NAME_MAX = 200;

	private static final int ADDRESS_MAX = 300;

	private final PlaceRepository placeRepository;

	private final SamePlaceGuard samePlaceGuard;

	public OsmPlaceLoader(PlaceRepository placeRepository, SamePlaceGuard samePlaceGuard) {
		this.placeRepository = placeRepository;
		this.samePlaceGuard = samePlaceGuard;
	}

	/**
	 * 한 덩어리를 넣고 실제로 넣은 장소 수를 돌려준다.
	 *
	 * <p>🔴 <b>이미 있는 장소는 건너뛰고 고치지 않는다.</b> 관광공사·상가 적재가 먼저 넣은 정본을
	 * 존중한다 — 같은 장소가 두 출처에 있을 때 나중에 온 쪽이 이름을 덮으면, 어느 이름이 맞는지를
	 * 아무도 못 정한다. 같은 파일을 두 번 돌려도 행이 두 배가 되지 않는 것도 같은 규칙이 지킨다.
	 *
	 * <p>「이미 있다」는 둘이다. 먼저 <b>같은 OSM 번호로 들어온 행</b> — 합쳐진 줄(S15P21E201-1619)도 번호가 남아 있어
	 * 여기서 걸리고 되살아나지 않는다. 다음은 <b>다른 번호의 같은 곳</b> — 이름이 같고 가까운 장소가 이미 있으면 넣지
	 * 않는다({@link SamePlaceGuard}, S15P21E201-1620). 운영의 중복 113줄이 이 적재가 상가·관광공사에 이미 있던 곳을 또
	 * 넣어 생겼다. 판정이 애매한 짝(체인·넓은 갈래)도 넣지 않고 {@code report} 에 사람 확인으로 남긴다.
	 */
	@Transactional
	public int saveChunk(List<OsmPoiRow> rows, String datasetVersion, OffsetDateTime collectedAt) {
		return saveChunk(rows, datasetVersion, collectedAt, new SamePlaceReport());
	}

	/** {@link #saveChunk(List, String, OffsetDateTime)} 에 같은 곳 판정을 모을 자리를 준다 — 실행기가 적재 끝에 찍는다. */
	@Transactional
	public int saveChunk(List<OsmPoiRow> rows, String datasetVersion, OffsetDateTime collectedAt,
			SamePlaceReport report) {
		List<UUID> ids = rows.stream().map((row) -> placeIdOf(row.osmId())).toList();
		Set<UUID> existing = new HashSet<>();
		this.placeRepository.findAllById(ids).forEach((place) -> existing.add(place.getPlaceId()));

		List<OsmPoiRow> fresh = new ArrayList<>(rows.size());
		for (OsmPoiRow row : rows) {
			// 이미 있거나(DB) 이 덩어리 안에서 중복된 OSM 번호면 건너뛴다.
			if (existing.add(placeIdOf(row.osmId()))) {
				fresh.add(row);
			}
		}
		List<SamePlaceGuard.Candidate> candidates = fresh.stream()
				.map((row) -> new SamePlaceGuard.Candidate(placeIdOf(row.osmId()), row.name(), row.category(), row.lat(),
						row.lng(), SOURCE_TYPE, String.valueOf(row.osmId())))
				.toList();
		List<SamePlaceGuard.Verdict> verdicts = this.samePlaceGuard.screen(candidates);

		List<Place> places = new ArrayList<>(fresh.size());
		for (int i = 0; i < fresh.size(); i++) {
			OsmPoiRow row = fresh.get(i);
			UUID placeId = placeIdOf(row.osmId());
			SamePlaceGuard.Verdict verdict = verdicts.get(i);
			if (verdict != null) {
				report.record(candidates.get(i), verdict);
				continue;
			}
			places.add(Place.imported(placeId, cut(row.name(), NAME_MAX), row.category(),
					cut(row.address(), ADDRESS_MAX), row.lat(), row.lng(),
					SOURCE_TYPE, String.valueOf(row.osmId()), collectedAt,
					// 원천에 "이 사실이 언제 관측됐나" 칸이 없다. 지어내지 않고 비운다.
					null, datasetVersion));
		}
		if (places.isEmpty()) {
			return 0;
		}
		this.placeRepository.saveAll(places);
		return places.size();
	}

	/**
	 * OSM 노드 번호에서 언제나 같은 장소 아이디를 만든다. 무작위 UUID 를 쓰면 같은 파일을 두 번
	 * 돌릴 때 같은 곳이 두 행이 되고, 그러면 후보 수가 부풀어 백분위가 좋아 보인다.
	 */
	public static UUID placeIdOf(long osmId) {
		return UUID.nameUUIDFromBytes(("gabolle:place:OSM:" + osmId).getBytes(StandardCharsets.UTF_8));
	}

	private static String cut(String value, int max) {
		if (value == null) {
			return null;
		}
		return (value.length() <= max) ? value : value.substring(0, max);
	}
}
