package com.gabolle.backend.place.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 사용자 입력 코드와 장소 피처를 잇는 대조표 한 줄 (S15P21E201-545).
 *
 * <p>명세 4.3 이 "사용자 입력 코드와 장소 피처 코드가 달라지면 안 된다" 고 요구한다. 그 대조를
 * 코드에만 두면 분석 쿼리가 못 보고, 문서에만 두면 코드와 갈린다. 그래서 표다.
 *
 * <p>🔴 <b>이 표가 갈래 조회(-473)의 정본이다.</b> "표식을 새로 붙이면 코드를 고치지 않아도 반영된다"
 * 를 지키려면 갈래 목록 자체가 여기서 나와야 한다. 자바는 읽기만 한다 — 9번째 취향 차원이 이 표에
 * 들어오면 조회 응답에 자동으로 나타난다.
 *
 * <p>🔴 읽기 전용이다. 이 표는 마이그레이션이 채우고 {@code PlaceFeatureCodeMapTest} 가 빠짐을 검사한다.
 * 런타임에 고치면 그 검사가 무의미해진다.
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
