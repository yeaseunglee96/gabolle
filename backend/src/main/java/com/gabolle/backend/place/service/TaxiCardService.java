package com.gabolle.backend.place.service;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.api.TaxiCardResponse;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;

/**
 * 택시 목적지 카드 (S15P21E201-217).
 *
 * <p>한국어를 못 하는 여행자가 택시 기사에게 화면을 보여주면 목적지가 전달되는 화면에 쓴다. 조회
 * 하나(장소 1건, {@code findById})로 끝난다 — 좌표나 사용자 식별 없이 장소 아이디만으로 답이
 * 정해진다.
 */
@Service
@Profile({ "db", "dev" })
public class TaxiCardService {

	private final PlaceRepository placeRepository;

	public TaxiCardService(PlaceRepository placeRepository) {
		this.placeRepository = placeRepository;
	}

	/**
	 * @param acceptLanguageHeader 요청의 {@code Accept-Language} 값 그대로. 없으면 {@code null} —
	 *        그 경우 한국어를 우선한다
	 */
	@Transactional(readOnly = true)
	public TaxiCardResponse get(UUID placeId, String acceptLanguageHeader) {
		Place place = this.placeRepository.findById(placeId)
				.orElseThrow(() -> new PlaceNotFoundException(placeId));

		// 🔴 기준이 영문 이름이 아니라 영문 **주소**다. 이 응답이 영어로 싣는 값은 주소이고,
		// 영문 이름만 있고 주소가 없으면 화면에 보일 영어는 아무것도 없다 — 그때 "en" 이라고
		// 답하면 응답이 거짓말을 한다 (RequestLanguage 주석 참고).
		String resolvedLanguage = RequestLanguage.resolve(acceptLanguageHeader, place.getAddressEn() != null);

		return new TaxiCardResponse(
				place.getPlaceId(),
				place.getNameKo(),
				place.getAddress(),
				place.getAddressEn(),
				resolvedLanguage,
				driverSentence(place));
	}

	/**
	 * 기사에게 보여줄 한국어 한 문장을 서버가 조립한다.
	 *
	 * <p>🔴 화면(앱)에서 조립하지 않는 이유 — 문구가 어색해서 고쳐야 할 때, 서버가 문장을 주면
	 * 문구만 바꿔 서버를 다시 배포하면 되지만, 화면이 스스로 조립하면 앱을 다시 배포해야 하고
	 * 배포된 앱은 스토어 심사 때문에 즉시 못 고친다.
	 *
	 * <p>🔴 이 문장은 항상 한국어다. 읽는 사람이 여행자가 아니라 한국어를 쓰는 택시 기사이기
	 * 때문이다 — 언어에 따라 바꾸면 이 기능 자체가 무의미해진다.
	 */
	private String driverSentence(Place place) {
		String address = place.getAddress();
		String target = (address == null || address.isBlank()) ? place.getNameKo() : address;
		return "이 주소로 가주세요, " + target;
	}

}
