package com.gabolle.backend.trip.application.port;

import java.util.Collection;
import java.util.Map;

/**
 * 여행 → 일정 방향의 유일한 문. 여행 목록 한 줄에 얹을 <b>표지</b>만 묻는다 —
 * 대표 사진 한 장과 첫 방문지 이름.
 * <p>
 * 🔴 <b>이 문이 있는 이유.</b> 표지는 일정 → 장소 → 사진을 타야 나오는 값이라, 여행 모듈이
 * 직접 읽으면 {@code itineraries}·{@code itinerary_versions}·{@code itinerary_item} 의 표
 * 구조를 여행이 알게 된다. 그러면 일정 쪽 표를 고칠 때 여행 목록이 같이 깨진다.
 * 쓰는 쪽인 여행이 이 인터페이스를 정의하고 주는 쪽인 일정이 구현한다 —
 * {@code RouteOrderPort}·{@code PlaceEventSchedulePort} 와 같은 모양이고 같은 이유다.
 * <p>
 * 🔴 <b>실패를 예외로 알리지 않는다.</b> 표지는 장식이다. 사진 한 장 때문에 여행 목록
 * 전체가 500 이 되면 앱에서 «내 여행» 화면이 통째로 안 열린다. 못 구하면 그냥 없는 것이다.
 */
public interface TripCoverPort {

	/**
	 * 여행들의 표지를 <b>한 번에</b> 구한다.
	 * <p>
	 * 🔴 <b>묻는 횟수는 여행 수와 무관하게 하나다.</b> 이 문을 만든 실질적인 이유가 그것이다 —
	 * 줄마다 일정 → 장소 → 사진을 따로 부르면 여행 50개에 질의 150번이 나가고, 그건 목록이
	 * 느려지는 정도가 아니라 화면이 안 뜨는 수준이 된다(S15P21E201-1370). 구현이 이 약속을
	 * 깨면 여기 있을 이유가 없다.
	 *
	 * @param tripIds 표지를 구할 여행. 비어 있으면 빈 map 이다
	 * @return 여행 식별자 → 표지. <b>표지를 못 구한 여행은 아예 키가 없다</b> — 일정이 아직
	 *     없는 여행, 일정은 있는데 방문지가 없는 여행, 그리고 이 문 너머가 실패한 경우가
	 *     모두 여기에 해당한다. {@code null} 값을 담지 않는다
	 */
	Map<String, Cover> coversOf(Collection<String> tripIds);

	/**
	 * 여행들의 「지금 확정된 일정」 번호를 <b>한 번에</b> (S15P21E201-1602) — 코스를 마지막으로 고른 일정, 안 골랐으면
	 * 추천이 만든 기본 일정. 앱이 일정이 여럿인 여행을 누를 때 「버전 N」 목록을 안 띄우고 바로 연다.
	 *
	 * <p>표지와 달리 실패를 삼키지 않는다 — 「일정이 없다」로 잘못 답하면 앱이 여행을 빈 것으로 그린다.
	 *
	 * @return 여행 식별자 → 일정 식별자. 일정이 없는 여행은 키가 없다. 기본 구현은 아무것도 모른다
	 */
	default Map<String, String> currentItinerariesOf(Collection<String> tripIds) {
		return Map.of();
	}

	/**
	 * 표지를 아무것도 못 주는 구현. 일정 표가 없는 프로파일과, 표지를 안 보는 시험이 쓴다.
	 * 여기에 빈 구현을 두는 것은 부르는 쪽에 {@code null} 검사를 퍼뜨리지 않기 위해서다.
	 */
	TripCoverPort NONE = (tripIds) -> Map.of();

	/**
	 * 여행 한 건의 표지.
	 *
	 * @param imageUrl 표지 사진 — 첫 방문지부터 앞쪽 몇 곳을 훑어 «사진이 있는 첫 곳»의 것이다.
	 *     그래서 {@code stopNameKo} 와 다른 장소의 사진일 수 있다(S15P21E201-1436). 첫 방문지만 보면
	 *     식당·카페가 첫 곳인 여행에서 사진이 거의 없다 — 화면도 같은 규칙으로 찾고 있었다. <b>{@code null} 이 흔하다</b> — 2026-09-21 실서버에서
	 *     첫 방문지가 정해진 여행 41건 중 사진이 있는 것은 10건뿐이었다. 화면은 사진이 없는
	 *     쪽을 정상으로 그려야 한다
	 * @param stopNameKo 첫 방문지의 한국어 이름
	 * @param stopNameEn 첫 방문지의 영어 이름. 없을 수 있다
	 */
	record Cover(String imageUrl, String stopNameKo, String stopNameEn) {
	}
}
