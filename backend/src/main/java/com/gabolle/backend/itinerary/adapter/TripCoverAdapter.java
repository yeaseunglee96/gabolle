package com.gabolle.backend.itinerary.adapter;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.trip.application.port.TripCoverPort;

import jakarta.persistence.EntityManager;

/**
 * {@link TripCoverPort} 의 일정 쪽 구현. 인터페이스는 쓰는 쪽(여행)이 정의하고 구현은 여기에
 * 둔다 — 여행이 일정 표를 직접 읽으면 판 구조를 고칠 때 여행 목록까지 함께 고쳐야 한다.
 *
 * <p>🔴 <b>여행이 몇 개든 질의는 하나다.</b> 포트가 목록을 통째로 받는 모양인 이유가 그것이고,
 * 이 클래스에 반복문 안에서 DB 를 부르는 코드가 생기면 포트를 만든 뜻이 사라진다
 * (S15P21E201-1370).
 *
 * <p>🔴 <b>장소 표까지 건너간다.</b> 표지는 «첫 방문지가 어디냐»(일정의 몫)와 «그 장소의 사진이
 * 무엇이냐»(장소의 몫)가 합쳐진 값이다. 어려운 쪽이 앞이라 일정에 두었다. 뒤쪽은 조인 한 줄이고,
 * 이것을 다시 장소 포트로 쪼개면 질의가 둘로 갈라져 «한 번에» 라는 약속이 깨진다.
 */
@Component
@Profile({ "db", "dev" })
public class TripCoverAdapter implements TripCoverPort {

	private static final Logger log = LoggerFactory.getLogger(TripCoverAdapter.class);

	/**
	 * 여행마다 표지 한 줄 — 첫 방문지의 <b>이름</b>과, 앞쪽 방문지 가운데 <b>사진이 있는 첫 곳</b>의 사진.
	 *
	 * <p>🔴 <b>이름과 사진이 다른 곳에서 올 수 있다 (S15P21E201-1436).</b> 처음에는 둘 다 «첫 방문지»
	 * 에서 가져왔는데, 첫 방문지는 식당·카페인 경우가 많고 그런 곳은 관광공사 사진이 없다 — 2026-09-21
	 * 실서버에서 첫 방문지가 정해진 여행 41건 중 사진이 있는 것은 10건뿐이었다. 그래서 사진은 앞
	 * {@link #COVER_LOOKAHEAD_STOPS} 곳까지 훑어 처음 만나는 것을 쓴다. 화면이 이미 그렇게 하고 있었고
	 * (frontend/src/plan/tripCover.ts), 규칙이 다르면 화면이 서버 값으로 갈아탈 때 사진이 오히려 줄어든다.
	 *
	 * <p>이름은 «첫 방문지» 그대로다 — {@code firstStopName} 이라는 이름이 뜻하는 것이 그것이고, 사진을
	 * 고른 곳으로 바꾸면 「첫 방문지」를 묻는 쪽이 다른 답을 받는다.
	 *
	 * <p>🔴 <b>«첫 일정» 은 가장 먼저 만들어진 일정이다.</b> 지어낸 규칙이 아니라 여행 하나를
	 * 열었을 때 나오는 그 일정이다 — {@code JpaItineraryRepository.findByTripId} 가
	 * {@code findByTripIdOrderByCreatedAtAsc} 로 읽는다. 다르게 고르면 목록의 표지와 열어 본
	 * 화면의 첫 장소가 어긋난다. 실제로 일정을 셋 가진 여행이 2026-09-21 실서버에 하나 있다.
	 *
	 * <p>🔴 <b>정렬 꼬리의 {@code itinerary_id}·{@code itinerary_item_id} 는 장식이 아니다.</b>
	 * {@code created_at} 이나 {@code (day_index, sequence)} 가 같은 줄이 둘 있으면 어느 줄이 뽑힐지
	 * 정해지지 않고, 그러면 같은 여행의 표지가 새로고침마다 바뀐다.
	 *
	 * <p>판은 {@code latest_version} 을 따른다 — 일정을 편집하면 표지도 따라 바뀌는 것이 맞다.
	 * 여행이 몇 개든 질의는 이 하나다.
	 */
	private static final String COVER_OF_EACH_TRIP = """
			WITH first_itinerary AS (
			    SELECT DISTINCT ON (itinerary.trip_id)
			           itinerary.trip_id       AS trip_id,
			           itinerary.itinerary_id  AS itinerary_id,
			           itinerary.latest_version AS latest_version
			    FROM itineraries itinerary
			    WHERE itinerary.trip_id IN (:tripIds)
			    ORDER BY itinerary.trip_id, itinerary.created_at, itinerary.itinerary_id
			), stops AS (
			    SELECT first_itinerary.trip_id AS trip_id,
			           item.place_id           AS place_id,
			           ROW_NUMBER() OVER (
			               PARTITION BY first_itinerary.trip_id
			               ORDER BY item.day_index, item.sequence, item.itinerary_item_id
			           ) AS stop_rank
			    FROM first_itinerary
			    JOIN itinerary_versions version
			      ON version.itinerary_id = first_itinerary.itinerary_id
			     AND version.version = first_itinerary.latest_version
			    JOIN itinerary_item item
			      ON item.itinerary_version_id = version.itinerary_version_id
			), head AS (
			    SELECT * FROM stops WHERE stop_rank <= :lookahead
			)
			SELECT head.trip_id,
			       (SELECT photo.photo_url
			          FROM head AS with_photo
			          JOIN place AS photo ON photo.place_id = with_photo.place_id
			         WHERE with_photo.trip_id = head.trip_id
			           AND photo.photo_url IS NOT NULL
			         ORDER BY with_photo.stop_rank
			         LIMIT 1) AS photo_url,
			       place.name_ko,
			       place.name_en
			FROM head
			JOIN place ON place.place_id = head.place_id
			WHERE head.stop_rank = 1
			""";

