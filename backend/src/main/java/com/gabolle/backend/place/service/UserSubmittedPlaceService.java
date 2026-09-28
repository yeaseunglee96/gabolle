package com.gabolle.backend.place.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;

/**
 * 사용자가 기록에 붙이려고 고른 장소를 우리 표의 장소로 바꾼다 — S15P21E201-1426.
 *
 * <p><b>왜 있나.</b> 글 작성 화면은 우리 DB 장소와 카카오 검색 결과를 섞어 보여주는데 카카오
 * 결과에는 {@code placeId} 가 없다. 그래서 그것을 고른 기록은 장소와 안 이어지고,
 * {@code story.place_id} 는 기록과 장소 자료를 잇는 유일한 다리라 비어 있으면 글 순위
 * 개인화·{@code STORY_LIKE} 를 취향 신호로 쓰기·장소 기준 기록 묶어보기가 전부 성립하지 않는다.
 * 2026-09-23 운영 실측에서 원글 28건 중 5건에만 채워져 있었다.
 *
 * <p><b>원칙은 그대로다.</b> 앱이 우리 표에 없는 식별자를 저장하는 것이 아니라, 서버가 먼저
 * 행을 만든 뒤 그 {@code place_id} 를 쓴다.
 */
@Service
@Profile({ "db", "dev" })
public class UserSubmittedPlaceService {

	private final PlaceRepository places;

	private final Clock clock;

	public UserSubmittedPlaceService(PlaceRepository places, Clock clock) {
		this.places = places;
		this.clock = clock;
	}

	/**
	 * 사용자가 고른 장소 한 건. 앱이 검색 결과에서 그대로 옮겨 담는 값이다.
	 *
	 * @param sourceType 어느 검색이 준 값인가. 앱의 {@code OriginCandidate.source} 와 같은 어휘다
	 *     ({@code KAKAO_LOCAL} · {@code INTERNAL_FALLBACK})
	 * @param externalId 그 원천에서의 식별자. 이것이 같으면 같은 장소다
	 */
	public record Snapshot(String sourceType, String externalId, String name, String address,
			Double lat, Double lng, String category) {
	}

	/**
	 * 있으면 찾고 없으면 만든다.
	 *
	 * <p>🔴 <b>찾을 때 {@code curationStatus} 를 안 본다.</b> 사용자가 카카오에서
	 * 해운대해수욕장을 고르면 {@code (KAKAO_LOCAL, '7913306')} 인 <b>큐레이션 행</b>이 이미
	 * 있고({@code V20260916210000}), 그 행에 이어야 한다. 사용자가 고른 것만 따로 찾으면
	 * 같은 장소가 두 행이 되고 「이 장소의 기록 모아보기」가 반씩 갈린다.
	 *
	 * <p>🔴 <b>새로 만든 행은 {@code USER_SUBMITTED} 다.</b> 이름·주소·좌표가 남의 검색 결과
	 * 그대로이고 아무도 안 봤다. 그래서 추천 후보와 검색에서 빠진다 — 거르는 조건은
	 * {@code PlaceRepository} 에 있다.
	 *
	 * @return 이을 장소. 이미 있던 것일 수도 있고 방금 만든 것일 수도 있다
	 */
	@Transactional
	public Place findOrCreate(Snapshot snapshot) {
		// 🔴 출처는 대문자로 맞춘다. uq_place_source 는 글자 그대로 비교하므로 kakao_local 이
		//    한 번 섞여 들어오면 같은 장소가 두 행이 된다. 표에 있는 값은 전부 대문자다
		//    (OSM · TOURAPI · SBIZ · KAKAO_LOCAL). externalId 는 안 건드린다 — 원천의 값이고
		//    대소문자가 뜻을 가질 수 있다.
		String sourceType = upperOrNull(snapshot.sourceType());
		String externalId = normalized(snapshot.externalId());
		if (sourceType == null || externalId == null) {
			throw new IllegalArgumentException("place.source 와 place.externalId 는 함께 있어야 합니다.");
		}
		String name = normalized(snapshot.name());
		if (name == null) {
			// 이름 없는 장소를 만들면 화면에 빈 칩이 뜬다. name_ko 는 NOT NULL 이기도 하다.
			throw new IllegalArgumentException("place.name 은 비울 수 없습니다.");
		}
		if ((snapshot.lat() == null) != (snapshot.lng() == null)) {
			// ck_place_origin_pair 가 DB 에서도 막지만, 여기서 막아야 어느 칸이 문제인지 말할 수 있다.
			throw new IllegalArgumentException("place.lat 과 place.lng 은 함께 있거나 함께 없어야 합니다.");
		}

		return this.places.findBySourceTypeAndSourceId(sourceType, externalId)
				.map(this::survivorOf)
				.orElseGet(() -> create(sourceType, externalId, name, snapshot));
	}

	/**
	 * 합쳐진 줄이면 남는 줄로 잇는다 — S15P21E201-1619. 사용자가 카카오에서 고른 곳이 합쳐진 줄이면 그 줄에 기록을
	 * 붙여 봐야 찾아 주는 조회에서 빠져 있어 다시 못 찾는다. 남는 줄이 없으면 받은 줄 그대로다.
	 */
	private Place survivorOf(Place found) {
		if (found.getMergedInto() == null) {
			return found;
		}
		return this.places.findById(found.getMergedInto()).orElse(found);
	}

	private Place create(String sourceType, String externalId, String name, Snapshot snapshot) {
		OffsetDateTime now = OffsetDateTime.now(this.clock);
		Place created = Place.userSubmitted(placeIdOf(sourceType, externalId), name,
				normalized(snapshot.category()), normalized(snapshot.address()),
				snapshot.lat(), snapshot.lng(), sourceType, externalId, now);
		return this.places.save(created);
	}

	/**
	 * 원천 식별자에서 {@code place_id} 를 결정적으로 만든다 — 적재기들이 쓰는 방식과 같다
	 * ({@code SbizPlaceLoader.placeIdOf} 등).
	 *
	 * <p>같은 장소를 두 사람이 동시에 고르면 조회는 둘 다 빈손이고 저장이 둘 다 일어난다.
	 * 그때 {@code place_id} 가 같으면 뒤엣것이 PK 에서 막히고, 달랐다면 {@code uq_place_source}
	 * 에서 막힌다 — 어느 쪽이든 행은 하나다. 앞엣것이 낫다: 다시 시도하면 조회가 그 행을 찾는다.
	 */
	static UUID placeIdOf(String sourceType, String externalId) {
		return UUID.nameUUIDFromBytes((sourceType + ':' + externalId).getBytes(StandardCharsets.UTF_8));
	}

	private static String normalized(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static String upperOrNull(String value) {
		String trimmed = normalized(value);
		return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
	}
}
