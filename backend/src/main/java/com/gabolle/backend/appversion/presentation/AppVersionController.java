package com.gabolle.backend.appversion.presentation;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.appversion.application.AppReleasePort;
import com.gabolle.backend.appversion.domain.AppRelease;
import com.gabolle.backend.common.api.ApiResponse;

/**
 * 앱 업데이트 안내(S15P21E201-2048). 로그인 전에 불린다 — SecurityConfig 가 이 경로만 연다.
 * 스토어에 이미 공개된 정보만 답하고 쓰기가 없다.
 */
@RestController
@RequestMapping("/api/v1/app")
@Profile({ "db", "dev" })
public class AppVersionController {

	private static final Set<String> PLATFORMS = Set.of("ios", "android");

	private final AppReleasePort appReleasePort;

	public AppVersionController(AppReleasePort appReleasePort) {
		this.appReleasePort = appReleasePort;
	}

	@GetMapping("/version")
	public ResponseEntity<ApiResponse<AppRelease>> version(@RequestParam("platform") String platform) {
		String normalized = platform.trim().toLowerCase(Locale.ROOT);
		if (!PLATFORMS.contains(normalized)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
		}
		return this.appReleasePort.find(normalized)
				.map(release -> ResponseEntity.ok(ApiResponse.success(release, "req_" + UUID.randomUUID())))
				.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
	}
}
