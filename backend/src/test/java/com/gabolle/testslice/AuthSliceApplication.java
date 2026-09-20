package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 인증 통합 테스트가 띄우는 애플리케이션 — 인증·사용자·공통만 올린다.
 *
 * <p>{@code com.gabolle.backend} 밖에 두는 것이 필수다. 테스트 소스도 클래스패스에 올라가므로
 * 안에 두면 본 애플리케이션 스캔에 걸려 아래 {@code @EnableJpaRepositories} 가 {@code no-db}
 * 프로필에서도 켜지고 {@code contextLoads} 가 죽는다.
 *
 * <p>스캔 목록에 패키지를 더하는 것은 그 패키지 하나를 더하는 일이 아니다 — 그것이 기대는 곳까지
 * 함께 따라온다. 아래 목록이 길어진 것이 전부 그 결과다.
 */
// trip: 가입(LocalAuthService)이 익명 여행을 승계하려면 AnonymousTripClaimService·
//       JpaTripRepository 빈이 있어야 한다.
// place: trip 을 더하면 그 패키지의 컨트롤러가 전부 따라 올라오는데 TripFacetViewController 가
//        place 서비스를 필수로 요구한다. 컨트롤러에 조건을 걸어 이 슬라이스에서만 빼는 방법은
//        쓰지 않는다 — 운영에서도 조용히 빠질 수 있는 자리가 늘고 그 실패는 아무도 못 잡는다.
@SpringBootApplication(scanBasePackages = {
		"com.gabolle.backend.common",
		"com.gabolle.backend.auth",
		"com.gabolle.backend.user",
		"com.gabolle.backend.trip",
		"com.gabolle.backend.place",
		// trip 의 SpendProfileService 가 event 의 EventIngestService 를 요구한다.
		//
		// 이 목록이 계속 길어지는 것 자체가 신호다. trip 에 빈을 더할 때마다 이 슬라이스가 따라
		// 넓어지면 "인증만 올린다" 는 뜻이 사라진다. 언젠가 trip 을 빼고 인증이 실제로 쓰는 것만
		// 남겨야 한다.
		"com.gabolle.backend.event",
		// AccountDeletionService 가 StorageCleanupService(story.application)를 요구한다.
		// presentation 은 필요 없어 두 패키지만 더한다. storage 는 StorageCleanupService 가 쓰는
		// StoragePort 의 기본 구현(LocalFileStorage)이 있는 곳이다 — 기본값이 local 이라 이
		// 슬라이스의 db 프로필에서 MinIO 없이 뜬다.
		"com.gabolle.backend.story.application",
		"com.gabolle.backend.story.storage"
})
// 엔티티는 인증 밖의 것도 올린다. 계정 삭제가 그 사람의 여행·일정·추천 기록을 JPQL 로 지우는데,
// 엔티티가 이 영속성 단위에 없으면 실행에서 "그런 엔티티 없다" 로 터진다. 운영은
// GabolleBackendApplication 이 전부 스캔하므로 풀린다 — 이 목록이 좁으면 테스트만 실패하고
// 운영은 멀쩡한, 방향이 반대인 거짓 경보가 난다.
//
// 빈은 여전히 안 올린다. 여기서 넓힌 것은 표 매핑뿐이다.
//
// 로컬에서는 이 실패가 안 보인다 — Docker 가 없는 PC 에서는 Postgres 검사가 통째로
// 건너뛰어지므로, 목록이 좁다는 사실은 CI 에 올려야 드러난다.
@EntityScan(basePackages = {
		"com.gabolle.backend.auth.domain",
		"com.gabolle.backend.user.domain",
		"com.gabolle.backend.trip.infra",
		"com.gabolle.backend.itinerary.infra",
		"com.gabolle.backend.recommendation.domain",
		"com.gabolle.backend.event.domain",
		// 계정 삭제 미리보기가 story 를 JPQL 로 센다.
		"com.gabolle.backend.story.domain",
		// place 빈들이 쓰는 표 매핑. 없으면 빈은 만들어지고 첫 질의에서 터진다.
		"com.gabolle.backend.place.domain",
		// 계정 삭제·개인화 초기화가 취향 벡터와 미리 만든 피드를 JPQL 로 지운다.
		"com.gabolle.backend.feed.domain",
		"com.gabolle.backend.preference.domain",
		// 탈퇴가 ON DELETE CASCADE 에 기대지 않고 직접 지우는 표들
		// (AccountDeletionService.USER_OWNED_ROWS). AccountDeletionOwnedRowsTest 가 DB 없이
		// 먼저 잡는다.
		"com.gabolle.backend.collection.domain",
		"com.gabolle.backend.menuscan.domain",
		// 탈퇴가 DishImageUsage 를 직접 지운다. 같은 꾸러미의 DishImage·DishDescription 은
		// 사람을 안 가리키지만 꾸러미 단위로 올리므로 함께 들어온다.
		"com.gabolle.backend.dish.domain",
		"com.gabolle.backend.review.domain",
		"com.gabolle.backend.share.domain"
})
@EnableJpaRepositories(basePackages = {
		"com.gabolle.backend.auth.repository",
		"com.gabolle.backend.user.repository",
		"com.gabolle.backend.trip.infra",
		"com.gabolle.backend.place.repository",
		"com.gabolle.backend.event.repository",
		// AnalyticsQueryService(event.application)가 RecommendationJobRepository 를 요구한다.
		// 엔티티 매핑과 달리 저장소는 여기서 따로 정해야 한다.
		"com.gabolle.backend.recommendation.repository",
		// story.application 의 서비스들이 쓰는 저장소(StorageCleanupRepository·
		// UploadedImageRepository·UserBlockRepository 등).
		"com.gabolle.backend.story.repository"
})
public class AuthSliceApplication {
}
