package com.gabolle.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;

/**
 * 실제 배포가 쓰는 프로필(dev)로 <b>전체</b> 애플리케이션이 뜨는지 본다 — S15P21E201-161
 * 배포 실패(2026-09-08) 후속.
 *
 * <h2>🔴 이 테스트가 메우는 구멍 — 왜 CI 는 초록인데 배포는 죽었나</h2>
 *
 * {@code RouteOptimizerAvailabilityCheck}(S15P21E201-161)가
 * {@code com.fasterxml.jackson.databind.ObjectMapper}(Jackson 2, 옛 패키지)를 import 했다.
 * 컴파일은 통과했지만 — 이 저장소는 Jackson 3({@code tools.jackson})이라 Spring 은
 * 그 타입의 빈을 등록하지 않는다. 배포({@code SPRING_PROFILES_ACTIVE=dev})에서만
 * "No qualifying bean of type ObjectMapper" 로 죽었다.
 *
 * <p>CI({@code backend:build})가 이걸 못 잡은 이유를 찾다가 이 저장소의 구조적 구멍을
 * 발견했다 — {@link GabolleBackendApplicationTests}(전체 앱)는 {@code no-db} 프로필로 돌아
 * {@code @Profile({"db","dev"})}(이 저장소에 123개 클래스가 이 조건을 쓴다)인
 * 빈을 애초에 만들지 않고, DB 가 필요한 통합 테스트(예: {@code AccountDeletionIntegrationTest})는
 * 전부 <b>슬라이스 앱</b>({@code AuthSliceApplication} 등 — 도메인 하나만 골라 띄우는 작은
 * 앱)만 쓴다. <b>전체 앱을 진짜 DB 로 띄우는 테스트가 이 저장소에 하나도 없었다</b> —
 * S15P21E201-546(스키마 버그가 187개 테스트를 전부 초록으로 통과시킴)과 뿌리가 같은 종류의
 * 간극이다.
 *
 * <p>여기서는 딱 하나만 확인한다 — {@code dev} 프로필의 컨텍스트가 <b>뜨는가</b>. 기능이
 * 맞는지는 슬라이스 통합 테스트들의 몫이다. 이 테스트가 잡는 것은 "각자는 맞는데 합치면
 * 안 뜬다" 는 배선 문제뿐이다 — 정확히 오늘 겪은 그 종류다.
 *
 * <p>🔴 {@code gabolle.auth.jwt-secret} 처럼 기본값이 없는 값은 여기서 직접 채운다 — 실제
 * {@code GABOLLE_JWT_SECRET} 환경변수가 있어야 하는 게 아니라, {@code spring.datasource.*}
 * 처럼 이 값이 채워진다는 사실만 필요하다({@link TestDatabase} 와 같은 판단).
 */
@SpringBootTest(properties = {
		"spring.profiles.active=dev",
		"spring.mail.host=127.0.0.1",
		"gabolle.auth.jwt-secret=0123456789abcdef0123456789abcdef"
})
@ExtendWith(PostgresAvailableCondition.class)
class DevProfileApplicationContextTest {

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Test
	void contextLoads() {
	}
}
