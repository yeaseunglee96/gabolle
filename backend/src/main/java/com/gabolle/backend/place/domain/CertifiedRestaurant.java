package com.gabolle.backend.place.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 공공기관이 인증한 부산 식당 — 로컬 탐색의 「맛집」 갈래가 쓰는 이름표 모음 (S15P21E201-2051).
 *
 * <p>맛집은 사람마다 기준이 달라 자의적으로 고르면 믿음이 안 간다. 그래서 공공기관 목록에 오른 식당만 쓴다 —
 * 부산시 맛집정보 · 구군 모범음식점 · 택슐랭(택시기사가 추천한 식당). 장소에 다는 표식은 셋이고 전부
 * {@code place_feature} 에 산다.
 *
 * <ul>
 *   <li>{@code INTEREST_TAG} / {@link #EXPLORE_KEY} — 「맛집 갈래에 든다」. 기존 탐색 여덟 갈래와 같은 서랍이라
 *       {@code /facets} 가 건수를 세고, {@code GET /places?facetType=INTEREST_TAG&facetKey=LOCAL_RESTAURANT} 로도 나온다</li>
 *   <li>{@link #AREA_FEATURE_TYPE} — 「어느 구·동의 가게인가」. 열쇠가 {@code "해운대구/우동"} 꼴이다</li>
 *   <li>{@link #SOURCE_FEATURE_TYPE} — 「어느 목록이 인증했나」. 목록마다 한 줄, 열쇠가 {@link Source} 이름이다</li>
 * </ul>
 *
 * <p>🔴 구·동을 값(JSON)이 아니라 <b>열쇠</b>에 넣은 이유 — 목록을 구·동으로 거르려면 DB 가 값을 비교해야 하는데,
 * 이 저장소의 조회는 JPQL(JPA 의 객체 질의어)이고 JPQL 은 {@code value}(JSONB) 안을 못 본다
 * ({@code PlaceFeatureRepository.findByFeatureTypeIn} 설명). 열쇠는 일반 칸이라 거르고 세고 색인을 탄다.
 * 같은 이유로 {@code INTEREST_TAG}·{@code CATEGORY_TAG} 도 낱말을 열쇠에 둔다.
 *
 * <h4>맛집과 카페 (S15P21E201-2059)</h4>
 * 같은 틀을 「카페」 갈래({@link Kind#CAFE})가 그대로 쓴다. 구·동 표식과 인증 목록 표식은 둘이 똑같고 <b>갈래 낱말만</b>
 * 다르다 — 맛집은 {@code LOCAL_RESTAURANT}, 카페는 {@code LOCAL_CAFE}. 인증 목록에는 공공기관 셋 말고 카페 쪽 둘
 * ({@link Source#KTO_CAFE} · {@link Source#REDTABLE_FEATURED})과 <b>현지인 설문 추천</b>({@link Source#LOCAL_SURVEY})이 더 있다.
 *
 * <p>한 장소가 두 갈래에 서는 것은 막지 않는다 — 음식점을 겸하는 카페가 있다. 다만 장소 갈래(category)가
 * {@code CAFE_HEALING} 인 곳은 맛집에서 빠진다(운영 SQL 이 옮기고, 맛집 적재기는 되살리지 않는다).
 */
public final class CertifiedRestaurant {

	/** {@code place.source_type} 과 이 장소들에 단 표식의 {@code source_type}. 되돌릴 때 이 값 하나로 걸린다. */
	public static final String SOURCE_TYPE = "BUSAN_CERTIFIED_FOOD";

	/**
	 * 카페 적재기({@code LocalCafeLoader})가 단 표식의 {@code place_feature.source_type}. 되돌릴 때 이 값과 수집분 버전으로 걸린다.
	 * 새로 만든 카페 장소의 {@code place.source_type} 은 이 값이 아니라 원천의 것이다 — 관광공사는 {@code TOURAPI},
	 * 레드테이블은 {@code REDTABLE}(다른 적재기와 같은 번호에서 같은 장소 id 가 나와 두 번 만들어지지 않는다).
	 */
	public static final String CAFE_SOURCE_TYPE = "BUSAN_LOCAL_CAFE";

	/** 현지인 설문 추천 표식({@link Source#LOCAL_SURVEY})을 단 운영 SQL 이 {@code place_feature.source_type} 에 찍는 값. */
	public static final String SURVEY_SOURCE_TYPE = "LOCAL_SURVEY";

	/** 탐색 갈래 「맛집」의 열쇠 — {@link ExtraInterestTagCode#LOCAL_RESTAURANT} 와 같은 낱말이다. */
	public static final String EXPLORE_KEY = "LOCAL_RESTAURANT";

	/** 어느 구·동인가. 태그형(열쇠가 반드시 있다 — {@code ck_place_feature_key_shape}). */
	public static final String AREA_FEATURE_TYPE = "ADMIN_AREA_TAG";

	/** 어느 목록이 인증했나. 태그형이고 열쇠는 {@link Source#name()} 이다. */
	public static final String SOURCE_FEATURE_TYPE = "CERTIFICATION_TAG";

	/** 열쇠 한 칸의 길이 상한 — {@code place_feature.feature_key} 가 VARCHAR(50) 이다. */
	public static final int KEY_MAX = 50;

	/** 구와 동을 한 열쇠로 묶는 글자. 구·동 이름에는 쓰이지 않는다. */
	private static final char AREA_SEPARATOR = '/';

	private CertifiedRestaurant() {
	}

	/**
	 * 로컬 탐색에서 구 → 동으로 골라 보는 갈래 (S15P21E201-2059). 둘은 같은 표식 체계를 쓰고 갈래 낱말만 다르다.
	 * 선언 순서는 화면의 칩 순서(맛집 다음 카페)와 같다.
	 */
	public enum Kind {

		/** 맛집 — {@code INTEREST_TAG/LOCAL_RESTAURANT}. */
		RESTAURANT(ExtraInterestTagCode.LOCAL_RESTAURANT),

		/** 카페 — {@code INTEREST_TAG/LOCAL_CAFE}. */
		CAFE(ExtraInterestTagCode.LOCAL_CAFE);

		private final ExtraInterestTagCode tag;

		Kind(ExtraInterestTagCode tag) {
			this.tag = tag;
		}

		/** 이 갈래에 든다는 표식의 열쇠 — {@code place_feature.feature_key} 에 들어가는 낱말. */
		public String exploreKey() {
			return this.tag.name();
		}

		/**
		 * 요청에서 온 글자로 찾는다. 앞뒤 공백과 대소문자는 눈감는다. 모르는 값은 빈 값 — 부르는 쪽이 400 으로 돌려준다
		 * (오타를 「맛집」으로 조용히 읽으면 요청자가 엉뚱한 목록을 받고도 모른다).
		 */
		public static Optional<Kind> fromParam(String text) {
			if (text == null || text.isBlank()) {
				return Optional.empty();
			}
			String normalized = text.trim().toUpperCase(Locale.ROOT);
			return Arrays.stream(values()).filter(kind -> kind.name().equals(normalized)).findFirst();
		}
	}

	/**
	 * 인증한 목록. 파일에는 한국어 이름으로 오고, DB 와 응답에는 {@link #name()} 이 간다 — 화면이 언어마다 이름표를
	 * 따로 달 수 있게 낱말을 고정해 둔다. 앞의 둘은 장소 상세의 공인 표식({@code RECOGNITION})이 쓰는 낱말과 같다.
	 *
	 * <p>🔴 선언 순서가 곧 응답의 {@code certifiedSources} 순서다 — 공공기관 셋, 카페 쪽 둘, 현지인 추천 순이다.
	 */
	public enum Source {

		/** 부산광역시 맛집정보 — 시가 직접 소개하는 식당. */
		BUSAN_FOOD_INFO("부산시 맛집정보", true),

		/** 구·군이 지정한 모범음식점. 장소 상세의 {@code RECOGNITION} 과 같은 낱말이다. */
		MODEL_RESTAURANT("모범음식점", true),

		/** 택시기사가 추천한 식당(택슐랭). 장소 상세의 {@code RECOGNITION} 과 같은 낱말이다. */
		TAXI_DRIVER_PICK("택슐랭", true),

		/**
		 * 한국관광공사가 소개한 카페 (S15P21E201-2059) — 카페 수집 파일의 {@code sources} 가 「한국관광공사」인 줄.
		 * 값은 {@code {"source":"한국관광공사"}}.
		 */
		KTO_CAFE("한국관광공사", false),

		/**
		 * 레드테이블(부산광역시가 공공데이터포털에 연 식당 목록)이 소개한 카페 (S15P21E201-2059). 값은
		 * {@code {"source":"레드테이블"}} 이고, 레드테이블이 <b>방송·수상 이력</b>도 함께 적어 둔 곳(수집 파일의
		 * 「레드테이블(방송·수상)」)이면 {@code "broadcastOrAward":true} 가 더 붙는다. 방송은 TV 맛집 프로그램에 나온 것이고,
		 * 수상은 블루리본 서베이처럼 다른 맛집 안내가 준 평가다.
		 */
		REDTABLE_FEATURED("레드테이블", false),

		/**
		 * 부산 현지인 설문에서 추천받은 곳 (S15P21E201-2059). 공공기관 인증이 아니라 사람들의 추천이다.
		 * 값은 {@code {"source":"현지인 추천","recommenders":n}} — 추천한 사람 수를 모르면 {@code recommenders} 칸이 없다.
		 * 수집 파일에는 오지 않는다 — 운영 SQL({@code backend/scripts/local-picks-20261015/})이 단다.
		 */
		LOCAL_SURVEY("현지인 추천", false);

		private final String fileLabel;

		private final boolean inRestaurantFile;

		Source(String fileLabel, boolean inRestaurantFile) {
			this.fileLabel = fileLabel;
			this.inRestaurantFile = inRestaurantFile;
		}

		/**
		 * 한국어 이름 — 사전({@code place_feature_code})의 한국어 이름과도 같고, 표식 값의 {@code source} 칸에 들어간다.
		 * 공공 인증 식당 수집 파일은 이 이름으로 목록을 적는다.
		 */
		public String fileLabel() {
			return this.fileLabel;
		}

		/**
		 * 공공 인증 식당 수집 파일의 {@code sources} 에 올 수 있는 목록인가. 앞의 셋만 그렇다 — 카페 쪽 둘과 현지인 추천이
		 * 맛집 파일에서 읽히면 엉뚱한 장소에 엉뚱한 출처가 달린다.
		 */
		public boolean inRestaurantFile() {
			return this.inRestaurantFile;
		}

		/**
		 * 공공 인증 식당 수집 파일의 이름으로 찾는다. 모르는 이름은 빈 값 — 부르는 쪽이 멈출지 정한다.
		 * 파일에 올 수 없는 목록({@link #inRestaurantFile()} 이 거짓)도 빈 값이다.
		 */
		public static Optional<Source> fromFileLabel(String label) {
			if (label == null) {
				return Optional.empty();
			}
			String trimmed = label.trim();
			return Arrays.stream(values()).filter(source -> source.inRestaurantFile && source.fileLabel.equals(trimmed))
					.findFirst();
		}

		/** 응답에서 온 낱말로 찾는다. 모르는 낱말은 빈 값 — 응답이 깨지지 않게 부르는 쪽이 건너뛴다. */
		public static Optional<Source> fromCode(String code) {
			if (code == null) {
				return Optional.empty();
			}
			return Arrays.stream(values()).filter(source -> source.name().equals(code)).findFirst();
		}
	}

	/** 구 하나와 그 안의 동 하나. */
	public record Area(String gu, String dong) {

		public Area {
			if (gu == null || gu.isBlank() || dong == null || dong.isBlank()) {
				throw new IllegalArgumentException("구와 동이 둘 다 있어야 한다: " + gu + " / " + dong);
			}
		}

		/** 표식의 열쇠 — {@code "해운대구/우동"}. */
		public String key() {
			return areaKey(this.gu, this.dong);
		}

		/** 열쇠에서 되돌린다. 모양이 틀리면 빈 값 — 남이 다른 꼴로 넣은 표식이 응답을 깨지 못하게. */
		public static Optional<Area> fromKey(String key) {
			if (key == null) {
				return Optional.empty();
			}
			int at = key.indexOf(AREA_SEPARATOR);
			if (at <= 0 || at == key.length() - 1) {
				return Optional.empty();
			}
			String gu = key.substring(0, at).trim();
			String dong = key.substring(at + 1).trim();
			if (gu.isEmpty() || dong.isEmpty() || dong.indexOf(AREA_SEPARATOR) >= 0) {
				return Optional.empty();
			}
			return Optional.of(new Area(gu, dong));
		}
	}

	/** 구·동 표식의 열쇠. 길이 상한({@link #KEY_MAX})은 부르는 쪽(읽는 쪽)이 먼저 거른다. */
	public static String areaKey(String gu, String dong) {
		return gu + AREA_SEPARATOR + dong;
	}

	/** 구 전체를 고르는 열쇠 앞머리 — {@code "해운대구/"}. {@code LIKE} 에 쓰려면 이스케이프가 따로 필요하다. */
	public static String guKeyPrefix(String gu) {
		return gu + AREA_SEPARATOR;
	}
}
