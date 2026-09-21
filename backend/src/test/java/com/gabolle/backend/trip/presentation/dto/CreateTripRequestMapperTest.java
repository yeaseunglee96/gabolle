package com.gabolle.backend.trip.presentation.dto;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.trip.application.TripCreationService;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code CreateTripRequest.spendProfile} 이 다른 여덟 차원과 같은 {@code PreferenceAnswer}
 * 로 합쳐지는지 본다.
 */
class CreateTripRequestMapperTest {

	private CreateTripRequest baseRequest(SpendProfileAnswerInput spendProfile) {
		return new CreateTripRequest(
				LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12),
				null, null, null, 1, null, null,
				List.of(new CreateTripRequest.PreferenceAnswerInput("category", "\"CAFE\"", "SELECTED")),
				spendProfile,
				List.of());
	}

	@Test
	@DisplayName("spendProfile 을 안 보내면 preferences 목록이 그대로다")
	void omittedSpendProfileAddsNothing() {
		TripCreationService.Command command = CreateTripRequestMapper.toCommand(baseRequest(null), "usr_1");

		assertThat(command.preferences()).hasSize(1);
		assertThat(command.preferences().get(0).dimension()).isEqualTo("CATEGORY");
	}

	@Test
	@DisplayName("🔴 spendProfile 이 SPEND_PROFILE PreferenceAnswer 로 합쳐진다")
	void spendProfileIsMergedAsPreferenceAnswer() {
		SpendProfileAnswerInput spendProfile = new SpendProfileAnswerInput("\"LUXURY\"", "SELECTED");

		TripCreationService.Command command = CreateTripRequestMapper.toCommand(baseRequest(spendProfile), "usr_1");

		assertThat(command.preferences()).hasSize(2);
		assertThat(command.preferences()).filteredOn(a -> a.dimension().equals("SPEND_PROFILE"))
				.hasSize(1)
				.allSatisfy(a -> {
					assertThat(a.valueJson()).isEqualTo("\"LUXURY\"");
					assertThat(a.status()).isEqualTo(PreferenceSnapshot.AnswerStatus.SELECTED);
				});
		assertThat(command.preferences()).anySatisfy(a -> assertThat(a.dimension()).isEqualTo("CATEGORY"));
	}

	@Test
	@DisplayName("건너뛴 spendProfile(SKIPPED)도 그대로 옮긴다 — 값 없이")
	void skippedSpendProfileCarriesNoValue() {
		SpendProfileAnswerInput spendProfile = new SpendProfileAnswerInput(null, "SKIPPED");

		TripCreationService.Command command = CreateTripRequestMapper.toCommand(baseRequest(spendProfile), "usr_1");

		assertThat(command.preferences()).filteredOn(a -> a.dimension().equals("SPEND_PROFILE"))
				.hasSize(1)
				.allSatisfy(a -> {
					assertThat(a.valueJson()).isNull();
					assertThat(a.status()).isEqualTo(PreferenceSnapshot.AnswerStatus.SKIPPED);
				});
	}
}
