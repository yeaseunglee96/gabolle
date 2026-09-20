package com.gabolle.backend.auth.config;

import com.gabolle.backend.auth.domain.AuthSession;
import com.gabolle.backend.auth.repository.AuthSessionRepository;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile({"db", "dev"})
public class HmacJwtAuthenticationFilter extends OncePerRequestFilter {

	private final AuthProperties properties;
	private final ObjectMapper objectMapper;
	private final AuthSessionRepository sessionRepository;
	private final AppUserRepository userRepository;

	public HmacJwtAuthenticationFilter(AuthProperties properties, ObjectMapper objectMapper,
			AuthSessionRepository sessionRepository, AppUserRepository userRepository) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.sessionRepository = sessionRepository;
		this.userRepository = userRepository;
	}

	/**
	 * 오류 디스패치에서도 돈다. 기본값은 안 도는 것이라 뒤집는다.
	 *
	 * <p>서버가 4xx 를 정한 뒤 {@code /error} 로 다시 디스패치하는 것은 같은 요청인데, 기본값대로
	 * 건너뛰면 그 차례에는 신원이 없어 {@code anyRequest().authenticated()} 가 거부한다 — 4xx 가
	 * 401 로 바뀌어 나가고, 앱은 401 을 세션 만료로 읽어 사용자를 로그아웃시킨다.
	 *
	 * <p>인증되지 않은 요청의 거동은 그대로다. 헤더가 없으면 아무 권한도 심지 않는다.
	 */
	@Override
	protected boolean shouldNotFilterErrorDispatch() {
		return false;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String authorization = request.getHeader("Authorization");
		if (authorization != null && authorization.startsWith("Bearer ")) {
			AppUser user = verify(authorization.substring(7));
			if (user != null) {
				// role 을 JWT 클레임이 아니라 매 요청 DB 에서 읽는다 — 발급된 토큰을 바꾸지 않고도
				// 권한 회수가 다음 요청부터 즉시 반영된다. ACTIVE 확인이 어차피 매 요청 조회다.
				List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
				UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
						user.getUserId().toString(), null, authorities);
				authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
				SecurityContextHolder.getContext().setAuthentication(authentication);
			}
		}
		filterChain.doFilter(request, response);
	}

	private AppUser verify(String token) {
		try {
			String[] parts = token.split("\\.", -1);
			if (parts.length != 3) {
				return null;
			}
			JsonNode header = objectMapper.readTree(decode(parts[0]));
			if (!"HS256".equals(header.path("alg").asText())
					|| !"JWT".equalsIgnoreCase(header.path("typ").asText())) {
				return null;
			}
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] expected = mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
			byte[] actual = Base64.getUrlDecoder().decode(parts[2]);
			if (!java.security.MessageDigest.isEqual(expected, actual)) {
				return null;
			}
			JsonNode payload = objectMapper.readTree(decode(parts[1]));
			long expiresAt = payload.path("exp").asLong(0);
			long issuedAt = payload.path("iat").asLong(0);
			String subject = payload.path("sub").asText(null);
			String sessionId = payload.path("sid").asText(null);
			Instant now = Instant.now();
			if (expiresAt <= now.getEpochSecond() || issuedAt <= 0 || issuedAt > now.plusSeconds(60).getEpochSecond()
					|| subject == null || subject.isBlank() || sessionId == null || sessionId.isBlank()) {
				return null;
			}
			UUID userId = UUID.fromString(subject);
			UUID parsedSessionId = UUID.fromString(sessionId);
			AppUser user = userRepository.findById(userId).filter(u -> u.getStatus() == UserStatus.ACTIVE).orElse(null);
			if (user == null) {
				return null;
			}
			AuthSession session = sessionRepository.findBySessionIdAndUserUserId(parsedSessionId, userId).orElse(null);
			return session != null && session.isUsableAt(now) ? user : null;
		} catch (Exception exception) {
			return null;
		}
	}

	private String decode(String encoded) {
		return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
	}
}
