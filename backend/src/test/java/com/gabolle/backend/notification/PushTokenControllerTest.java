package com.gabolle.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.common.security.GlobalAuthExceptionHandler;
import com.gabolle.backend.notification.application.PushTokenService;
import com.gabolle.backend.notification.infra.PushTokenJpaEntity;
import com.gabolle.backend.notification.infra.PushTokenJpaRepository;
import com.gabolle.backend.notification.presentation.PushTokenController;
import com.gabolle.backend.notification.presentation.PushTokenExceptionHandler;

/**
 * 기기 푸시 토큰 등록·해제 — S15P21E201-1391.
 *
 * <p>계약(경로·본문)은 앱이 이미 부르고 있는 그대로다
 * ({@code frontend/src/notifications/pushToken.ts}, 1429 로 머지됨). 그래서 이 시험이 지키는 것은
 * 「우리가 정한 모양」이 아니라 「앱이 보내는 모양」이다.
 */
class PushTokenControllerTest {

	private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");

	private MockMvc mockMvc;

	private final List<PushTokenJpaEntity> rows = new ArrayList<>();

	private UUID me;

	private UUID someoneElse;

	@BeforeEach
	void setUp() {
		this.rows.clear();
		this.me = UUID.randomUUID();
		this.someoneElse = UUID.randomUUID();
		PushTokenService service = new PushTokenService(fakeRepository(), Clock.fixed(NOW, ZoneOffset.UTC));
		this.mockMvc = MockMvcBuilders.standaloneSetup(new PushTokenController(service))
				.setControllerAdvice(new PushTokenExceptionHandler(), new GlobalAuthExceptionHandler(org.mockito.Mockito.mock(com.gabolle.backend.common.security.SecurityEventLogger.class)))
				.build();
	}

	private Authentication as(UUID userId) {
		return new TestingAuthenticationToken(userId.toString(), null);
	}

	private void register(UUID userId, String token, String platform) throws Exception {
		this.mockMvc.perform(put("/api/v1/me/push-tokens")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"token\":\"" + token + "\",\"platform\":\"" + platform + "\"}")
						.principal(as(userId)))
				.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("앱이 보내는 모양 그대로 받는다 — PUT /api/v1/me/push-tokens { token, platform }")
	void storesTheTokenTheAppSends() throws Exception {
		register(this.me, "ExponentPushToken[abc]", "ios");

		assertThat(this.rows).singleElement()
				.satisfies((row) -> {
					assertThat(row.userId()).isEqualTo(this.me);
					assertThat(row.token()).isEqualTo("ExponentPushToken[abc]");
					assertThat(row.platform()).isEqualTo("ios");
				});
	}

	@Test
	@DisplayName("한 사람이 기기를 여러 대 쓰면 줄이 여러 개다")
	void oneUserCanHaveManyDevices() throws Exception {
		register(this.me, "ExponentPushToken[phone]", "android");
		register(this.me, "ExponentPushToken[tablet]", "android");

		assertThat(this.rows).hasSize(2);
	}

	@Test
	@DisplayName("같은 기기를 다시 올리면 줄이 늘지 않는다")
	void registeringTheSameDeviceTwiceKeepsOneRow() throws Exception {
		register(this.me, "ExponentPushToken[abc]", "ios");
		register(this.me, "ExponentPushToken[abc]", "ios");

		assertThat(this.rows).hasSize(1);
	}

	@Test
	@DisplayName("🔴 기기를 다른 사람이 쓰면 주인을 덮는다 — 안 덮으면 앞사람 알림이 뒷사람 폰에 뜬다")
	void aDeviceHandedOverChangesOwner() throws Exception {
		register(this.me, "ExponentPushToken[shared]", "android");

		register(this.someoneElse, "ExponentPushToken[shared]", "android");

		assertThat(this.rows).singleElement()
				.satisfies((row) -> assertThat(row.userId()).isEqualTo(this.someoneElse));
	}

