package com.gabolle.backend.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.gabolle.backend.batch.security.InternalTokenAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import jakarta.servlet.DispatcherType;

/**
 * 🔴 S15P21E201-1548 — {@code @EnableMethodSecurity} 를 켠다. 이전에는 이게 꺼져 있어
 * {@code AdminModerationController}·{@code AnalyticsController} 가 {@code @PreAuthorize} 를
 * 붙여도 조용히 무시된다고 스스로 경고하고 있었다 — 방어선이 아래 {@code requestMatchers}
 * 경로 매처 단 하나뿐이라, 누가 실수로 그 컨트롤러를 {@code /api/v1/admin/} 밖으로 옮기면
 * 그 즉시 인가가 사라졌다.
 *
 * <p>이제 두 컨트롤러에 {@code @PreAuthorize("hasRole('ADMIN')")} 를 추가로 붙여
 * **이중 방어**로 만든다 — 경로 매처는 대체된 것이 아니라 그대로 남아 있다. 코드베이스
 * 어디에도 기존 {@code @PreAuthorize}/{@code @Secured}/{@code @RolesAllowed} 사용이 없었으므로
 * (2026-09-24 확인), 이 스위치를 켜도 다른 곳의 숨은 동작이 갑자기 살아나지 않는다.
 */
@EnableMethodSecurity
@Configuration
public class SecurityConfig {

	private final ObjectProvider<HmacJwtAuthenticationFilter> jwtFilter;
	private final ObjectProvider<AnonymousSessionAuthenticationFilter> anonymousSessionFilter;

	/** 기계용 문. 사람용 JWT 필터와 별개로 돈다. */
	private final ObjectProvider<InternalTokenAuthenticationFilter> internalTokenFilter;

	private final AuthProperties properties;
	private final ApiAuthenticationEntryPoint authenticationEntryPoint;

