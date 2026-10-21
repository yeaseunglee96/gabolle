package com.gabolle.backend.appversion.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.appversion.application.AppReleasePort;
import com.gabolle.backend.appversion.domain.AppRelease;

/** {@code GET /api/v1/app/version} — 로그인 없이(주체 없이) 부른다. */
class AppVersionControllerTest {

	private final Map<String, AppRelease> rows = new HashMap<>();
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		AppReleasePort port = platform -> Optional.ofNullable(this.rows.get(platform));
		this.rows.put("ios", new AppRelease("ios", "1.0.3", "1.0.0", "https://apps.apple.com/app/id6811252919"));
		this.rows.put("android", new AppRelease("android", "1.0.3", "1.0.0",
				"https://play.google.com/store/apps/details?id=com.gabolle.app"));
		this.mockMvc = MockMvcBuilders.standaloneSetup(new AppVersionController(port)).build();
	}

	@Test
	@DisplayName("주체 없이 부르면 그 플랫폼의 최신·최소 지원 버전과 스토어 주소가 온다")
	void returnsReleaseWithoutLogin() throws Exception {
		this.mockMvc.perform(get("/api/v1/app/version").param("platform", "ios"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.platform").value("ios"))
				.andExpect(jsonPath("$.data.latestVersion").value("1.0.3"))
				.andExpect(jsonPath("$.data.minSupportedVersion").value("1.0.0"))
				.andExpect(jsonPath("$.data.storeUrl").value("https://apps.apple.com/app/id6811252919"));
	}

	@Test
	@DisplayName("대소문자를 가리지 않는다 — Platform.OS 는 소문자지만 사람이 부를 때를 위해")
	void platformIsCaseInsensitive() throws Exception {
		this.mockMvc.perform(get("/api/v1/app/version").param("platform", "ANDROID"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.storeUrl")
						.value("https://play.google.com/store/apps/details?id=com.gabolle.app"));
	}

	@Test
	@DisplayName("🔴 모르는 플랫폼은 400 — 아무 값이나 지어내 답하지 않는다")
	void unknownPlatformIs400() throws Exception {
		this.mockMvc.perform(get("/api/v1/app/version").param("platform", "web"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("🔴 platform 이 빠져도 400 — 스프링 오류 디스패치로 넘기면 로그인 전 앱이 401 을 받는다")
	void missingPlatformIs400() throws Exception {
		this.mockMvc.perform(get("/api/v1/app/version"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("행이 없으면 404 — 앱은 실패를 「안내 안 함」으로 다룬다")
	void missingRowIs404() throws Exception {
		this.rows.remove("android");
		this.mockMvc.perform(get("/api/v1/app/version").param("platform", "android"))
				.andExpect(status().isNotFound());
	}
}
