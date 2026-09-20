package com.gabolle.backend.menuscan.presentation;

import java.io.IOException;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.menuscan.application.MenuScanService;
import com.gabolle.backend.menuscan.presentation.dto.MenuScanResponse;

/**
 * 메뉴판 사진에서 글자를 읽는다. {@code POST /api/v1/menu-scans} 는 multipart 로 {@code image} 를
 * 받고, 선택 part {@code language}(ko/en/ja/zh-Hans/zh-Hant)가 없으면 한국어로 취급한다.
 *
 * <p>앱이 모델을 직접 부르지 않고 서버가 중계한다 — React Native 번들은 열어볼 수 있어 키를 넣으면
 * 추출된다. 키는 서버에만 둔다.
 *
 * <p>사진을 저장하지 않는다. 기록 사진 업로드 경로를 재활용하지 않는 이유는
 * {@link MenuScanService} 의 javadoc 에 있다.
 *
 * <p>이 API 가 주는 것은 «이런 글자가 보인다»뿐이다. «갑각류 없음»·«안전» 같은 판단을 담을 칸이
 * {@link MenuScanResponse} 에 아예 없다.
 *
 * <p>한도 집계가 표에 있어 DB 가 없으면 동작이 성립하지 않으므로 프로필을 그렇게 맞춘다.
 */
@RestController
@Profile({ "db", "dev" })
public class MenuScanController {

	private final MenuScanService service;

	public MenuScanController(MenuScanService service) {
		this.service = service;
	}

	@PostMapping(value = "/api/v1/menu-scans", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<MenuScanResponse> scan(@RequestPart("image") MultipartFile image,
			@RequestPart(value = "language", required = false) String language,
			Authentication authentication) throws IOException {

		UUID userId = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.service.scan(userId, image.getBytes(), language),
				"req_" + UUID.randomUUID());
	}
}
