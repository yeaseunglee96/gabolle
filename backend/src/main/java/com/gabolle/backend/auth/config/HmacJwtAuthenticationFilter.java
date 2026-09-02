package com.gabolle.backend.auth.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabolle.backend.auth.domain.AuthSession;
import com.gabolle.backend.auth.repository.AuthSessionRepository;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

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

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String authorization = request.getHeader("Authorization");
		if (authorization != null && authorization.startsWith("Bearer ")) {
			String subject = verify(authorization.substring(7));
			if (subject != null) {
				UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(subject, null,
						Collections.emptyList());
				authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
				SecurityContextHolder.getContext().setAuthentication(authentication);
			}
		}
		filterChain.doFilter(request, response);
	}

	private String verify(String token) {
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
			if (!userRepository.findById(userId).map(user -> user.getStatus() == UserStatus.ACTIVE).orElse(false)) {
				return null;
			}
			AuthSession session = sessionRepository.findBySessionIdAndUserUserId(parsedSessionId, userId).orElse(null);
			return session != null && session.isUsableAt(now) ? subject : null;
		} catch (Exception exception) {
			return null;
		}
	}

	private String decode(String encoded) {
		return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
	}
}
