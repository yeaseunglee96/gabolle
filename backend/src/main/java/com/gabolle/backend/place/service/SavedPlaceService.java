package com.gabolle.backend.place.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.domain.SavedPlace;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.repository.SavedPlaceRepository;

/**
 * 저장한 장소(하트)를 서버에 남긴다 — S15P21E201-1013.
 *
 * <p>지금까지 기기에만 있었다. 기기를 바꾸면 사라졌다.
 *
 * <p>🔴 소유 검사가 따로 없다. 이 자원의 주인은 <b>요청자 자신</b>이고, 경로에 남의
 * 식별자를 넣을 자리가 아예 없다({@code /api/v1/me/...}). 사용자 번호는 인증 주체에서만
 * 읽으므로 남의 목록에 닿을 길이 없다.
 */
@Service
@Profile({ "db", "dev" })
public class SavedPlaceService {

	private final SavedPlaceRepository savedPlaceRepository;

	private final PlaceRepository placeRepository;

	private final Clock clock;

	public SavedPlaceService(SavedPlaceRepository savedPlaceRepository, PlaceRepository placeRepository,
			Clock clock) {
		this.savedPlaceRepository = savedPlaceRepository;
		this.placeRepository = placeRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<SavedPlace> list(UUID userId) {
		return this.savedPlaceRepository.findByUserIdOrderByCreatedAtDesc(userId);
	}

	/**
	 * 하트를 켠다. 이미 켜져 있으면 아무것도 하지 않는다.
	 *
	 * <p>🔴 <b>같은 요청을 두 번 보내도 결과가 같다.</b> 하트는 켜짐/꺼짐이라 "두 번 켠 상태"
	 * 가 없다. 누를 때마다 행을 더하는 모양이면 연타나 재시도에서 {@code uk_saved_place} 에
	 * 걸려 실패하거나, 제약이 없었다면 목록에 같은 장소가 여러 번 뜬다.
	 *
	 * @throws PlaceNotFoundException 없는 장소를 저장하려 했다. 🔴 이것을 안 막으면 외래키
	 *     위반이 그대로 올라와 <b>500</b> 이 나가고, 화면은 "서버가 고장났다" 와 "그런 장소가
	 *     없다" 를 구분하지 못한다
	 */
	@Transactional
	public void save(UUID userId, UUID placeId) {
		if (!this.placeRepository.existsById(placeId)) {
			throw new PlaceNotFoundException(placeId);
		}
		if (this.savedPlaceRepository.existsByUserIdAndPlaceId(userId, placeId)) {
			return;
		}
		this.savedPlaceRepository.save(
				SavedPlace.of(UUID.randomUUID(), userId, placeId, OffsetDateTime.now(this.clock)));
	}

	/**
	 * 하트를 끈다.
	 *
	 * <p>🔴 <b>안 켜져 있던 것을 꺼도 성공이다.</b> 이미 꺼진 뒤에 재시도가 도착하는 일이
	 * 흔하고, 그때 404 를 내면 화면은 "꺼졌는데 못 껐다고 한다" 를 그린다. 끄기의 결과는
	 * "그 하트가 없는 상태" 이고 그건 두 경우 모두 같다.
	 *
	 * <p>여기서는 장소가 있는지도 안 본다 — 없는 장소의 하트를 끄는 것도 결과가 같다.
	 */
	@Transactional
	public void remove(UUID userId, UUID placeId) {
		this.savedPlaceRepository.deleteByUserIdAndPlaceId(userId, placeId);
	}
}
