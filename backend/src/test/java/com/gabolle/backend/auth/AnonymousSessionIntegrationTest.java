package com.gabolle.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.gabolle.backend.auth.service.AnonymousSessionService;
import com.gabolle.backend.auth.service.AnonymousSessionService.IssuedAnonymousSession;
import com.gabolle.backend.auth.support.AuthPostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 진짜 PostgreSQL 위에서 익명 출입증을 잰다.
 *
 * <p>요점은 표에 원본이 없다는 것이다. 가짜 저장소로는 표 자체가 없어 항상 통과한 것처럼
 * 보이므로, {@code anonymous_session} 의 행을 SQL 로 직접 읽어 해시만 있는지 확인한다.
 */
class AnonymousSessionIntegrationTest extends AuthPostgresIntegrationTest {

	@Autowired
	private AnonymousSessionService anonymousSessionService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("두 번 발급하면 서로 다른 출입증이 나온다")
	void issuingTwiceProducesDifferentTokens() {
		IssuedAnonymousSession first = anonymousSessionService.issue();
		IssuedAnonymousSession second = anonymousSessionService.issue();

		assertThat(first.token()).isNotEqualTo(second.token());
		assertThat(first.sessionId()).isNotEqualTo(second.sessionId());
	}

	@Test
	@DisplayName("표에는 원본 출입증이 없다 — 해시만 저장된다")
	void databaseNeverStoresTheRawToken() {
		IssuedAnonymousSession issued = anonymousSessionService.issue();

		List<String> storedHashes = jdbcTemplate.queryForList(
				"SELECT token_hash FROM anonymous_session WHERE session_id = ?", String.class, issued.sessionId());

		assertThat(storedHashes).hasSize(1);
		assertThat(storedHashes.get(0)).isNotEqualTo(issued.token());
		assertThat(storedHashes.get(0)).hasSize(64); // SHA-256 hex
		// 표 전체를 훑어도 원본 문자열이 어디에도 없다
		List<String> allHashes = jdbcTemplate.queryForList("SELECT token_hash FROM anonymous_session", String.class);
		assertThat(allHashes).doesNotContain(issued.token());
	}

	@Test
	@DisplayName("발급받은 출입증으로 그 세션의 주인을 다시 찾는다")
	void issuedTokenResolvesBackToItsOwnSession() {
		IssuedAnonymousSession issued = anonymousSessionService.issue();

		assertThat(anonymousSessionService.resolve(issued.token()))
				.hasValueSatisfying(session -> assertThat(session.getSessionId()).isEqualTo(issued.sessionId()));
	}

	@Test
	@DisplayName("존재하지 않는 출입증은 어떤 세션에도 연결되지 않는다")
	void unknownTokenResolvesToNoSession() {
		anonymousSessionService.issue(); // 표가 비어 있지 않은 상태에서도 확인한다

		assertThat(anonymousSessionService.resolve("token-that-was-never-issued-by-anyone")).isEmpty();
	}
}
