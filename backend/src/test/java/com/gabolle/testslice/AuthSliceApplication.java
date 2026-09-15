package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 인증 통합 테스트가 띄우는 애플리케이션 — <b>인증·사용자·공통만</b> 올린다.
 *
 * <h2>🔴 왜 이게 지금 필요한가</h2>
 *
 * 인증은 이 저장소에서 배포에 가장 많이 나간 모듈인데 <b>실제 DB 위에서 도는 테스트가 하나도
 * 없었다.</b> 21개 테스트가 전부 Mockito 로 리포지토리를 흉내 낸다. 그러면 확인할 수 없는 것이
 * 있다 — 트랜잭션이 되돌려질 때 무엇이 남고 무엇이 사라지는지다.
 *
 * <p>S15P21E201-421(로그인 연속 실패 차단)이 정확히 그 자리에 걸린다. 실패 횟수를 세는 코드가
 * 예외를 던지는 트랜잭션 안에 있으면 센 것까지 같이 사라져서 기능이 한 번도 동작하지 않는데,
 * 응답은 정상이라 Mockito 테스트는 전부 초록이다. 그래서 이 슬라이스를 만들었다.
 *
 * <p>🔴 {@code com.gabolle.backend} 밖에 두는 것이 필수다. 테스트 소스도 클래스패스에 올라가므로
 * 안에 두면 본 애플리케이션 스캔에 걸려 아래 {@code @EnableJpaRepositories} 가 {@code no-db}
 * 프로필에서도 켜지고 {@code contextLoads} 가 죽는다. 같은 사고가
 * {@code RecommendationSliceApplication} 주석에 기록돼 있다.
 */
