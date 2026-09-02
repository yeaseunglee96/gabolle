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
				.requestMatchers(
						"/api/v1/auth/signup",
						"/api/v1/auth/email-verification/confirm",
						"/api/v1/auth/email-verification/resend",
						"/api/v1/auth/login",
						"/api/v1/auth/refresh",
						"/api/v1/auth/logout",
						"/api/v1/auth/password-reset/request",
						"/api/v1/auth/password-reset/confirm",
						"/api/v1/auth/web/refresh",
						"/api/v1/auth/web/logout",
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
