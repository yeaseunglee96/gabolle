package com.gabolle.backend.place.loader;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.place.domain.CertifiedRestaurant;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlaceRepository;

/**
 * 로컬 탐색 「카페」 갈래에 올릴 카페를 {@code place} 와 {@code place_feature} 에 넣는다 (S15P21E201-2059).
 *
 * <p>적재 흐름 전체는 맛집 적재기({@link CertifiedRestaurantLoader})와 같은 엔진 {@link LocalPickLoader} 다 — 이미 있는 장소에는
 * 표식만 달고, 없으면 새로 만들고, 새로 만들기 전에 같은 곳 검사({@link SamePlaceGuard})를 거치고, 두 번 돌려도 행이 안 는다.
 * 여기에는 <b>카페만의 것</b>이 남는다.
 *
 * <ul>
 *   <li>갈래 표식은 {@code LOCAL_CAFE}. 이미 있는 장소의 갈래(category)는 <b>안 바꾼다</b> — 장소 갈래가 음식인 곳도 카페 표식만
 *       더한다(음식점을 겸하는 카페)</li>
 *   <li>인증 표식은 소개한 목록마다 한 줄 — {@code KTO_CAFE}(한국관광공사) · {@code REDTABLE_FEATURED}(레드테이블, 방송·수상 이력이
 *       적힌 곳은 값에 {@code broadcastOrAward:true})</li>
 *   <li>새 장소는 갈래 {@code CAFE_HEALING}. 출처는 <b>원천의 것을 그대로 쓴다</b> — 관광공사 줄은 {@code TOURAPI}, 레드테이블 줄은
 *       {@code REDTABLE}({@link LocalCafeRow#newPlace}). 그 적재기들과 같은 번호식이라 같은 가게를 서로 모르고 두 번 넣지 않는다.
 *       표식({@code CATEGORY_TAG} 포함)의 {@code source_type} 은 {@link CertifiedRestaurant#CAFE_SOURCE_TYPE} 이라 되돌릴 때 이 값과
 *       수집분 버전 하나로 걸린다</li>
 * </ul>
 *
 * <p>🔴 이 적재기는 <b>레드테이블 사진을 쓰지 않는다</b>({@link LocalCafeReader} 가 파일에서 막는다). 관광공사 사진은 출처 문구와
 * 함께 새 장소에만 붙고, 이미 있는 장소의 사진은 건드리지 않는다.
 */
@Component
@Profile({ "db", "dev" })
public class LocalCafeLoader extends LocalPickLoader<LocalCafeRow> {

	public LocalCafeLoader(PlaceRepository placeRepository, PlaceFeatureRepository placeFeatureRepository,
			SamePlaceGuard samePlaceGuard) {
		super(placeRepository, placeFeatureRepository, samePlaceGuard);
	}

	@Override
	protected CertifiedRestaurant.Kind kind() {
		return CertifiedRestaurant.Kind.CAFE;
	}

	@Override
	protected String markSourceType() {
		return CertifiedRestaurant.CAFE_SOURCE_TYPE;
	}
}
