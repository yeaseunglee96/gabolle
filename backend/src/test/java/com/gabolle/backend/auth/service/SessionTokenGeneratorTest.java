package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SessionTokenGeneratorTest {

	private final SessionTokenGenerator generator = new SessionTokenGenerator();

	@Test
	void issuesRandomUrlSafeTokenAndStoresOnlyItsHash() {
		String first = generator.issue();
		String second = generator.issue();

		assertThat(first).isNotEqualTo(second);
		assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]+");
		assertThat(generator.hash(first)).hasSize(64).isNotEqualTo(first);
	}
}
