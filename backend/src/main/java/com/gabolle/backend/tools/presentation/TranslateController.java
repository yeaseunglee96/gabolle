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
 * 문장 하나를 번역해 준다. 화면에서 업체를 직접 부르면 업체 키가 브라우저에 노출되므로 서버가 대신
 * 부른다.
 *
 * <p>인가는 "로그인한 사람이면 된다" 다. 문장은 부르는 쪽이 준 값이라 소유권 구분이 없고, 로그인을
 * 요구하는 것은 우리 업체 키로 남이 대신 번역을 돌리는 비용을 막기 위해서다.
 *
 * <p>{@code @Profile} 을 컨트롤러에도 걸어야 한다. {@link TranslationService} 가 이 두 프로필에서만
 * 뜨므로, 같이 묶지 않으면 프로필 없는 기본 컨텍스트 테스트가 빈을 못 찾아 죽는다.
 */
@RestController
@RequestMapping("/api/v1/tools")
@Profile({ "db", "dev" })
public class TranslateController {

	private final TranslationService translationService;

	public TranslateController(TranslationService translationService) {
		this.translationService = translationService;
	}

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
