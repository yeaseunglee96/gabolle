package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.itinerary.presentation.dto.ItineraryEditJobRequest;

/**
 * 빼기 이유는 정해진 코드만 받는다 (S15P21E201-1689). 비어 있으면 받는다 — 이미 나간 앱(build 44)은 이 칸을 안 보낸다.
 */
class RemovalReasonValidationTest {

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	@DisplayName("🔴 비어 있거나(안 보냄 · 빈 글자) 앱이 쓰는 다섯 코드면 받는다")
	void emptyOrKnownCodesPass() {
		for (String reason : new String[] { null, "", "ALREADY_VISITED", "NOT_INTERESTED", "TOO_FAR", "CLOSED",
				"OTHER" }) {
			assertThat(this.validator.validate(new ItineraryEditJobRequest(3, reason, null, null)))
					.as("이유 %s", reason).isEmpty();
		}
	}

	@Test
	@DisplayName("🔴 목록에 없는 값은 400 이다 — 자유 글자 · 소문자 · 한글")
	void unknownValuesAreRejected() {
		for (String reason : new String[] { "멀어요", "too_far", "FAR", "그냥" }) {
			assertThat(this.validator.validate(new ItineraryEditJobRequest(3, reason, null, null)))
					.as("이유 %s", reason).isNotEmpty();
		}
	}
}
