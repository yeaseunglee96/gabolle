package com.gabolle.backend.functional;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import com.gabolle.backend.auth.api.AuthUserResponse;
import com.gabolle.backend.auth.service.AccountDeletionService;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.functional.support.AuthedClient;
import com.gabolle.backend.functional.support.FunctionalJourneyTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 탈퇴 — 실제 소켓과 실제 {@code SecurityFilterChain} 을 지나는 검사 (S15P21E201-837).
 *
 * <h2>왜 HTTP 로 다시 보나</h2>
 *
 * {@code AccountDeletionIntegrationTest} 는 서비스를 직접 부른다. 그래서 <b>요청 본문이 실제로
 * 어떻게 묶이는지</b> 를 못 본다. 이번 변경의 핵심이 바로 본문 계약이다 — 비밀번호를
 * {@code @NotBlank} 에서 내리고 확인 값을 필수로 올렸다. 그 두 칸이 잘못 묶이면 서비스 검사는
 * 전부 초록인데 실제 요청만 400 으로 떨어진다. 2026-09-10 에 가입 경로에서 똑같은 일이 났다
 * (S15P21E201-816 — 원시 {@code boolean} 이라 키를 빼면 이유 없는 400 이 나갔다).
 *
 * <h2>🔴 소셜 계정을 어떻게 만드나</h2>
 *
 * 소셜 로그인을 실제로 태우려면 provider 를 흉내 내야 하고 그건 이 검사의 목적이 아니다. 대신
 * 가입으로 만든 계정에서 <b>자격증명 행을 지우고 소셜 신원 행을 넣어</b> 소셜로만 가입한 계정과
 * 같은 모양으로 만든다. 이 기능이 보는 것은 "자격증명이 있느냐" 하나뿐이므로 그 모양이면 충분하다.
 */
class AccountDeletionJourneyFunctionalTest extends FunctionalJourneyTest {

	private static final String CONFIRM = AccountDeletionService.CONFIRMATION_PHRASE;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("완료 기준 — 소셜로만 가입한 계정이 확인 값만 보내면 204 로 탈퇴된다")
	void socialOnlyAccountCanDeleteItself() {
		AuthedClient authed = loginAsNewUser("delete-social");
		UUID userId = currentUserId(authed);
		makeSocialOnly(userId);

		ResponseEntity<String> response = authed.delete("/api/v1/auth/me",
				Map.of("confirmation", CONFIRM), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(countRows("auth_identity", userId)).isZero();
		assertThat(countRows("auth_session", userId)).isZero();
	}

	@Test
	@DisplayName("완료 기준 — 비밀번호로 가입한 계정도 확인 값만으로 탈퇴된다")
	void passwordAccountCanDeleteWithConfirmationAlone() {
		AuthedClient authed = loginAsNewUser("delete-local");
		UUID userId = currentUserId(authed);

		ResponseEntity<String> response = authed.delete("/api/v1/auth/me",
				Map.of("confirmation", CONFIRM), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(countRows("local_credential", userId)).isZero();
	}

	@Test
	@DisplayName("🔴 확인 값이 없으면 400 이고 계정은 그대로다 — 본문 계약이 실제로 도는지 본다")
	void missingConfirmationIsRejectedAndNothingIsDeleted() {
		AuthedClient authed = loginAsNewUser("delete-noconfirm");
		UUID userId = currentUserId(authed);

		ResponseEntity<String> response = authed.delete("/api/v1/auth/me", Map.of(), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(countRows("local_credential", userId)).isEqualTo(1);
		// 계정이 살아 있다 — 같은 표로 다시 부를 수 있다.
		assertThat(currentUserId(authed)).isEqualTo(userId);
	}

	@Test
	@DisplayName("🔴 확인 값이 다르면 DELETION_NOT_CONFIRMED 로 거절한다 — 대소문자도 본다")
	void wrongConfirmationIsRejected() {
		AuthedClient authed = loginAsNewUser("delete-wrongconfirm");
		UUID userId = currentUserId(authed);

		ResponseEntity<String> response = authed.delete("/api/v1/auth/me",
				Map.of("confirmation", "delete"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("DELETION_NOT_CONFIRMED");
		assertThat(countRows("local_credential", userId)).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 비밀번호를 함께 보냈는데 틀리면 401 이고 아무것도 지워지지 않는다")
	void wrongPasswordStillBlocksDeletion() {
		AuthedClient authed = loginAsNewUser("delete-wrongpw");
		UUID userId = currentUserId(authed);

		ResponseEntity<String> response = authed.delete("/api/v1/auth/me",
				Map.of("confirmation", CONFIRM, "password", "not-the-password"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(countRows("local_credential", userId)).isEqualTo(1);
	}

	// ── 도구 ──────────────────────────────────────────────────────────────────

	private UUID currentUserId(AuthedClient authed) {
		ResponseEntity<ApiResponse<AuthUserResponse>> me = authed.get("/api/v1/auth/me",
				new ParameterizedTypeReference<ApiResponse<AuthUserResponse>>() {
				});
		assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
		return me.getBody().data().userId();
	}

	/** 자격증명을 지우고 소셜 신원을 붙여 "소셜로만 가입한 계정" 과 같은 모양으로 만든다. */
	private void makeSocialOnly(UUID userId) {
		this.jdbcTemplate.update("DELETE FROM local_credential WHERE user_id = ?", userId);
		this.jdbcTemplate.update("""
				INSERT INTO auth_identity (identity_id, user_id, provider, provider_subject, provider_email, linked_at)
				VALUES (?, ?, 'KAKAO', ?, NULL, now())
				""", UUID.randomUUID(), userId, "kakao-" + userId);
		assertThat(countRows("local_credential", userId)).isZero();
	}

	private Integer countRows(String table, UUID userId) {
		return this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
	}
}
