package com.gabolle.backend.place.loader;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.gabolle.backend.place.domain.CertifiedRestaurant;

/**
 * 로컬 탐색 「카페」 갈래에 올릴 카페 한 곳 — {@link LocalCafeReader} 가 읽은 한 줄이다 (S15P21E201-2059).
 *
 * <p>{@code sourceId} 는 수집한 쪽이 매긴 번호다. 앞머리가 출처를 말한다 — {@code KTO} + 한국관광공사 콘텐츠 번호(contentid),
 * {@code RT} + 레드테이블 식당 번호(RSTR_ID). 새 장소의 출처와 번호를 이 앞머리에서 가른다({@link #newPlace}).
 * {@code gu}·{@code dong} 은 좌표를 VWorld(국토교통부 지도 서비스)로 역지오코딩(좌표로 행정 주소를 알아내는 것)해서 얻은
 * <b>법정동</b>이다. {@code existingPlaceId} 가 있으면 그 카페는 우리 DB 에 이미 있는 장소이고, 표식만 달고 장소는 새로 만들지
 * 않는다 — 그 장소의 갈래(category)도 안 바꾼다. {@code existingCategory} 는 수집할 때 그 장소가 가졌던 갈래(음식 또는
 * 카페·힐링)로, 읽어 두기만 한다.
 *
 * <p>{@code tel}·{@code intro} 는 읽기만 하고 DB 에 넣지 않는다 — {@code place} 에 전화번호 칸이 없고, 소개 글은 레드테이블이 쓴
 * 홍보 문구(「…를 추천합니다!」)라 쓸 조건을 확인한 적이 없다.
 *
 * <p>🔴 <b>사진은 한국관광공사 것만 쓴다.</b> 레드테이블 사진은 다시 써도 되는지 이용 조건을 확인하지 못했다. 수집 파일은 이미
 * 비워 두었고, 리더({@link LocalCafeReader})가 한 번 더 막는다.
 */
public record LocalCafeRow(
		String sourceId,
		String name,
		double lat,
		double lng,
		String address,
		String tel,
		String gu,
		String dong,
		List<Origin> origins,
		String intro,
		UUID existingPlaceId,
		String existingCategory,
		String photoUrl,
		String photoSource) implements LocalPickRow {

	/** 새 카페 장소의 갈래 — 앱의 여섯 갈래 가운데 「카페·힐링」. */
	static final String NEW_PLACE_CATEGORY = "CAFE_HEALING";

	/** 관광공사 번호의 앞머리. */
	static final String KTO_PREFIX = "KTO";

	/** 레드테이블 번호의 앞머리. */
	static final String REDTABLE_PREFIX = "RT";

	public LocalCafeRow {
		origins = List.copyOf(origins);
	}

	/**
	 * 어느 목록이 이 카페를 소개했나. 파일에는 한국어 이름으로 온다.
	 *
	 * @param source 표식({@code CERTIFICATION_TAG})의 열쇠
	 * @param broadcastOrAward 레드테이블이 방송·수상 이력도 적어 둔 곳인가
	 */
	public enum Origin {

		KTO("한국관광공사", CertifiedRestaurant.Source.KTO_CAFE, false),

		REDTABLE("레드테이블", CertifiedRestaurant.Source.REDTABLE_FEATURED, false),

		REDTABLE_MEDIA("레드테이블(방송·수상)", CertifiedRestaurant.Source.REDTABLE_FEATURED, true);

		private final String fileLabel;

		private final CertifiedRestaurant.Source source;

		private final boolean broadcastOrAward;

		Origin(String fileLabel, CertifiedRestaurant.Source source, boolean broadcastOrAward) {
			this.fileLabel = fileLabel;
			this.source = source;
			this.broadcastOrAward = broadcastOrAward;
		}

		public String fileLabel() {
			return this.fileLabel;
		}

		public CertifiedRestaurant.Source source() {
			return this.source;
		}

		public boolean broadcastOrAward() {
			return this.broadcastOrAward;
		}

		/** 레드테이블 쪽인가 — 번호 앞머리 {@code RT} 와 맞아야 한다. */
		public boolean isRedtable() {
			return this.source == CertifiedRestaurant.Source.REDTABLE_FEATURED;
		}

		/** 파일의 이름으로 찾는다. 모르는 이름은 빈 값 — 부르는 쪽이 멈춘다. */
		public static Optional<Origin> fromFileLabel(String label) {
			if (label == null) {
				return Optional.empty();
			}
			String trimmed = label.trim();
			return Arrays.stream(values()).filter(origin -> origin.fileLabel.equals(trimmed)).findFirst();
		}
	}

	/** 표식의 구·동. */
	@Override
	public CertifiedRestaurant.Area area() {
		return new CertifiedRestaurant.Area(this.gu, this.dong);
	}

	/** 소개한 목록마다 한 줄. 레드테이블이 두 이름으로 오면 같은 표식이라 엔진이 합치고 방송·수상은 하나라도 있으면 남긴다. */
	@Override
	public List<CertMark> certMarks() {
		return this.origins.stream().map(origin -> new CertMark(origin.source(), origin.broadcastOrAward())).toList();
	}

	/**
	 * 번호 앞머리로 출처를 가른다. 관광공사는 {@code TOURAPI}, 레드테이블은 {@code REDTABLE} 이고 장소 번호도 그 적재기들이 쓰는
	 * 식({@link TourApiPlaceLoader#placeIdOf} · {@link RedtablePlaceLoader#placeIdOf})으로 만든다. 그러면
	 * <b>같은 가게를 그 적재기가 먼저(또는 나중에) 넣어도 같은 장소 번호</b>라 두 번 만들어지지 않는다 — 출처 값을 새로 지으면 그
	 * 적재기들이 이 카페를 모른 채 한 번 더 넣는다.
	 */
	@Override
	public NewPlace newPlace() {
		if (this.sourceId.startsWith(KTO_PREFIX)) {
			String contentId = this.sourceId.substring(KTO_PREFIX.length());
			return new NewPlace(TourApiPlaceLoader.placeIdOf(contentId), TourApiPlaceLoader.SOURCE_TYPE, contentId,
					NEW_PLACE_CATEGORY);
		}
		if (this.sourceId.startsWith(REDTABLE_PREFIX)) {
			String rstrId = this.sourceId.substring(REDTABLE_PREFIX.length());
			return new NewPlace(RedtablePlaceLoader.placeIdOf(rstrId), RedtablePlaceLoader.SOURCE_TYPE, rstrId,
					NEW_PLACE_CATEGORY);
		}
		// 리더가 번호 모양을 먼저 검사하므로 여기 오면 리더를 거치지 않고 만든 줄이다.
		throw new IllegalStateException("출처를 알 수 없는 번호다(KTO… 또는 RT… 여야 한다): " + this.sourceId);
	}
}
