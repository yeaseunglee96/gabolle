package com.gabolle.backend.functional;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.gabolle.backend.functional.support.AuthedClient;
import com.gabolle.backend.functional.support.FunctionalJourneyTest;

/**
 * 부슐랭(컬렉션) API 는 꺼져 있다(S15P21E201-1588) — 설정({@code gabolle.collections.enabled})이 없으면 문이 없다.
 *
 * <p>앱 전체를 띄운 공용 여정 컨텍스트를 그대로 쓴다. 설정을 바꾸지 않은 이 상태가 곧 운영과 같은 「설정 없음」이다.
 * 컬렉션 규칙 자체는 {@code CollectionControllerTest} 가 컨트롤러를 직접 만들어 계속 잰다 — 스위치와 무관하다.
 */
class CollectionsOffFunctionalTest extends FunctionalJourneyTest {

	@Test
	@DisplayName("🔴 설정이 없으면 /api/v1/me/collections 는 404 — 로그인해도 없는 경로다")
	void collectionsAreOffWithoutTheSetting() {
		AuthedClient authed = loginAsNewUser("collections-off");

		ResponseEntity<String> collections = authed.get("/api/v1/me/collections", String.class);
		// 같은 사람의 다른 경로(내 여행 목록)는 열려 있다 — 404 가 로그인 문제가 아니라 문이 없어서라는 대조군이다.
		ResponseEntity<String> trips = authed.get("/api/v1/trips", String.class);

		assertThat(collections.getStatusCode())
				.as("컬렉션 문이 열려 있다: %s", collections.getBody())
				.isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(trips.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
}
