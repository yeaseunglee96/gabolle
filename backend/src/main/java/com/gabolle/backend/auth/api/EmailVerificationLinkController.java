package com.gabolle.backend.auth.api;

import com.gabolle.backend.auth.config.AuthProperties;
import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.auth.service.LocalAuthService;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 메일 안의 링크를 사람이 브라우저에서 직접 누르는 입구다.
 *
 * <p>{@code POST /api/v1/auth/email-verification/confirm} 은 클라이언트가 JSON 으로 부르는
 * 계약이라 그대로 두고, 이 클래스는 링크 클릭만 담당한다. 메일 클라이언트는 GET 밖에 못
 * 보내므로 둘 중 하나로는 메일 인증을 끝낼 수 없다.
 *
 * <p>이 엔드포인트를 {@link AuthController} 에 넣지 않은 이유는 응답 모양이 다르기
 * 때문이다. 거기는 전부 {@code ApiResponse} 봉투를 돌려주는데 여기는 302 를 준다.
 *
 * <p>{@code gabolle.auth.email-verification-base-url} 을 이 경로로 맞춰 두어야 메일에
 * 들어가는 링크가 여기로 온다. 링크는 {@code LocalAuthService} 가
 * {@code base-url + "?token=" + rawToken} 으로 조립한다.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Profile({"db", "dev"})
public class EmailVerificationLinkController {

	private final LocalAuthService localAuthService;
	private final AuthProperties properties;

	public EmailVerificationLinkController(LocalAuthService localAuthService, AuthProperties properties) {
		this.localAuthService = localAuthService;
		this.properties = properties;
	}

	/**
	 * {@code token} 을 필수로 걸지 않는다.
	 *
	 * <p>필수로 걸면 파라미터가 없을 때 Spring 이
	 * {@code MissingServletRequestParameterException} 을 던지고, 그것이
	 * {@link AuthExceptionHandler} 의 {@code @ExceptionHandler(Exception.class)}
	 * catch-all 에 걸려 500 이 된다. 2026-09-04 배포에서 실측했다.
	 *
	 * <p>여기 오는 사람은 메일 링크를 누른 사용자이므로 500 이나 JSON 오류를 보여 줄
	 * 자리가 아니다. 토큰이 없는 것은 링크가 잘린 것이고, 잘린 링크는 틀린 토큰과
	 * 결과가 같아야 한다 — 둘 다 실패 화면으로 보낸다.
	 */
	@GetMapping("/email-verification")
	public ResponseEntity<Void> verifyByLink(
			@RequestParam(name = "token", required = false) String token) {
		String location;
		try {
			if (token == null || token.isBlank()) {
				throw new AuthException("MISSING_VERIFICATION_TOKEN", "인증 토큰이 없습니다.",
						HttpStatus.BAD_REQUEST);
			}
			localAuthService.verifyEmail(token);
			location = properties.getEmailVerificationSuccessRedirectUrl();
		}
		catch (AuthException ex) {
			// 실패해도 토큰은 리다이렉트 주소에 싣지 않는다. 브라우저 이력과 Referer 로 새어
			// 나가고, 아직 안 쓴 토큰이면 그것으로 남의 계정 인증을 끝낼 수 있다.
			location = appendError(properties.getEmailVerificationFailureRedirectUrl(), ex.getCode());
		}
		return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
	}

	private String appendError(String baseUrl, String code) {
		String separator = baseUrl.contains("?") ? "&" : "?";
		return baseUrl + separator + "error=" + URLEncoder.encode(code, StandardCharsets.UTF_8);
	}
}