	@Test
	@DisplayName("로그아웃하면 그 기기를 뗀다")
	void unregisterRemovesTheDevice() throws Exception {
		register(this.me, "ExponentPushToken[abc]", "ios");

		this.mockMvc.perform(delete("/api/v1/me/push-tokens/{token}", "ExponentPushToken[abc]").principal(as(this.me)))
				.andExpect(status().isNoContent());

		assertThat(this.rows).isEmpty();
	}

	@Test
	@DisplayName("🔴 남의 기기는 못 뗀다 — 토큰이 주소에 실려 오므로 주인을 본다")
	void cannotUnregisterSomeoneElsesDevice() throws Exception {
		register(this.someoneElse, "ExponentPushToken[theirs]", "ios");

		this.mockMvc.perform(delete("/api/v1/me/push-tokens/{token}", "ExponentPushToken[theirs]").principal(as(this.me)))
				// 「그 토큰이 있다」를 알려 주지 않는다 — 없을 때와 같은 답이다.
				.andExpect(status().isNoContent());

		assertThat(this.rows).hasSize(1);
	}

	@Test
	@DisplayName("없는 기기를 떼도 204 다")
	void unregisteringAnUnknownDeviceIsFine() throws Exception {
		this.mockMvc.perform(delete("/api/v1/me/push-tokens/{token}", "ExponentPushToken[nope]").principal(as(this.me)))
				.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("🔴 모르는 갈래는 400 이다 — 표의 CHECK 에 걸리기 전에 여기서 막는다")
	void unknownPlatformIsRejected() throws Exception {
		this.mockMvc.perform(put("/api/v1/me/push-tokens")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"token\":\"ExponentPushToken[abc]\",\"platform\":\"windows\"}")
						.principal(as(this.me)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("PUSH_TOKEN_INVALID_REQUEST"));

		assertThat(this.rows).isEmpty();
	}

	@Test
	@DisplayName("빈 토큰은 400 이다")
	void blankTokenIsRejected() throws Exception {
		this.mockMvc.perform(put("/api/v1/me/push-tokens")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"token\":\"  \",\"platform\":\"ios\"}")
						.principal(as(this.me)))
				.andExpect(status().isBadRequest());

		assertThat(this.rows).isEmpty();
	}

	@Test
	@DisplayName("로그인하지 않았으면 등록되지 않는다 — 익명 출입증에 매달면 로그아웃 뒤에도 알림이 간다")
	void anonymousCannotRegister() throws Exception {
		this.mockMvc.perform(put("/api/v1/me/push-tokens")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"token\":\"ExponentPushToken[abc]\",\"platform\":\"ios\"}"))
				.andExpect(status().isUnauthorized());

		assertThat(this.rows).isEmpty();
	}

	/**
	 * 저장소 대역 — 표의 UNIQUE(token) 를 흉내 낸다. 그 규칙이 이 기능의 핵심이라 대역에도 있어야 한다.
	 *
	 * <p>JpaRepository 는 메서드가 많아 손으로 다 구현할 수 없다. 쓰는 셋만 답을 만들어 둔다.
	 */
	private PushTokenJpaRepository fakeRepository() {
		PushTokenJpaRepository repository = org.mockito.Mockito.mock(PushTokenJpaRepository.class);
		org.mockito.Mockito.when(repository.findByToken(org.mockito.ArgumentMatchers.anyString()))
				.thenAnswer((call) -> this.rows.stream()
						.filter((row) -> row.token().equals(call.getArgument(0))).findFirst());
		org.mockito.Mockito.when(repository.save(org.mockito.ArgumentMatchers.any(PushTokenJpaEntity.class)))
				.thenAnswer((call) -> {
					PushTokenJpaEntity saved = call.getArgument(0);
					this.rows.add(saved);
					return saved;
				});
		org.mockito.Mockito.when(repository.deleteByTokenAndUserId(
				org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(UUID.class)))
				.thenAnswer((call) -> this.rows.removeIf((row) -> row.token().equals(call.getArgument(0))
						&& row.userId().equals(call.getArgument(1))) ? 1L : 0L);
		return repository;
	}
}
