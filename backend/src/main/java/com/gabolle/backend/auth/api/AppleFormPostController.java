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
 * {@code POST /api/v1/auth/oauth/apple/form-post} — 애플이 POST 로 보낸 결과를 화면으로 넘긴다
 * (S15P21E201-833).
 *
 * <h2>왜 이 자리가 필요한가</h2>
 * 애플은 <b>이름이든 이메일이든 하나라도 요청하면</b> 로그인 결과를 쿼리가 아니라
 * {@code response_mode=form_post} 로, 즉 등록된 주소에 <b>HTTP POST</b> 로 보내겠다고 요구한다.
 * 안 보내면 인증 요청 자체를 거절한다 — {@code "response_mode must be form_post when name or
 * email scope is requested"} (2026-09-10 실측, S15P21E201-825).
 *
 * <p>그래서 그동안 이메일을 <b>아예 요청하지 않았다.</b> 되돌아오는 자리가 정적 화면이라 POST 본문을
 * 받을 수 없었기 때문이다. 그 결과 애플로 가입한 계정에는 이메일이 어디에도 없고, 기존 이메일·비밀번호
 * 계정이 있어도 "비밀번호 확인하고 연결" 제안이 뜨지 않아 같은 사람에게 계정이 하나 더 생겼다.
 *
 * <h2>이메일은 여기서 읽지 않는다</h2>
 * 🔴 이 경로는 <b>운반만</b> 한다. 폼 본문의 {@code user} 값(이름·이메일이 담겨 오는 칸)은
 * <b>서명되어 있지 않다</b> — 애플이 보낸 것인지 아무나 만들어 보낸 것인지 이 서버는 구분할 수 없다.
 * 그것을 신원으로 쓰면 남의 이메일을 자기 계정에 붙일 수 있다.
 *
 * <p>이메일은 그 뒤 코드 교환에서 애플이 <b>서명한</b> {@code id_token} 의 {@code email} 클레임으로
 * 들어온다. 그 경로는 이미 있다({@code AppleIdTokenVerifier} 가 {@code email}·{@code email_verified}
 * ·{@code is_private_email} 을 읽고 {@code AppleOAuthProviderClient} 가 실어 보낸다). 그래서 이 티켓은
 * 계정 판정 코드를 한 줄도 바꾸지 않는다 — <b>요청을 못 보내고 있던 것이 전부였다.</b>
 *
 * <p>같은 이유로 프런트는 애플에 {@code name} 을 요청하지 않는다. 이름만 서명 밖에서 오는 값이라
 * 신원에 쓸 수 없고, {@code email} 만 요청해도 POST 요구는 똑같이 생기므로 잃는 것이 없다.
 *
 * <h2>보낼 곳은 하나뿐이다</h2>
 * 🔴 리다이렉트 주소를 <b>요청에서 받지 않는다.</b> 애플의 POST 는 서명 없는 평범한 폼 전송이라
 * 누구나 흉내낼 수 있고, 본문에 실린 주소로 되돌려 보내면 이 서버가 임의의 주소로 사람을 보내 주는
 * 도구가 된다. 주소는 {@code gabolle.auth.apple-form-post-redirect-url} 하나이고, 이 클래스는
 * 거기에 {@code code}·{@code state}·{@code error} 만 붙인다.
 *
 * <p>{@link AuthController} 에 넣지 않은 이유는 {@link EmailVerificationLinkController} 와 같다 —
 * 그쪽은 전부 {@code ApiResponse} 봉투를 돌려주는데 여기는 302 를 준다.
 *
 * <h2>이 경로가 판정하지 않는 것</h2>
 * {@code state} 를 여기서 검증하지 않는다. 그 검증은 원래 자리에 그대로 있다 — 화면이 이어서 부르는
 * 코드 교환({@code POST /api/v1/auth/oauth/apple})이 챌린지에 묶인 {@code state}·{@code nonce}
 * ·{@code code_verifier} 를 함께 본다. 여기서 한 번 더 보면 같은 규칙이 두 곳에 생기고, 이 자리에는
 * 그 판정에 필요한 것(요청자의 세션)이 없다. 그래서 값을 <b>그대로 옮기고</b> 판정은 뒤에 맡긴다.
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
	 * 예외를 던지고 그것이 JSON 오류나 500 으로 나가는데, 여기 도착하는 것은 브라우저이므로
	 * 화면에 보여 줄 수 없는 응답이다({@link EmailVerificationLinkController} 가 같은 이유로
	 * 같은 선택을 했고, 그 자리는 2026-09-04 배포에서 실제로 500 이 났다).
	 *
	 * <p>{@code user} 는 받지도 않는다. 서명 밖의 값이라 쓸 데가 없고, 받아서 넘기면 다음 사람이
	 * 그것을 쓸 수 있는 값으로 오해한다.
	 */
	// 🔴 경로를 value 자리에 둔다 — {@code path = } 로 쓰면 안 된다. 인가 정책 감사
	//    (RouteAuthorizationRegistryTest.collect)가 애너테이션의 value() 만 읽어서,
	//    path= 로 두면 이 경로가 감사에 "/api/v1/auth/oauth/apple" 로 잡히고 실제 경로는
	//    표와 대조되지 않는다. 처음에 path= 로 썼다가 그 검사가 빨개져서 알았다.
	// 🔴 consumes 를 걸지 않는다. 처음에는 form-urlencoded 로 제한했는데, 그러면 그 형식이
	//    아닌 요청에 <b>맞는 핸들러가 없어</b> 오류 전달(/error)로 넘어가고 그 자리는 인증을
	//    요구해서 401 이 나간다. 인가 감사(RouteAuthorizationFunctionalTest)가 빈 JSON 으로
	//    찔러 보다가 그것을 잡았다 — "로그인 전에 열려 있어야 하는 경로가 막혀 있다".
	//    이 경로는 몸통을 해석하지 않고 요청 파라미터 셋만 읽어 고정된 주소로 넘기므로,
	//    형식을 가리지 않는 편이 더 튼튼하다. 형식이 틀리면 파라미터가 비고, 그때의 동작은
	//    아래 "셋 다 비어 있으면" 가지가 이미 정의하고 있다.
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
