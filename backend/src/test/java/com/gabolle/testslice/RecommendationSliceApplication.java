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
		"com.gabolle.backend.recommendation",
		// S15P21E201-808 — 추천 엔진이 기대는 곳만 더한다. 엔진에서 @ConditionalOnBean 을
		// 걷어냈으므로 이제 배선은 이 목록이 정한다.
		//
		// trip 은 패키지 전체가 아니라 infra 만 올린다. 엔진이 필요로 하는 것은 여행 저장소
		// 둘(TripRepository · TripSeedPlaceRepository)뿐인데, trip 전체를 올리면
		// TripQueryService 가 서고 그것을 조건으로 삼는 RecommendationJobRunner 와 그 뒤의
		// 결과 조회·컨트롤러가 줄줄이 켜져서 itinerary 까지 따라온다. 이 슬라이스의 뜻은
		// "추천만 올린다" 이므로 거기서 멈춘다.
		"com.gabolle.backend.place",
		"com.gabolle.backend.trip.infra",
		// S15P21E201-106 — 테마 설정과 ThemeWeightResolver 가 여기 있다. 추천이 테마별
		// 가중치를 먹이려면 둘 다 필요하다.
		//
		// 🔴 이 줄이 처음 들어온 경위 (2026-09-15). ThemeWeightResolver 가 recommendation
		// .config 에 있던 동안, 생성자가 요구하는 CourseThemeProperties 가 목록 밖이라
		// 이 슬라이스가 컨텍스트부터 죽었다. 그때는 이 줄이 유일한 고침이었는데, 같은
		// 이유로 다른 슬라이스 넷도 함께 죽고 있었다(통합 테스트 131건). 그래서
		// resolver 를 설정 옆(coursetheme)으로 옮겨 원인을 없앴고, 이 줄은 남긴다 —
		// 이제는 "이 슬라이스가 테마를 쓴다" 는 뜻이지 빈을 주워 담는 땜질이 아니다.
		"com.gabolle.backend.coursetheme"
})
@EntityScan(basePackages = {
		"com.gabolle.backend.event.domain",
		"com.gabolle.backend.recommendation.domain",
		"com.gabolle.backend.place.domain",
		"com.gabolle.backend.trip.infra",
		// 🔴 2026-09-11 (S15P21E201-549) — user 를 더했다. EventIngestService 가 행동 이벤트를
		//    적기 전에 그 사람이 행동 개인화를 켜 뒀는지 보게 되면서 AppUserRepository 를 필수로
		//    요구한다. 매핑과 저장소가 없으면 이 슬라이스의 컨텍스트가 안 뜨고 검사 수십 개가
		//    한꺼번에 빨개진다 — CI 가 그렇게 잡았다.
		"com.gabolle.backend.user.domain"
})
@EnableJpaRepositories(basePackages = {
		"com.gabolle.backend.event.repository",
		"com.gabolle.backend.recommendation.repository",
		"com.gabolle.backend.place.repository",
		"com.gabolle.backend.trip.infra",
		"com.gabolle.backend.user.repository"
})
public class RecommendationSliceApplication {
}
