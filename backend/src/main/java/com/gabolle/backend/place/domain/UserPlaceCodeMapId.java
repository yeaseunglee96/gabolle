package com.gabolle.backend.place.domain;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * {@link UserPlaceCodeMap} 의 복합 키. 표에 대리 키가 없고
 * {@code pk_user_place_code_map (user_input_kind, user_input_code, place_feature_type)} 이 그대로 키다.
 *
 * <p>같은 사용자 입력이 장소 피처 둘에 걸릴 수 있어서 셋이 다 키다. 실제로 {@code MOBILITY} 가
 * {@code ACCESSIBILITY_TAG} 와 {@code STAIRS_PRESENT} 둘에 걸려 있다.
 */
@Embeddable
public class UserPlaceCodeMapId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Enumerated(EnumType.STRING)
	@Column(name = "user_input_kind", nullable = false, length = 20)
	private UserInputKind userInputKind;

	/** 취향 여덟 차원 또는 제약 세 종류의 코드. 값 목록은 {@code ck_user_place_code_map_code} 가 정본이다. */
	@Column(name = "user_input_code", nullable = false, length = 50)
	private String userInputCode;

	@Column(name = "place_feature_type", nullable = false, length = 50)
	private String placeFeatureType;

	protected UserPlaceCodeMapId() {
	}

	public UserPlaceCodeMapId(UserInputKind userInputKind, String userInputCode, String placeFeatureType) {
		this.userInputKind = userInputKind;
		this.userInputCode = userInputCode;
		this.placeFeatureType = placeFeatureType;
	}

	public UserInputKind getUserInputKind() {
		return userInputKind;
	}

	public String getUserInputCode() {
		return userInputCode;
	}

	public String getPlaceFeatureType() {
		return placeFeatureType;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof UserPlaceCodeMapId that)) {
			return false;
		}
		return userInputKind == that.userInputKind
				&& Objects.equals(userInputCode, that.userInputCode)
				&& Objects.equals(placeFeatureType, that.placeFeatureType);
	}

	@Override
	public int hashCode() {
		return Objects.hash(userInputKind, userInputCode, placeFeatureType);
	}
}
