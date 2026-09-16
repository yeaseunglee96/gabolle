package com.gabolle.backend.place.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.domain.SavedPlace;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.repository.SavedPlaceRepository;

/**
 * 저장한 장소(하트) — S15P21E201-1013.
 *
 * <p>여행과 무관한 전역 목록이다. 여행별 후보 판단은 {@code RecommendationPlaceAction} 이
 * 따로 갖는다.
 */
@Service
@Profile({ "db", "dev" })
public class SavedPlaceService {

	/**
	 * 한 번에 돌려주는 최대 개수 — S15P21E201-1037.
	 *
	 * <p>상한을 두면 <b>알리는 칸을 함께</b> 둬야 한다({@code hasMore}). 상한만 두고 안
	 * 알리면 목록이 조용히 잘리고, 사용자에게는 「내가 누른 하트가 사라졌다」로 보인다.
	 * 같은 판단을 축제 목록과 일정 판 목록이 먼저 했다(S15P21E201-1011).
	 */
	public static final int MAX_ITEMS = 500;

	private final SavedPlaceRepository savedPlaceRepository;

	private final PlaceRepository placeRepository;

	private final Clock clock;

	public SavedPlaceService(SavedPlaceRepository savedPlaceRepository, PlaceRepository placeRepository,
			Clock clock) {
		this.savedPlaceRepository = savedPlaceRepository;
		this.placeRepository = placeRepository;
		this.clock = clock;
	}

	/**
	 * 내가 저장한 것. 상한까지만 돌려주고, 더 있으면 그 사실을 함께 알린다.
	 *
	 * <p>상한보다 하나 더 요청해서 더 있는지 본다 — 따로 개수를 세면 같은 표를 두 번 읽는다.
	 */
	@Transactional(readOnly = true)
	public Page list(UUID userId) {
		List<SavedPlace> found = this.savedPlaceRepository
				.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, MAX_ITEMS + 1));

		boolean hasMore = found.size() > MAX_ITEMS;
		return new Page(hasMore ? found.subList(0, MAX_ITEMS) : found, hasMore);
	}

	/**
	 * 하트를 켠다. <b>몇 번을 보내도 같다.</b>
	 *
	 * <p>2026-09-16 (S15P21E201-1037) — 그전에는 {@code exists} 로 보고 없으면 넣었다.
	 * 연타하면 두 요청이 둘 다 통과한 뒤 하나가 유일 제약에 걸려 500 이 나갔다. 지금은
	 * 넣기와 판정을 DB 한 문장에서 끝낸다 — {@link SavedPlaceRepository#insertIfAbsent} 참고.
	 *
	 * <p>장소가 없는지는 여전히 먼저 본다. 그것은 경쟁이 아니라 <b>잘못된 요청</b>이고,
	 * 화면이 404 를 보고 「기기에 남아 있던 옛 이름표」를 지우는 근거로 쓴다.
	 */
	@Transactional
	public void save(UUID userId, UUID placeId) {
		if (!this.placeRepository.existsById(placeId)) {
			throw new PlaceNotFoundException(placeId);
		}
		this.savedPlaceRepository.insertIfAbsent(UUID.randomUUID(), userId, placeId,
				OffsetDateTime.now(this.clock));
	}

	/**
	 * 하트를 끈다. 안 켜져 있던 것을 꺼도 성공이다 — 끄기의 결과는 「안 켜져 있음」이고
	 * 그것은 이미 참이다. 없다고 404 를 주면 화면이 두 번 누른 사용자에게 오류를 보여 준다.
	 */
	@Transactional
	public void remove(UUID userId, UUID placeId) {
		this.savedPlaceRepository.deleteByUserIdAndPlaceId(userId, placeId);
	}

	/**
	 * 잘라 온 목록과, 잘렸는지 여부.
	 *
	 * @param hasMore 상한에 걸려 <b>더 있는데 안 보냈다.</b> 이 칸이 없으면 부르는 쪽이
	 *     「상한에 걸린 것」과 「이게 전부인 것」을 구분할 수 없다
	 */
	public record Page(List<SavedPlace> items, boolean hasMore) {
	}
}
