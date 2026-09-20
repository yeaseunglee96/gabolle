package com.gabolle.backend.common.privacy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/** 일반 추천 로그에 개인정보가 섞이면 저장 전에 막힌다. */
class SensitivePayloadGuardTest {

	private final SensitivePayloadGuard guard = new SensitivePayloadGuard();

	@Test
	@DisplayName("정밀 좌표 키가 있으면 거부한다")
	void rejectsPreciseCoordinateKeys() {
		Map<String, Object> payload = Map.of("origin", Map.of("lat", 35.1796, "lng", 129.0756));

		assertThatThrownBy(() -> this.guard.verify(payload, "feature_values"))
				.isInstanceOf(SensitiveDataInPayloadException.class);
	}

	@Test
	@DisplayName("키 이름이 평범해도 값이 좌표 쌍이면 거부한다")
	void rejectsCoordinatePairValues() {
		Map<String, Object> payload = Map.of("origin_hint", "35.1796, 129.0756");

		assertThatThrownBy(() -> this.guard.verify(payload, "feature_values"))
				.isInstanceOf(SensitiveDataInPayloadException.class)
				.hasMessageContaining("정밀 좌표");
	}

	@Test
	@DisplayName("이메일과 전화번호는 어디에 있든 거부한다")
	void rejectsDirectIdentifiers() {
		assertThatThrownBy(() -> this.guard.verify(Map.of("memo", "someone@example.com"), "payload"))
				.isInstanceOf(SensitiveDataInPayloadException.class);
		assertThatThrownBy(() -> this.guard.verify(Map.of("memo", "010-1234-5678"), "payload"))
				.isInstanceOf(SensitiveDataInPayloadException.class);
	}

	@Test
	@DisplayName("알레르기 자유 입력 원문과 광고 ID 는 키 이름만으로 거부한다")
	void rejectsSensitiveFreeTextAndAdvertisingIds() {
		assertThatThrownBy(() -> this.guard.verify(Map.of("allergy_note", "짧게"), "payload"))
				.isInstanceOf(SensitiveDataInPayloadException.class);
		assertThatThrownBy(() -> this.guard.verify(Map.of("advertising_id", "x"), "payload"))
				.isInstanceOf(SensitiveDataInPayloadException.class);
	}

	@Test
	@DisplayName("외부 지도 API 원본 응답은 키 이름만으로 거부한다")
	void rejectsRawMapApiResponses() {
		assertThatThrownBy(() -> this.guard.verify(Map.of("kakao_raw_response", Map.of()), "payload"))
				.isInstanceOf(SensitiveDataInPayloadException.class);
	}

	@Test
	@DisplayName("중첩된 리스트 안쪽도 본다")
	void walksNestedCollections() {
		Map<String, Object> payload = Map.of("track", List.of(Map.of("t", 1)));

		assertThatThrownBy(() -> this.guard.verify(payload, "payload"))
				.isInstanceOf(SensitiveDataInPayloadException.class);
	}

	@Test
	@DisplayName("문제가 된 자리를 경로로 알려준다")
	void reportsOffendingPath() {
		Map<String, Object> payload = Map.of("origin", Map.of("latitude", 35.1));

		Throwable thrown = catchThrowable(() -> this.guard.verify(payload, "feature_values"));

		assertThat(thrown).isInstanceOf(SensitiveDataInPayloadException.class);
		assertThat(((SensitiveDataInPayloadException) thrown).getPath())
				.isEqualTo("feature_values.origin.latitude");
	}

	@Test
	@DisplayName("코드와 내부 ID 로만 이뤄진 정상 피처는 통과한다")
	void allowsCodesAndInternalIdentifiers() {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("place_id", "8f14e45f-ceea-467a-9575-8b1b0f5d1c3a");
		payload.put("user_id", "3d1f0f22-2f0b-4a4c-8a19-1a6a3a4b5c6d");
		payload.put("category_code", "CAFE");
		payload.put("quietness_score", 0.82);
		payload.put("constraint_snapshot_id", "9b7d1c33-1a2b-4c3d-8e5f-0a1b2c3d4e5f");
		// "그 피처를 못 구했다" 는 null 로 남는다. 0 으로 채우지 않는다.
		payload.put("shade_ratio", null);

		assertThatCode(() -> this.guard.verify(payload, "feature_values")).doesNotThrowAnyException();
	}
}
