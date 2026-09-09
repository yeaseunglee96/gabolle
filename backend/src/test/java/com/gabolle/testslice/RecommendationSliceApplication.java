package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 통합 테스트가 띄우는 애플리케이션 — <b>추천·이벤트·공통만</b> 올린다.
 *
 * <p>🔴 애플리케이션 전체({@code GabolleBackendApplication})를 띄우지 않는 이유가 있다.
 * 전체를 띄우면 <b>다른 도메인이 반쯤 만들어 둔 빈까지 전부 살아나야</b> 이 테스트가 돈다.
 * 실제로 그렇게 됐다 — 인증(S15P21E201-312)이 {@code db} 프로필에서 컨텍스트를 못 띄우는
 * 상태였고, 그 순간 추천 테스트 30여 개가 <b>추천과 아무 상관 없는 이유로</b> 빨개졌다.
 *
 * <p>테스트가 남의 미완성 코드에 인질로 잡히면, 빨간불이 무엇을 뜻하는지 아무도 모르게 된다.
 * 그래서 이 티켓이 책임지는 범위만 올린다. 애플리케이션 전체가 뜨는지는
 * {@code GabolleBackendApplicationTests} 가 따로 본다.
 *
 * <p>🔴 그래도 진짜 PostgreSQL 위에서 돈다 — 자동 설정도 Flyway 도 그대로다. 좁힌 것은
 * <b>스캔 범위뿐</b>이고, JSONB · UUID · 배열 · DB 제약 검증은 하나도 안 줄었다.
 *
 * <h2>왜 {@code com.gabolle.backend} 밖에 있나</h2>
 *
 * 테스트 소스도 테스트 실행 시 클래스패스에 올라간다. 그래서 이 클래스를
 * {@code com.gabolle.backend} 안에 두면 <b>본 애플리케이션의 컴포넌트 스캔에 걸려</b>
 * 아래 {@code @EnableJpaRepositories} 가 {@code no-db} 프로필에서도 켜지고,
 * EntityManagerFactory 가 없어 {@code contextLoads} 가 죽는다. 실제로 한 번 그렇게 깨졌다.
 *
 * <p>{@code @TestConfiguration} 으로 바꿔 스캔에서 빼는 방법도 시도했는데 그건 더 나빴다 —
 * Boot 는 {@code @SpringBootTest(classes=...)} 에 <b>테스트 컴포넌트만</b> 있으면
 * {@code @SpringBootConfiguration} 을 따로 찾아 <b>덧붙인다.</b> 결국 본 앱이 통째로 다시
 * 올라와 auth 가 그대로 딸려왔다.
 *
 * <p>그래서 <b>패키지를 옮기는 것</b>이 답이다. 스캔 대상이 아니고, 스스로
 * {@code @SpringBootConfiguration} 이라 Boot 가 다른 것을 찾지도 않는다.
 */
@SpringBootApplication(scanBasePackages = {
		"com.gabolle.backend.common",
		"com.gabolle.backend.event",
		"com.gabolle.backend.recommendation"
})
@EntityScan(basePackages = {
		"com.gabolle.backend.event.domain",
		"com.gabolle.backend.recommendation.domain"
})
@EnableJpaRepositories(basePackages = {
		"com.gabolle.backend.event.repository",
		"com.gabolle.backend.recommendation.repository"
})
public class RecommendationSliceApplication {
}
