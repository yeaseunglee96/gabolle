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
 * {@code /internal/**} 을 공유 토큰 하나로 연다 — MLOps Phase 1.
 *
 * <p>토큰이 맞으면 {@code ROLE_INTERNAL} 을 심는다. {@code SecurityConfig} 의
 * {@code requestMatchers("/internal/**").hasRole("INTERNAL")} 이 그것을 요구한다.
 *
 * <p>🔴 <b>인가를 여기서 하지 않는다.</b> 여기서 하는 일은 "이 요청이 우리 배치인가" 를
 * 판정해 권한을 <b>심는 것</b>뿐이고, 막는 것은 {@code SecurityConfig} 다. 이 저장소가
 * {@code @PreAuthorize} 를 안 쓰는 이유와 같다 — 인가가 두 곳에 있으면 한 곳만 고쳐도
 * 고쳤다고 믿게 된다 ({@code AdminModerationController} 의 같은 설명).
 *
 * <p>🔴 비교는 {@link MessageDigest#isEqual} 로 한다. {@code String.equals} 는 다른
 * 글자가 나오는 순간 멈추므로, 맞는 글자 수에 따라 <b>걸리는 시간이 달라진다.</b> 그 시간
 * 차이로 토큰을 한 글자씩 알아낼 수 있다(타이밍 공격). 길이를 먼저 재는 것도 같은 이유로
 * 안 한다.
 */
public class InternalTokenAuthenticationFilter extends OncePerRequestFilter {

	/** 배치가 토큰을 싣는 헤더. */
	public static final String HEADER = "X-Internal-Token";

	private final InternalApiProperties properties;

	public InternalTokenAuthenticationFilter(InternalApiProperties properties) {
		this.properties = properties;
	}

	/**
	 * 🔴 오류 디스패치에서도 돈다. 기본값은 <b>안 도는 것</b>이라 반드시 뒤집어야 한다.
	 *
	 * <p>{@code OncePerRequestFilter} 는 이름 그대로 <b>요청 하나에 한 번만</b> 돈다.
	 * 그런데 Spring 이 400 을 정한 뒤 {@code /error} 로 다시 디스패치하는 것은 <b>같은
	 * 요청</b>이고, 기본 구현({@code shouldNotFilterErrorDispatch()} 이 {@code true})은
	 * 그 두 번째 차례를 건너뛴다.
	 *
	 * <p>건너뛰면 그 디스패치에는 권한이 없다. Security 는 비어 있는 컨텍스트를 보고
	 * {@code anyRequest().authenticated()} 로 거부하므로, <b>400 이 401 로 바뀌어 나간다.</b>
	 * 그 응답이 나쁜 이유는 <b>거짓말을 하기 때문</b>이다 — 배치가 잘못된 요청을 보냈는데
	 * 응답은 "인증 실패" 라고 한다. 그러면 사람이 토큰과 방화벽을 먼저 몇 시간 뒤진다.
	 * 실제로 이 필터를 만들면서 그 길을 한 번 갔다.
	 *
	 * <h2>🔴 같은 이유로 경로로도 걸러내지 않는다</h2>
	 *
	 * 처음에는 {@code shouldNotFilter} 로 {@code /internal/} 밖을 건너뛰었다. 깔끔해
	 * 보였지만 위와 <b>똑같은 함정</b>이다 — 두 번째 디스패치의 URI 는 {@code /error} 라서
	 * 그 조건에도 걸린다. 그래서 지금은 <b>모든 요청에서 헤더만 본다.</b> 비용은 헤더 하나
	 * 읽는 것이고, 우리 배치 말고는 아무도 이 헤더를 안 보낸다.
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
			// 🔴 토큰을 설정 안 했으면 권한을 심지 않는다 → SecurityConfig 가 거부한다.
			//    "설정을 안 했으니 통과" 로 만들면 그 사고가 조용해진다.
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
		return MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8),
				this.properties.getToken().getBytes(StandardCharsets.UTF_8));
	}
}
