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
	private final AuthProperties properties;
	private final ApiAuthenticationEntryPoint authenticationEntryPoint;

	@Autowired
	public SecurityConfig(ObjectProvider<HmacJwtAuthenticationFilter> jwtFilter,
			ObjectProvider<AnonymousSessionAuthenticationFilter> anonymousSessionFilter, AuthProperties properties,
			ApiAuthenticationEntryPoint authenticationEntryPoint) {
		this.jwtFilter = jwtFilter;
		this.anonymousSessionFilter = anonymousSessionFilter;
		this.properties = properties;
		this.authenticationEntryPoint = authenticationEntryPoint;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.cors(Customizer.withDefaults())
			// 🔴 S15P21E201-769 — ZAP 스캔이 /actuator/health 응답에서 JSESSIONID 쿠키를 잡았다
			//    (SameSite 속성도 없이). 이 앱은 JWT 전용이다 — HmacJwtAuthenticationFilter 가
			//    매 요청 DB 에서 role 을 다시 읽으므로 세션에 담아 둘 것이 애초에 없다. 그런데도
			//    Spring Security 기본값(세션 필요시 생성)이 살아 있어서 아무도 안 쓰는 세션이
			//    계속 만들어지고 있었다 — 세션 고정 공격면만 늘리는 상태다.
			//
			//    STATELESS 로 끄면 이 필터 체인이 HttpSession 을 절대 안 만들고 안 읽는다.
			//    웹의 리프레시 토큰 쿠키(GABOLLE_WEB_REFRESH_COOKIE_NAME)는 이것과 무관하다 —
			//    그건 애플리케이션 코드가 직접 Set-Cookie 로 관리하는 별개의 쿠키이지
			//    HttpSession 이 아니다.
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/actuator/health").permitAll()
				// S15P21E201-216 — 기록 사진은 주소를 아는 사람이 그대로 연다(피드·상세 화면이 <img> 로
				//    부른다. 그 요청에는 Authorization 헤더가 안 붙는다). 키가 UUID 라 추측할 수 없고,
				//    올리기(POST /api/v1/uploads/story-image)는 여전히 인증이 필요하다.
				.requestMatchers(HttpMethod.GET, "/api/v1/uploads/images/**").permitAll()
				// S15P21E201-330 — 공유 조회는 표(token)를 아는 사람이 로그인 없이 그대로 연다. 표가
				//    43글자 난수라 추측할 수 없고, 발급(POST)·복제는 여전히 인증이 필요하다.
				.requestMatchers(HttpMethod.GET, "/api/v1/shares/*").permitAll()
				// 🔴 S15P21E201-267 — 운영자 전용 경로. `-686` 이 만든 ADMIN Role 로 막는다.
				//
				//    @PreAuthorize 를 쓰지 않는 이유가 둘이다. 첫째, 이 저장소는 메서드 보안
				//    (@EnableMethodSecurity)이 **꺼져 있어서** 그 애너테이션이 <b>조용히 무시된다</b> —
				//    붙여 놓고 안 막히는 것이 가장 나쁜 상태다. 둘째, 경로 앞자리로 막으면 나중에
				//    운영자 API 를 하나 더 만들 때 그 사람이 애너테이션을 잊어도 막힌다.
				//
				//    권한은 HmacJwtAuthenticationFilter 가 매 요청 DB 에서 role 을 읽어
				//    ROLE_ADMIN 으로 심는다(토큰 클레임이 아니라 DB 라서 권한 회수가 즉시 반영된다).
				.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
				// 🔴 S15P21E201-704 — 2026-09-07 저녁에 이 줄 셋을 지웠다가 <b>같은 날 되살렸다.</b>
				//    그 사이 소셜 로그인이 앱에서 완전히 막혔다. 무슨 일이 있었는지 남긴다.
				//
				//    17:11 다른 작업이 AuthController 에서 실수로 지워졌던 매핑 셋을 되살렸다
				//         (/oauth/{provider}/challenge · /web/refresh · /web/logout, -704).
				//    17:17 인가 점검(-672)이 "이 목록에 있는데 그 경로를 매핑하는 컨트롤러가 없다"
				//         고 판단해 같은 셋을 이 목록에서 지웠다. 그 판단은 <b>그 시점의 브랜치에서는
				//         옳았다</b> — 갈라진 뒤에 매핑이 되살아난 것을 몰랐다.
				//    17:36 · 17:40 둘이 차례로 머지됐다. git 은 서로 다른 파일의 서로 다른 줄이라
				//         충돌 없이 합쳤고, 결과는 <b>경로는 있는데 인증을 요구하는 상태</b>다.
				//
				//    🔴 챌린지 발급은 소셜 로그인의 <b>첫 요청</b>이고 그때는 아직 로그인 전이다.
				//    그래서 인증을 요구하면 브라우저가 열리기도 전에 401 이 나고, 앱은 401 을 전부
				//    비밀번호 오류 문구로 바꿔 보여주므로 사용자에게는 "소셜 버튼을 눌렀는데 아이디·
				//    비밀번호가 틀렸다고 한다" 로 보인다.
				//
				//    교훈: 이 목록과 컨트롤러 매핑은 <b>두 파일에 나뉘어 있지만 하나의 사실</b>이다.
				//    한쪽만 보고 다른 쪽을 지우면 git 이 못 막는다. 그래서
				//    SecurityAllowlistMatchesRoutesTest 에 반대 방향 검사를 더했다 — 공개여야 하는
				//    경로가 목록에서 빠지면 빨개진다.
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
						// 🔴 이 한 줄이 /oauth/ 아래 한 마디짜리 경로를 <b>전부</b> 연다 — {provider} 뿐 아니라
						//    S15P21E201-689·-690 이 더한 /oauth/signup·/oauth/link 도 여기 걸린다(그 둘은 아직
						//    로그인 상태가 아니라서 열려야 맞다. 잠금은 10분짜리 1회용 티켓과, 연결 쪽은 기존
						//    계정의 비밀번호다). 그러니 /oauth/ 아래에 인증이 필요한 경로를 새로 만들 때는
						//    /oauth/{provider}/link 처럼 <b>두 마디</b>로 두어야 한다 — 한 마디로 두면 조용히 열린다.
						"/api/v1/auth/oauth/*",
						// 🔴 소셜 로그인의 첫 요청이다. 이때는 아직 로그인 전이므로 열려 있어야
						//    한다 — 막으면 브라우저가 열리기도 전에 401 이 난다 (-704)
						"/api/v1/auth/oauth/*/challenge",
						// S15P21E201-303 — 가입 안 한 사람이 첫 출입증을 받는 자리. 이때는 아직
						// X-Session-Token 이 없으므로 열려 있어야 한다.
						"/api/v1/auth/anonymous")
				.permitAll()
				.anyRequest().authenticated());
		jwtFilter.ifAvailable(filter -> http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class));
		// 🔴 JWT 필터 뒤에 둔다 — 이미 로그인한 사람의 SecurityContext 를 익명 세션이
		// 덮어쓰지 않게, AnonymousSessionAuthenticationFilter 는 비어 있을 때만 채운다.
		anonymousSessionFilter.ifAvailable(filter -> http.addFilterAfter(filter, HmacJwtAuthenticationFilter.class));

		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(properties.getCorsAllowedOrigins());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id", "X-Client-Platform",
				"X-Device-Id"));
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
