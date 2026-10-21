package com.gabolle.backend.metrics;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 인증이 끝난 요청마다 {@link ActivityDayRecorder} 를 부른다. 보안 필터 뒤(컨트롤러 직전)라 신원이 이미 정해져 있다.
 *
 * <p>로그인한 사람은 principal 이 사용자 번호(UUID)이고, 익명 출입증은 {@code anon:<세션번호>} 다. 그 밖의 신원(내부
 * 배치 등)은 사람이 아니므로 세지 않는다.
 */
@Component
@Profile({ "db", "dev" })
public class ActivityDayInterceptor implements HandlerInterceptor, WebMvcConfigurer {

	private static final String ANON_PREFIX = "anon:";

	private final ActivityDayRecorder recorder;

	public ActivityDayInterceptor(ActivityDayRecorder recorder) {
		this.recorder = recorder;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(this).addPathPatterns("/api/**");
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
			return true;
		}
		String name = auth.getName();
		if (name.startsWith(ANON_PREFIX)) {
			UUID id = parse(name.substring(ANON_PREFIX.length()));
			if (id != null) {
				this.recorder.anonymous(id);
			}
			return true;
		}
		boolean person = auth.getAuthorities().stream()
			.anyMatch(a -> "ROLE_USER".equals(a.getAuthority()) || "ROLE_ADMIN".equals(a.getAuthority()));
		UUID id = person ? parse(name) : null;
		if (id != null) {
			this.recorder.user(id);
		}
		return true;
	}

	private static UUID parse(String value) {
		try {
			return UUID.fromString(value);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}
}
