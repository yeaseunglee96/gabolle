package com.gabolle.backend.place.service;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.api.TaxiCardResponse;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;

/**
 * 택시 목적지 카드 — 한국어를 못 하는 여행자가 기사에게 화면을 보여주는 용도다.
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

		// 기준은 영문 이름이 아니라 영문 주소다. 이 응답이 영어로 싣는 값이 주소뿐이라,
		// 이름만 있고 주소가 없는데 "en" 이라고 답하면 화면에 보일 영어가 없다.
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
	 * 기사에게 보여줄 문장. 요청 언어와 무관하게 항상 한국어다 — 읽는 사람이 여행자가 아니라
	 * 택시 기사다.
	 *
	 * <p>앱이 아니라 서버가 조립하는 이유는 문구를 고칠 때 스토어 심사를 기다리지 않기 위해서다.
	 */
	private String driverSentence(Place place) {
		String address = place.getAddress();
		String target = (address == null || address.isBlank()) ? place.getNameKo() : address;
		return "이 주소로 가주세요, " + target;
	}

}
