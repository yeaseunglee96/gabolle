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
 * 메뉴판 사진에서 글자를 읽는다 — S15P21E201-1025.
 *
 * <pre>
 * POST /api/v1/menu-scans      multipart/form-data, part 이름은 "image"
 *                              선택 part "language" — 앱 언어(ko/en/ja/zh-Hans/zh-Hant).
 *                              없으면 한국어로 취급해 옛 앱 빌드와 동작이 같다
 * </pre>
 *
 * <h2>🔴 왜 서버가 중계하나 — 앱에 키를 넣으면 추출된다</h2>
 * React Native 번들은 열어볼 수 있다. 키는 <b>서버에만</b> 둔다.
 *
 * <pre>
 * 앱  →  우리 서버  →  GMS 중계  →  모델
 *               ↑ 키는 여기에만
 * </pre>
 *
 * <h2>🔴 사진을 저장하지 않는다</h2>
 * 요청에 실려 오고, 읽히고, 사라진다. 기록 사진 업로드 경로를 재활용하지 않는 이유는
 * {@link MenuScanService} 의 javadoc 에 있다 — 그쪽은 <b>주소만 알면 로그인 없이 열린다.</b>
 *
 * <h2>🔴 응답이 「없다」를 말하지 않는다</h2>
 * 이 API 가 주는 것은 <b>「이런 글자가 보인다」</b> 뿐이다. 「갑각류 없음」·「안전」 같은
 * 판단을 담을 칸이 {@link MenuScanResponse} 에 <b>아예 없다.</b> 사람이 다칠 수 있는 자리라
 * 화면이 그것을 지어낼 여지를 응답 모양에서 없앤다.
 *
 * <p>{@code @Profile({"db","dev"})} — 2026-09-16 (S15P21E201-1038) 에 붙였다. 이 저장소의
 * 컨트롤러 50개가 전부 갖고 있는데 이것 하나만 없었다. 그 상태에서는 DB 없이 띄우는
 * 프로필에서 <b>이 경로 하나만 살아 있고</b> 나머지는 없는 앱이 된다. 지금은 한도 집계가
 * 표에 있어 DB 가 없으면 동작 자체가 성립하지 않으므로 배선도 그렇게 맞춘다.
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