	@Autowired
	public SecurityConfig(ObjectProvider<HmacJwtAuthenticationFilter> jwtFilter,
			ObjectProvider<AnonymousSessionAuthenticationFilter> anonymousSessionFilter,
			ObjectProvider<InternalTokenAuthenticationFilter> internalTokenFilter, AuthProperties properties,
			ApiAuthenticationEntryPoint authenticationEntryPoint) {
		this.jwtFilter = jwtFilter;
		this.anonymousSessionFilter = anonymousSessionFilter;
		this.internalTokenFilter = internalTokenFilter;
		this.properties = properties;
		this.authenticationEntryPoint = authenticationEntryPoint;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.cors(Customizer.withDefaults())
			// 이 앱은 JWT 전용이라 세션에 담을 것이 없다. STATELESS 가 아니면 아무도 안 쓰는
			// HttpSession 이 계속 만들어져 세션 고정 공격면만 늘어난다.
			// 웹의 리프레시 토큰 쿠키는 애플리케이션 코드가 직접 관리하는 별개의 쿠키라 무관하다.
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint))
			.authorizeHttpRequests(authorize -> authorize
				// 🔴 S15P21E201-1724 — 비동기 응답이 끝날 때의 재진입(ASYNC)만 인가 검사를 건너뛴다.
				// 진행률 스트림(SseEmitter)이 끝나면 서블릿 컨테이너가 같은 요청을 ASYNC 로 한 번 더
				// 들여보내는데, JWT 필터는 그 차례에 안 돌고 세션은 STATELESS 라 신원이 없다. 그래서
				// 아래 anyRequest().authenticated() 가 거부했고, 응답이 이미 나가는 중이라 401 도 못
				// 쓰고 예외가 컨테이너까지 올라가 ERROR 를 남기며 청크 응답의 끝 표시 없이 연결을
				// 끊었다(nginx 의 「upstream prematurely closed」).
				// 여는 것이 아니다 — ASYNC 재진입은 첫 차례(REQUEST)가 아래 규칙을 통과하고 처리기가
				// 비동기를 시작한 요청에만 생긴다. 바깥에서 ASYNC 로 들어오는 요청은 없다.
				// ERROR 재진입은 여기 넣지 않는다 — 그쪽은 필터가 다시 돌아 신원을 되살린다
				// (HmacJwtAuthenticationFilter#shouldNotFilterErrorDispatch).
				.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
				.requestMatchers("/actuator/health").permitAll()
				// 기록 사진은 주소를 아는 사람이 그대로 연다 — 화면이 <img> 로 부르므로 그 요청에는
				// Authorization 헤더가 안 붙는다. 키가 UUID 라 추측할 수 없고, 올리기는 인증이 필요하다.
				.requestMatchers(HttpMethod.GET, "/api/v1/uploads/images/**").permitAll()
				// 장소 사진 대리 조회(S15P21E201-1832)도 화면이 <img> 로 부른다 — 위와 같은 이유로 헤더가 없다.
				// 장소 번호가 UUID 이고, Google 장소 번호가 저장된 장소에만 답한다(PlacePhotoController).
				.requestMatchers(HttpMethod.GET, "/api/v1/places/*/photo").permitAll()
				// 공유 조회는 43글자 난수 토큰을 아는 사람이 로그인 없이 연다. 발급(POST)·복제는
				// 여전히 인증이 필요하다.
				.requestMatchers(HttpMethod.GET, "/api/v1/shares/*").permitAll()
				// 운영자 전용 경로. S15P21E201-1548 — 이제 메서드 보안도 켜져 있어 컨트롤러의
				// @PreAuthorize 가 이중으로 막지만, 이 경로 매처는 대체가 아니라 그대로 둔다 —
				// 운영자 API 를 새로 만드는 사람이 애너테이션을 잊어도 이 줄이 여전히 막는다.
				// 권한은 HmacJwtAuthenticationFilter 가 매 요청 DB 에서 role 을 읽어 심는다
				// (토큰 클레임이 아니라 DB 라서 권한 회수가 즉시 반영된다).
				.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
				// 기계가 부르는 배치 경로. 운영자 경로와 문이 다르다 — 배치 실행자를 ADMIN 목록에
				// 끼우면 사람 계정의 비밀번호를 기계가 들고 있어야 하고, 운영자 목록을 고치는
				// 사람이 자기가 배치를 멈춘다는 것을 모른 채 멈춘다.
				// 권한은 InternalTokenAuthenticationFilter 가 X-Internal-Token 헤더를 보고 심는다.
				// 토큰을 설정 안 하면 그 필터가 아무 권한도 안 심어서 이 줄이 전부 거부한다 —
				// "설정을 깜빡했더니 열려 있었다" 가 되지 않는다.
				.requestMatchers("/internal/**").hasRole("INTERNAL")
				// 아래 목록과 컨트롤러 매핑은 두 파일에 나뉘어 있지만 하나의 사실이다. 한쪽만 보고
				// 다른 쪽을 지우면 git 이 못 막으므로, SecurityAllowlistMatchesRoutesTest 가
				// 양쪽을 대조한다 — 공개여야 하는 경로가 목록에서 빠지면 빨개진다.
				.requestMatchers(
						"/api/v1/auth/signup",
						"/api/v1/auth/email-verification",
						"/api/v1/auth/email-verification/confirm",
						"/api/v1/auth/email-verification/resend",
						"/api/v1/auth/login",
						"/api/v1/auth/refresh",
						"/api/v1/auth/logout",
						"/api/v1/auth/password-reset/request",
						"/api/v1/auth/password-reset/confirm",
						// 웹(브라우저)은 리프레시 토큰을 본문이 아니라 쿠키로 주고받는다.
						// 그 요청도 로그인 전 상태에서 오므로 열려 있어야 한다
						"/api/v1/auth/web/refresh",
						"/api/v1/auth/web/logout",
						// 이 한 줄이 /oauth/ 아래 한 마디짜리 경로를 전부 연다 — {provider} 뿐 아니라
						// /oauth/signup·/oauth/link 도 걸린다(그 둘은 로그인 전이라 열려야 맞고, 잠금은
						// 10분짜리 1회용 티켓과 기존 계정의 비밀번호다). /oauth/ 아래에 인증이 필요한
						// 경로를 새로 만들 때는 두 마디로 두어야 한다 — 한 마디면 조용히 열린다.
						"/api/v1/auth/oauth/*",
						// 애플이 form_post 로 되돌려 보내는 자리. 로그인 전에 애플 서버가 직접
						// 부르므로 열려 있어야 한다. 위의 "/oauth/*" 는 한 마디만 받아 여기에 안 닿는다.
						// 와일드카드 대신 apple 을 박은 것은 아직 없는 경로까지 미리 열지 않기 위해서다.
						// 이 경로는 값을 옮기기만 하고 판정하지 않는다 — 실제 판정은 이어지는 코드
						// 교환이 챌린지에 묶인 state·nonce 로 한다.
						"/api/v1/auth/oauth/apple/form-post",
						// 소셜 로그인의 첫 요청이라 로그인 전이다. 막으면 브라우저가 열리기도 전에
						// 401 이 나고, 앱은 401 을 비밀번호 오류 문구로 바꿔 보여준다.
						"/api/v1/auth/oauth/*/challenge",
						// 가입 안 한 사람이 첫 출입증을 받는 자리. 이때는 아직 X-Session-Token 이 없다.
						"/api/v1/auth/anonymous")
				.permitAll()
				.anyRequest().authenticated());
		jwtFilter.ifAvailable(filter -> http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class));
		// JWT 필터 뒤에 둔다 — 이미 로그인한 사람의 SecurityContext 를 익명 세션이
		// 덮어쓰지 않게, AnonymousSessionAuthenticationFilter 는 비어 있을 때만 채운다.
		anonymousSessionFilter.ifAvailable(filter -> http.addFilterAfter(filter, HmacJwtAuthenticationFilter.class));
		// 내부 배치 토큰 필터는 모든 요청에서 돈다 — 경로로 거르지 않는다. Spring 은 검증 실패로
		// 400 을 정한 뒤 /error 로 다시 디스패치하는데 그 차례의 URI 는 /error 라서 걸러지고,
		// 권한이 안 심겨 400 이 401 로 바뀌어 나간다.
		// 이미 인증된 요청은 건드리지 않는다(판단은 필터 안) — 사람의 JWT 로 들어온 요청을
		// 기계 신원으로 덮어쓰면 감사 기록이 틀린다.
		internalTokenFilter
			.ifAvailable(filter -> http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class));

		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(properties.getCorsAllowedOrigins());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		// 프론트가 실제로 보내는 헤더가 전부 여기 있어야 한다. 브라우저의 사전 확인(preflight)이
		// 이 목록과 대조하므로, 빠진 헤더를 쓰는 요청은 상태 코드도 못 받고 fetch 자체가 실패한다.
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id", "X-Client-Platform",
				"X-Device-Id", "X-Session-Token", "Idempotency-Key"));
		configuration.setExposedHeaders(List.of("X-Request-Id"));
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		// 🔴 S15P21E201-1556 — 2026-09-23 App Store 심사에서 Apple 로그인이 Touch ID 이후
		// «항상» 403(Invalid CORS request)이었다. 실제 리뷰어 IP(17.64.127.x, 애플 자체
		// 대역)가 이 403을 맞은 것을 nginx 로그로 확인했고, curl 로 Origin 헤더 하나만
		// appleid.apple.com 으로 줘도 그대로 재현된다.
		//
		// 원인: 위 configuration 이 "/**" 전부에 걸리는데, 그 allowed-origins 는 우리
		// 프론트 주소들뿐이라 appleid.apple.com 이 없다. 그런데 이 경로(형 form-post)는
		// Apple 서버가 브라우저를 통해 «폼을 그대로 제출»하는 자리이지, 우리 JS 가
		// fetch/XHR 로 부르는 API 가 아니다 — CORS 는 스크립트가 교차 출처 응답을 읽는
		// 것을 막는 장치이지 폼이 어디로 제출되는지를 막는 장치가 아닌데, Spring 의 CORS
		// 필터는 Origin 헤더가 있으면(최신 Safari/Chrome 은 교차 출처 POST 내비게이션에도
		// 자동으로 붙인다) 경로를 안 가리고 판정한다.
		//
		// 그래서 이 경로만 origin 을 아예 안 가리는 별도 설정을 더 앞에 등록한다(더 구체적인
		// 패턴이 "/**" 보다 먼저 검사돼야 한다). 응답은 JS 가 안 읽고 브라우저가 그대로
		// 따라가는 302 라 자격 증명(쿠키)도 필요 없다 — allowCredentials 를 켜지 않아야
		// addAllowedOriginPattern("*") 을 같이 쓸 수 있다(CORS 스펙이 credentials=true 와
		// 와일드카드 출처의 동시 사용을 금지한다).
		CorsConfiguration formPostConfiguration = new CorsConfiguration();
		formPostConfiguration.addAllowedOriginPattern("*");
		formPostConfiguration.setAllowedMethods(List.of("POST"));
		formPostConfiguration.setAllowCredentials(false);
		source.registerCorsConfiguration("/api/v1/auth/oauth/*/form-post", formPostConfiguration);

		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
