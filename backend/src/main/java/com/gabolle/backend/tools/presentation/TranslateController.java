package com.gabolle.backend.tools.presentation;

import java.util.Locale;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.tools.application.TranslationService;
import com.gabolle.backend.tools.domain.TranslationDirection;
import com.gabolle.backend.tools.domain.TranslationRequest;
import com.gabolle.backend.tools.domain.TranslationResult;
import com.gabolle.backend.tools.presentation.dto.TranslateRequestDto;
import com.gabolle.backend.tools.presentation.dto.TranslateResponseDto;

/**
 * 문장 하나를 번역해 준다 — S15P21E201-343.
 *
 * <h2>왜 서버가 대신 부르는가</h2>
 * 번역 업체를 화면에서 직접 부르면 업체 키가 브라우저에 그대로 노출된다. 서버가 그 키를
 * 들고 대신 부르고, 결과만 돌려준다.
 *
 * <h2>🔴 인가는 "로그인한 사람이면 된다" 다</h2>
 * 이 자리에 남의 것/내 것 구분이 없다 — 문장은 부르는 쪽이 준 값이고 우리 자원이 아니다.
 * 그런데도 로그인을 요구하는 이유는 {@code RouteController} 와 같다 — 우리 업체 키로 남이
 * 대신 번역을 돌리는 것(비용)을 막기 위해서다.
 *
 * <p>{@code @Profile({"db","dev"})} 는 {@code place} 패키지와 같다 — 이 컨트롤러가 요구하는
 * {@link TranslationService}(그리고 그 아래 {@code TranslationVendorPort})가 이 두 프로필
 * 에서만 뜨므로, 컨트롤러도 같이 묶지 않으면 프로필 없는 기본 컨텍스트 테스트
 * ({@code GabolleBackendApplicationTests})가 빈을 못 찾아 죽는다.
 */
@RestController
@RequestMapping("/api/v1/tools")
@Profile({ "db", "dev" })
public class TranslateController {

	private final TranslationService translationService;

	public TranslateController(TranslationService translationService) {
		this.translationService = translationService;
	}

	/**
	 * @throws IllegalArgumentException 문장이 비었거나 너무 길거나, 방향을 모른다 — 400
	 * @throws com.gabolle.backend.tools.application.TranslationVendorException 업체 호출 실패 — 502
	 */
	@PostMapping("/translate")
	public ApiResponse<TranslateResponseDto> translate(@RequestBody TranslateRequestDto request,
			Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		AuthenticatedUsers.requireId(authentication);

		TranslationRequest domainRequest = new TranslationRequest(request.sourceText(),
				parseDirection(request.direction()));
		TranslationResult result = this.translationService.translate(domainRequest);

		return ApiResponse.success(TranslateResponseDto.from(result), resolveRequestId(requestId));
	}

	private TranslationDirection parseDirection(String raw) {
		try {
			return TranslationDirection.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException | NullPointerException exception) {
			throw new IllegalArgumentException("direction 은 KO_TO_EN · EN_TO_KO 중 하나여야 합니다: " + raw);
		}
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? "req_" + UUID.randomUUID() : requestId;
	}
}
