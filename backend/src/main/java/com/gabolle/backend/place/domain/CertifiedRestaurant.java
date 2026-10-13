package com.gabolle.backend.place.domain;

import java.util.Arrays;
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
 */
public final class CertifiedRestaurant {

	/** {@code place.source_type} 과 이 장소들에 단 표식의 {@code source_type}. 되돌릴 때 이 값 하나로 걸린다. */
	public static final String SOURCE_TYPE = "BUSAN_CERTIFIED_FOOD";

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
	 * 인증한 목록. 파일에는 한국어 이름으로 오고, DB 와 응답에는 {@link #name()} 이 간다 — 화면이 언어마다 이름표를
	 * 따로 달 수 있게 낱말을 고정해 둔다. 앞의 둘은 장소 상세의 공인 표식({@code RECOGNITION})이 쓰는 낱말과 같다.
	 */
	public enum Source {

		/** 부산광역시 맛집정보 — 시가 직접 소개하는 식당. */
		BUSAN_FOOD_INFO("부산시 맛집정보"),

		/** 구·군이 지정한 모범음식점. 장소 상세의 {@code RECOGNITION} 과 같은 낱말이다. */
		MODEL_RESTAURANT("모범음식점"),

		/** 택시기사가 추천한 식당(택슐랭). 장소 상세의 {@code RECOGNITION} 과 같은 낱말이다. */
		TAXI_DRIVER_PICK("택슐랭");

		private final String fileLabel;

		Source(String fileLabel) {
			this.fileLabel = fileLabel;
		}

		/** 수집 파일이 쓰는 한국어 이름 — 사전({@code place_feature_code})의 한국어 이름과도 같다. */
		public String fileLabel() {
			return this.fileLabel;
		}

		/** 파일의 이름으로 찾는다. 모르는 이름은 빈 값 — 부르는 쪽이 멈출지 정한다. */
		public static Optional<Source> fromFileLabel(String label) {
			if (label == null) {
				return Optional.empty();
			}
			String trimmed = label.trim();
			return Arrays.stream(values()).filter(source -> source.fileLabel.equals(trimmed)).findFirst();
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
