package com.gabolle.backend.auth.config;

import com.gabolle.backend.auth.domain.AnonymousSession;
import com.gabolle.backend.auth.service.AnonymousSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code X-Session-Token} 헤더로 익명 세션의 주인을 찾는다.
 *
 * <p>{@link HmacJwtAuthenticationFilter} 뒤에 두고 {@code SecurityContext} 가 비어 있을 때만
 * 채운다 — 그래야 실제 계정으로 로그인한 사람의 인증 정보를 익명 세션이 덮어쓰지 않는다.
 *
 * <p>principal 은 {@code "anon:" + sessionId} 문자열이다. 실제 계정의 principal(UUID 문자열)과
 * 겹치지 않도록 접두사를 붙였고, {@code UUID.fromString(authentication.getName())} 을 시도하는
 * 자리에서는 이 문자열이 예외가 되어 401 로 정리된다. 즉 이 필터는 "이 요청이 어느 익명 세션의
 * 것인가" 만 답하고, 로그인이 필요한 경로를 익명 세션에 열어 주지 않는다.
 */
@Component
@Profile({"db", "dev"})
public class AnonymousSessionAuthenticationFilter extends OncePerRequestFilter {

	public static final String HEADER_NAME = "X-Session-Token";
	/**
	 * {@code AuthenticatedUsers.requireOwner} 가 이 접두사로 "이 principal 이 익명 세션인가" 를
	 * 판정한다. 문자열을 양쪽에 따로 적으면 한쪽만 바뀐 날 조용히 어긋난다.
	 */
	public static final String ANONYMOUS_PRINCIPAL_PREFIX = "anon:";

	private final AnonymousSessionService anonymousSessionService;

	public AnonymousSessionAuthenticationFilter(AnonymousSessionService anonymousSessionService) {
		this.anonymousSessionService = anonymousSessionService;
	}

	/**
	 * 오류 디스패치에서도 돈다. 안 뒤집으면 익명 세션으로 여행을 만들다 요청 하나가 잘못됐을 때
	 * 4xx 가 401 로 바뀌고, 화면은 그것을 "세션이 끊겼다" 로 읽어 담아 둔 익명 여행을 잃는다.
	 */
	@Override
	protected boolean shouldNotFilterErrorDispatch() {
		return false;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			String token = request.getHeader(HEADER_NAME);
			if (token != null && !token.isBlank()) {
				anonymousSessionService.resolve(token).ifPresent(session -> authenticate(request, session));
			}
		}
		filterChain.doFilter(request, response);
	}

	private void authenticate(HttpServletRequest request, AnonymousSession session) {
		List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"));
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
				ANONYMOUS_PRINCIPAL_PREFIX + session.getSessionId(), null, authorities);
		authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}
}
