package com.gabolle.backend.auth.api;

import com.gabolle.backend.auth.config.AuthProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/auth/oauth/apple/form-post} — 애플이 POST 로 보낸 결과를 화면으로 넘긴다.
 * 애플은 이름이든 이메일이든 하나라도 요청하면 결과를 {@code response_mode=form_post} 로,
 * 즉 등록된 주소에 HTTP POST 로 보내겠다고 요구한다.
 *
 * <p>이 경로는 운반만 한다. 폼 본문의 {@code user} 값은 서명되어 있지 않아 애플이 보낸 것인지
 * 구분할 수 없으므로 신원으로 쓰면 남의 이메일을 자기 계정에 붙일 수 있다. 이메일은 그 뒤 코드
 * 교환에서 애플이 서명한 {@code id_token} 의 클레임으로 들어온다.
 *
 * <p>리다이렉트 주소를 요청에서 받지 않는다. 서명 없는 폼 전송이라 누구나 흉내낼 수 있고, 본문에
 * 실린 주소로 되돌려 보내면 이 서버가 open redirect 도구가 된다. 주소는
 * {@code gabolle.auth.apple-form-post-redirect-url} 하나다.
 *
 * <p>{@code state} 를 여기서 검증하지 않는다. 화면이 이어서 부르는 코드 교환이 챌린지에 묶인
 * {@code state}·{@code nonce}·{@code code_verifier} 를 함께 보고, 이 자리에는 그 판정에 필요한
 * 요청자의 세션이 없다.
 *
 * <p>{@link AuthController} 가 아니라 따로 둔 것은 그쪽이 전부 {@code ApiResponse} 봉투를
 * 돌려주는데 여기는 302 를 주기 때문이다.
 */
@RestController
@RequestMapping("/api/v1/auth/oauth/apple")
@Profile({ "db", "dev" })
public class AppleFormPostController {

	private final AuthProperties properties;

	public AppleFormPostController(AuthProperties properties) {
		this.properties = properties;
	}

	/**
	 * 애플이 보낸 폼을 받아 화면으로 넘긴다.
	 *
	 * <p>모든 칸을 {@code required = false} 로 둔다. 필수로 걸면 칸이 빠진 요청에서 Spring 이
	 * 예외를 던져 JSON 오류나 500 이 나가는데, 여기 도착하는 것은 브라우저라 화면에 보여 줄 수
	 * 없는 응답이다.
	 *
	 * <p>{@code user} 는 받지도 않는다. 서명 밖의 값이라 쓸 데가 없고, 받아서 넘기면 다음 사람이
	 * 쓸 수 있는 값으로 오해한다.
	 */
	// 경로는 value 자리에 둔다 — path= 로 쓰면 인가 정책 감사가 애너테이션의 value() 만 읽어
	// 실제 경로를 표와 대조하지 못한다.
	// consumes 를 걸지 않는다. 형식을 제한하면 그 형식이 아닌 요청에 맞는 핸들러가 없어
	// /error 로 넘어가고, 그 자리는 인증을 요구해서 401 이 나간다 — 로그인 전에 열려 있어야
	// 하는 경로가 막힌다. 형식이 틀리면 파라미터가 비고, 그 동작은 아래 빈 쿼리 가지가 정의한다.
	@PostMapping("/form-post")
	public ResponseEntity<Void> receive(
			@RequestParam(name = "code", required = false) String code,
			@RequestParam(name = "state", required = false) String state,
			@RequestParam(name = "error", required = false) String error) {

		List<String> query = new ArrayList<>(3);
		// 애플이 거절했으면 코드는 안 온다. 그 사실을 화면이 알아야 "취소했어요" 와 "실패했어요" 를
		// 가려 말할 수 있으므로 오류 코드를 함께 넘긴다.
		if (error != null && !error.isBlank()) {
			query.add("error=" + URLEncoder.encode(error, StandardCharsets.UTF_8));
		}
		if (code != null && !code.isBlank()) {
			query.add("code=" + URLEncoder.encode(code, StandardCharsets.UTF_8));
		}
		if (state != null && !state.isBlank()) {
			query.add("state=" + URLEncoder.encode(state, StandardCharsets.UTF_8));
		}
		// 셋 다 비어 있으면 오지 말았어야 하는 요청이다. 그래도 화면으로 보낸다 — 화면이 코드 없는
		// 착지를 이미 실패로 다루고, 여기서 오류 본문을 내면 브라우저에 JSON 이 그대로 보인다.
		if (query.isEmpty()) {
			query.add("error=" + URLEncoder.encode("APPLE_FORM_POST_EMPTY", StandardCharsets.UTF_8));
		}

		String base = this.properties.getAppleFormPostRedirectUrl();
		String separator = base.contains("?") ? "&" : "?";
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(URI.create(base + separator + String.join("&", query)))
				.build();
	}
}
