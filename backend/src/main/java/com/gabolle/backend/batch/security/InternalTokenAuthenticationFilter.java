package com.gabolle.backend.batch.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.gabolle.backend.batch.config.InternalApiProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code /internal/**} 을 공유 토큰으로 연다(하나 또는 회전용 복수). 토큰 중 하나라도 맞으면
 * {@code ROLE_INTERNAL} 을 심고, 막는 것은 {@code SecurityConfig} 다 — 인가가 두 곳에 있으면
 * 한 곳만 고쳐도 고쳤다고 믿게 된다.
 *
 * <p>비교는 매 후보마다 {@link MessageDigest#isEqual} 로 한다. {@code String.equals} 는 맞는
 * 글자 수에 따라 걸리는 시간이 달라져 토큰을 한 글자씩 알아낼 수 있다. 길이를 먼저 재지 않는
 * 것도 같은 이유다.
 */
public class InternalTokenAuthenticationFilter extends OncePerRequestFilter {

	/** 배치가 토큰을 싣는 헤더. */
	public static final String HEADER = "X-Internal-Token";

	private final InternalApiProperties properties;

	public InternalTokenAuthenticationFilter(InternalApiProperties properties) {
		this.properties = properties;
	}

	/**
	 * 오류 디스패치에서도 돌아야 한다. 기본값은 안 도는 것이라 반드시 뒤집는다.
	 *
	 * <p>Spring 이 400 을 정한 뒤 {@code /error} 로 다시 디스패치하는 것은 같은 요청인데,
	 * 그 차례를 건너뛰면 컨텍스트가 비어 Security 가 거부하고 400 이 401 로 바뀌어 나간다.
	 * 잘못된 요청을 보낸 쪽이 "인증 실패" 를 보고 토큰과 방화벽을 먼저 뒤지게 된다.
	 *
	 * <p>같은 이유로 {@code shouldNotFilter} 로 경로를 걸러내지도 않는다 — 두 번째 디스패치의
	 * URI 는 {@code /error} 라 {@code /internal/} 조건에 안 걸린다. 모든 요청에서 헤더만 본다.
	 */
	@Override
	protected boolean shouldNotFilterErrorDispatch() {
		return false;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {

		if (SecurityContextHolder.getContext().getAuthentication() == null && this.properties.isConfigured()
				&& matches(request.getHeader(HEADER))) {
			// 토큰을 설정 안 했으면 권한을 심지 않아 SecurityConfig 가 거부한다. "설정을 안
			// 했으니 통과" 로 만들면 그 사고가 조용해진다.
			SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken("internal-batch", null,
						List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"))));
		}
		chain.doFilter(request, response);
	}

	private boolean matches(String presented) {
		if (presented == null) {
			return false;
		}
		byte[] presentedBytes = presented.getBytes(StandardCharsets.UTF_8);
		for (String candidate : this.properties.getTokens()) {
			if (MessageDigest.isEqual(presentedBytes, candidate.getBytes(StandardCharsets.UTF_8))) {
				return true;
			}
		}
		return false;
	}
}
