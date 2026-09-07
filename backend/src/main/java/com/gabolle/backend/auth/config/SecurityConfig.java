package com.gabolle.backend.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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
	private final AuthProperties properties;
	private final ApiAuthenticationEntryPoint authenticationEntryPoint;

	@Autowired
	public SecurityConfig(ObjectProvider<HmacJwtAuthenticationFilter> jwtFilter, AuthProperties properties,
			ApiAuthenticationEntryPoint authenticationEntryPoint) {
		this.jwtFilter = jwtFilter;
		this.properties = properties;
		this.authenticationEntryPoint = authenticationEntryPoint;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.cors(Customizer.withDefaults())
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
						"/api/v1/auth/web/refresh",
						"/api/v1/auth/web/logout",
						// 🔴 이 한 줄이 /oauth/ 아래 한 마디짜리 경로를 <b>전부</b> 연다 — {provider} 뿐 아니라
						//    S15P21E201-689·-690 이 더한 /oauth/signup·/oauth/link 도 여기 걸린다(그 둘은 아직
						//    로그인 상태가 아니라서 열려야 맞다. 잠금은 10분짜리 1회용 티켓과, 연결 쪽은 기존
						//    계정의 비밀번호다). 그러니 /oauth/ 아래에 인증이 필요한 경로를 새로 만들 때는
						//    /oauth/{provider}/link 처럼 <b>두 마디</b>로 두어야 한다 — 한 마디로 두면 조용히 열린다.
						"/api/v1/auth/oauth/*",
						"/api/v1/auth/oauth/*/challenge")
				.permitAll()
				.anyRequest().authenticated());
		jwtFilter.ifAvailable(filter -> http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class));

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