// 🔴 S15P21E201-317 — trip 을 더했다. 가입(LocalAuthService)이 익명 여행을 승계하려면
//    AnonymousTripClaimService(trip.application)·JpaTripRepository(trip.infra) 빈이 있어야
//    한다. 아래 @EnableJpaRepositories 도 trip.infra 를 더해야 그 빈이 요구하는
//    TripJpaRepository 등이 실제로 만들어진다 — 안 더하면 이 슬라이스의 컨텍스트가
//    "그런 빈 없음" 으로 못 뜬다.
// 🔴 S15P21E201-137 — place 를 더했다. 위에서 trip 을 더한 순간 그 패키지의 컨트롤러가 전부
//    함께 올라오는데, 그중 TripFacetViewController(S15P21E201-475)가 place 쪽 서비스를 필수로
//    요구한다. 그것이 없으면 이 슬라이스의 컨텍스트가 통째로 못 뜨고 인증 테스트 52개가
//    한꺼번에 빨개진다 — 실제로 그렇게 back/dev 가 빨간 채로 있었다.
//
//    🔴 컨트롤러 쪽에 조건을 걸어 이 슬라이스에서만 빠지게 하는 방법은 쓰지 않는다. 그러면
//    운영에서도 조용히 빠질 수 있는 자리가 하나 늘고, 그 실패는 아무 검사도 못 잡는다.
//    ItinerarySliceApplication 이 같은 갈림길에서 같은 판단을 먼저 적어 뒀다.
//
//    남는 교훈은 스캔 목록에 패키지를 더하는 것이 그 패키지 하나를 더하는 일이 아니라는
//    것이다. 그 패키지가 기대는 곳까지 함께 따라온다.
@SpringBootApplication(scanBasePackages = {
		"com.gabolle.backend.common",
		"com.gabolle.backend.auth",
		"com.gabolle.backend.user",
		"com.gabolle.backend.trip",
		"com.gabolle.backend.place",
		// 🔴 S15P21E201-440 — event 를 더했다. 위의 trip 에 SpendProfileService(S15P21E201-709)가
		//    들어오면서 그것이 event 쪽 EventIngestService 를 필수로 요구한다. place 를 더한
		//    것과 똑같은 모양의 사고이고 오늘만 세 번째다.
		//
		//    🔴 이 목록이 계속 길어지는 것 자체가 신호다. trip 패키지에 빈을 하나 더할 때마다
		//    이 슬라이스가 따라 넓어져야 하는데, 그러면 "인증만 올린다" 는 이 슬라이스의 뜻이
		//    점점 사라진다. 언젠가는 trip 을 여기서 빼고 인증이 실제로 필요로 하는 것만
		//    남기는 쪽을 봐야 한다 — 지금은 back/dev 를 세워 두는 것이 먼저라 미룬다.
		"com.gabolle.backend.event",
		// 🔴 S15P21E201-978 — AccountDeletionService 가 생성자로 StorageCleanupService(story.
		// application)를 요구하게 됐다. event 를 더했을 때와 같은 모양의 사고다. story 전체가
		// 아니라 application·storage 두 패키지만 더한다 — presentation(컨트롤러)은 이 슬라이스가
		// 필요로 하지 않는다. application 을 더하면 그 패키지의 다른 서비스(StoryService 등)도
		// 함께 빈으로 올라오는데, 그것들이 쓰는 저장소는 전부 story.repository 에 있어 아래
		// @EnableJpaRepositories 에도 그 패키지를 더했다. storage 를 더한 것은
		// StorageCleanupService 가 요구하는 StoragePort 의 기본 구현(LocalFileStorage)이 거기
		// 있어서다 — @Profile({"db","dev"})·@ConditionalOnProperty(기본값 local) 라 이 슬라이스의
		// db 프로필에서 MinIO 없이도 뜬다.
		"com.gabolle.backend.story.application",
		"com.gabolle.backend.story.storage"
})
// 🔴 엔티티는 인증 밖의 것도 올린다. 계정 삭제(S15P21E201-425)가 그 사람의 여행·일정·추천 기록을
//    JPQL 로 지우는데, 엔티티가 이 영속성 단위에 없으면 "그런 엔티티 없다" 로 실행에서 터진다.
//    운영은 GabolleBackendApplication 이 전부 스캔하므로 그쪽에서는 풀린다 — 즉 이 목록이 좁으면
//    테스트만 실패하고 운영은 멀쩡한, 방향이 반대인 거짓 경보가 난다.
//
//    빈(@Service 등)은 여전히 안 올린다. scanBasePackages 는 그대로라서 남의 미완성 코드에
//    인질로 잡히지 않는다. 여기서 넓힌 것은 표 매핑뿐이다.
@EntityScan(basePackages = {
		"com.gabolle.backend.auth.domain",
		"com.gabolle.backend.user.domain",
		"com.gabolle.backend.trip.infra",
		"com.gabolle.backend.itinerary.infra",
		"com.gabolle.backend.recommendation.domain",
		"com.gabolle.backend.event.domain",
		// 🔴 2026-09-07 (S15P21E201-188) — 계정 삭제 미리보기가 story 도 JPQL 로 센다
		// (AccountDeletionService.preview). 위 문단이 경고한 바로 그 실패가 실제로 났다 —
		// CI 가 UnknownEntityException 으로 잡았다.
		"com.gabolle.backend.story.domain",
		// S15P21E201-137 — 위 scanBasePackages 에 place 를 더하면서 그 빈들이 쓰는 표 매핑도
		// 함께 올린다. 매핑이 없으면 빈은 만들어지고 첫 질의에서 터진다.
		"com.gabolle.backend.place.domain",
		// 🔴 2026-09-11 (S15P21E201-549) — feed·preference 를 더했다. 위 문단이 경고한 실패가
		// <b>두 번째로</b> 났다. 계정 삭제와 개인화 초기화가 취향 벡터(user_taste_vector·
		// user_taste_weight)와 미리 만든 피드(feed_build·user_feed·community_feed)를 JPQL 로
		// 지우는데, 그 엔티티가 이 영속성 단위에 없어서
		// "Could not resolve root entity 'UserFeedEntry'" 로 실행에서 터졌다 — CI 가 잡았다.
		//
		// 🔴 로컬에서는 이 실패가 안 보인다. Docker 가 없는 PC 에서는 Postgres 검사가 통째로
		// 건너뛰어지므로, 이 목록이 좁다는 사실은 CI 에 올려야 비로소 드러난다.
		"com.gabolle.backend.feed.domain",
		"com.gabolle.backend.preference.domain"
})
@EnableJpaRepositories(basePackages = {
		"com.gabolle.backend.auth.repository",
		"com.gabolle.backend.user.repository",
		"com.gabolle.backend.trip.infra",
		"com.gabolle.backend.place.repository",
		"com.gabolle.backend.event.repository",
		// 🔴 S15P21E201-160 — AnalyticsQueryService(event.application)가 생성자로
		// RecommendationJobRepository 를 요구한다. event 를 스캔하는 순간 그 빈도 같이
		// 요구된다 — place·trip 을 더했을 때와 같은 모양의 사고다. 위 @EntityScan 에는
		// recommendation.domain 이 이미 있었지만, 저장소는 @EnableJpaRepositories 가
		// 따로 정한다.
		"com.gabolle.backend.recommendation.repository",
		// 🔴 S15P21E201-978 — 위 scanBasePackages 에 story.application 을 더하면서 그 서비스들이
		// 쓰는 저장소(StorageCleanupRepository·UploadedImageRepository·UserBlockRepository 등)도
		// 함께 필요해졌다. 엔티티 매핑(story.domain)은 이미 있었다.
		"com.gabolle.backend.story.repository"
})
public class AuthSliceApplication {
}