	/**
	 * 사진을 찾을 때 앞에서 몇 곳까지 보는가.
	 *
	 * <p>여섯은 화면이 쓰던 수와 같게 맞춘 값이다(frontend/src/plan/tripCover.ts 의
	 * {@code slice(0, 6)}). 늘리면 「첫 방문지 근처」라기엔 먼 곳의 사진이 표지가 되고, 줄이면
	 * 사진 없는 카드가 늘어난다.
	 */
	static final int COVER_LOOKAHEAD_STOPS = 6;

	private final EntityManager entityManager;

	public TripCoverAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	@Transactional(readOnly = true)
	public Map<String, Cover> coversOf(Collection<String> tripIds) {
		List<UUID> ids = tripIds.stream().map(TripCoverAdapter::parseOrNull).filter((id) -> id != null).toList();
		if (ids.isEmpty()) {
			// 빈 목록으로 IN () 을 만들면 문법 오류다. 물어볼 것이 없으니 다녀올 것도 없다.
			return Map.of();
		}

		try {
			@SuppressWarnings("unchecked")
			List<Object[]> rows = this.entityManager.createNativeQuery(COVER_OF_EACH_TRIP)
					.setParameter("tripIds", ids)
					.setParameter("lookahead", COVER_LOOKAHEAD_STOPS)
					.getResultList();

			Map<String, Cover> covers = new HashMap<>();
			for (Object[] row : rows) {
				covers.put(String.valueOf(row[0]), new Cover(text(row[1]), text(row[2]), text(row[3])));
			}
			return covers;
		}
		catch (RuntimeException ex) {
			// 🔴 표지는 장식이라 여기서 예외를 올리지 않는다. 올리면 사진 한 장 때문에
			//    «내 여행» 목록 전체가 500 이 된다. 대신 로그를 남긴다 — 조용히 삼키면
			//    표지가 영영 안 나오는데 아무도 이유를 모른다.
			log.warn("여행 표지를 읽지 못했습니다. 표지 없이 목록을 냅니다 (여행 {}건)", ids.size(), ex);
			return Map.of();
		}
	}

	/**
	 * 여행마다 {@code chosen_at} 이 가장 늦은 일정(S15P21E201-1602). 일정이 생길 때 그 시각이 찍히고, 이미 있는 안을
	 * 다시 고르면 지금으로 옮겨진다({@code TripCourseService.choose}).
	 */
	private static final String CURRENT_OF_EACH_TRIP = """
			SELECT DISTINCT ON (itinerary.trip_id) itinerary.trip_id, itinerary.itinerary_id
			FROM itineraries itinerary
			WHERE itinerary.trip_id IN (:tripIds)
			ORDER BY itinerary.trip_id, itinerary.chosen_at DESC, itinerary.created_at DESC, itinerary.itinerary_id DESC
			""";

	@Override
	public Map<String, String> currentItinerariesOf(Collection<String> tripIds) {
		List<UUID> ids = tripIds.stream().map(TripCoverAdapter::parseOrNull).filter((id) -> id != null).toList();
		if (ids.isEmpty()) {
			return Map.of();
		}
		@SuppressWarnings("unchecked")
		List<Object[]> rows = this.entityManager.createNativeQuery(CURRENT_OF_EACH_TRIP)
				.setParameter("tripIds", ids)
				.getResultList();
		Map<String, String> current = new HashMap<>();
		for (Object[] row : rows) {
			current.put(String.valueOf(row[0]), String.valueOf(row[1]));
		}
		return current;
	}

	/** 식별자 모양이 아니면 조용히 뺀다 — 표지 하나 때문에 목록을 실패시키지 않는다. */
	private static UUID parseOrNull(String tripId) {
		try {
			return UUID.fromString(tripId);
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			return null;
		}
	}

	private static String text(Object value) {
		return value == null ? null : String.valueOf(value);
	}
}
