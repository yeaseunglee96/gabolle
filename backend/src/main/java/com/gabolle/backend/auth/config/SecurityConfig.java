package com.gabolle.backend.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
				.requestMatchers("/actuator/health").permitAll()
				// 기록 사진은 주소를 아는 사람이 그대로 연다 — 화면이 <img> 로 부르므로 그 요청에는
				// Authorization 헤더가 안 붙는다. 키가 UUID 라 추측할 수 없고, 올리기는 인증이 필요하다.
				.requestMatchers(HttpMethod.GET, "/api/v1/uploads/images/**").permitAll()
				// 공유 조회는 43글자 난수 토큰을 아는 사람이 로그인 없이 연다. 발급(POST)·복제는
				// 여전히 인증이 필요하다.
				.requestMatchers(HttpMethod.GET, "/api/v1/shares/*").permitAll()
				// 운영자 전용 경로. @PreAuthorize 를 쓰지 않는다 — 이 저장소는 메서드 보안
				// (@EnableMethodSecurity)이 꺼져 있어 그 애너테이션이 조용히 무시된다. 경로 앞자리로
				// 막으면 운영자 API 를 새로 만드는 사람이 애너테이션을 잊어도 막힌다.
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
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
