package com.gabolle.backend.batch.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.gabolle.backend.batch.config.InternalApiProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기계용 문이 언제 열리고 언제 안 열리는가.
 *
 * <p>이 필터는 권한을 심기만 한다. 막는 것은 {@code SecurityConfig} 의
 * {@code requestMatchers("/internal/**").hasRole("INTERNAL")} 이다.
 *
 * <p>DB 도 스프링 컨텍스트도 필요 없다 — 도커가 없는 PC 에서도 이 검사는 돈다.
 */
class InternalTokenAuthenticationFilterTest {

	private static final String TOKEN = "0123456789abcdef0123456789abcdef";

	@AfterEach
	void clearContext() {
		// SecurityContextHolder 는 스레드에 붙어 있다. 안 지우면 앞 테스트가 심은 권한이
		// 다음 테스트로 새어 나간다.
		SecurityContextHolder.clearContext();
	}

	private static InternalTokenAuthenticationFilter filterWithToken(String configured) {
		InternalApiProperties properties = new InternalApiProperties();
		properties.setToken(configured);
		return new InternalTokenAuthenticationFilter(properties);
	}

	private static Authentication runWith(InternalTokenAuthenticationFilter filter, String presentedHeader)
			throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/batch/taste-vectors/stale");
		if (presentedHeader != null) {
			request.addHeader(InternalTokenAuthenticationFilter.HEADER, presentedHeader);
		}
		filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
		return SecurityContextHolder.getContext().getAuthentication();
	}

	@Test
	@DisplayName("토큰이 맞으면 ROLE_INTERNAL 을 심는다")
	void matchingTokenGrantsInternalRole() throws Exception {
		Authentication authentication = runWith(filterWithToken(TOKEN), TOKEN);

		assertThat(authentication).isNotNull();
		assertThat(authentication.getAuthorities()).extracting(Object::toString).contains("ROLE_INTERNAL");
	}

	@Test
	@DisplayName("토큰이 다르면 아무 권한도 안 심는다")
	void wrongTokenGrantsNothing() throws Exception {
		assertThat(runWith(filterWithToken(TOKEN), "wrong-token")).isNull();
	}

	@Test
	@DisplayName("헤더가 없으면 아무 권한도 안 심는다")
	void missingHeaderGrantsNothing() throws Exception {
		assertThat(runWith(filterWithToken(TOKEN), null)).isNull();
	}

	@Test
	@DisplayName("🔴 서버에 토큰을 설정 안 했으면 문이 잠긴다 — 빈 헤더로도 안 열린다")
	void unconfiguredTokenKeepsTheDoorShut() throws Exception {
		// "설정을 깜빡했으니 통과" 로 만들면 그 사고는 아무 소리도 내지 않는다.
		// 빈 문자열끼리 같다고 판정해 열리는 것도 막는다.
		assertThat(runWith(filterWithToken(""), "")).isNull();
		assertThat(runWith(filterWithToken(""), null)).isNull();
		assertThat(runWith(filterWithToken("   "), "")).isNull();
	}

	@Test
	@DisplayName("🔴 이미 인증된 요청은 기계 신원으로 덮어쓰지 않는다 — 감사 기록이 틀어진다")
	void existingAuthenticationIsNeverOverwritten() throws Exception {
		Authentication human = new UsernamePasswordAuthenticationToken("person@example.com", null,
				List.of(new SimpleGrantedAuthority("ROLE_USER")));
		SecurityContextHolder.getContext().setAuthentication(human);

		// 사람의 요청이 마침 맞는 토큰을 달고 왔더라도 신원을 바꾸지 않는다.
		Authentication after = runWith(filterWithToken(TOKEN), TOKEN);

		assertThat(after).isSameAs(human);
		assertThat(after.getAuthorities()).extracting(Object::toString).doesNotContain("ROLE_INTERNAL");
	}

	@Test
	@DisplayName("설정값 앞뒤 공백은 무시한다 — 환경변수에 줄바꿈이 섞여도 문이 안 잠긴다")
	void configuredTokenIsTrimmed() throws Exception {
		Authentication authentication = runWith(filterWithToken("  " + TOKEN + "  "), TOKEN);

		assertThat(authentication).isNotNull();
	}
}
