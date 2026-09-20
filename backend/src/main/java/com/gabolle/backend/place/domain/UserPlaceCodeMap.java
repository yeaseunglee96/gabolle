package com.gabolle.backend.place.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 사용자 입력 코드와 장소 피처를 잇는 대조표 한 줄. 대조를 코드에만 두면 분석 쿼리가 못 보고
 * 문서에만 두면 코드와 갈려서, 표로 둔다.
 *
 * <p>갈래 조회의 정본이 이 표다. 자바는 읽기만 하므로 9번째 취향 차원이 이 표에 들어오면 조회
 * 응답에 자동으로 나타난다. 표는 마이그레이션이 채우고 {@code PlaceFeatureCodeMapTest} 가 빠짐을
 * 검사하니, 런타임에 고치지 않는다.
 */
@Entity
@Table(name = "user_place_code_map")
public class UserPlaceCodeMap {

	@EmbeddedId
	private UserPlaceCodeMapId id;

	@Enumerated(EnumType.STRING)
	@Column(name = "match_kind", nullable = false, length = 20)
	private MatchKind matchKind;

	@Column(name = "note")
	private String note;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected UserPlaceCodeMap() {
	}

	public UserPlaceCodeMapId getId() {
		return id;
	}

	public UserInputKind getUserInputKind() {
		return id.getUserInputKind();
	}

	/** 취향 차원 또는 제약 종류의 코드. 예: {@code CATEGORY}, {@code ALLERGY}. */
	public String getUserInputCode() {
		return id.getUserInputCode();
	}

	/** 짝이 되는 장소 피처 종류. 예: {@code INTEREST_TAG}, {@code ALLERGEN_TAG}. */
	public String getPlaceFeatureType() {
		return id.getPlaceFeatureType();
	}

	public MatchKind getMatchKind() {
		return matchKind;
	}

	public String getNote() {
		return note;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	/** 태그형인가. 태그형이면 {@code place_feature.feature_key} 에 코드가 있다. */
	public boolean isTagShaped() {
		return matchKind == MatchKind.TAG_OVERLAP || matchKind == MatchKind.HARD_FILTER;
	}
}
