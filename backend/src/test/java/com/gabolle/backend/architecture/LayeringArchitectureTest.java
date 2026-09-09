package com.gabolle.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 계층 규칙을 리뷰가 아니라 빌드가 재게 한다 — S15P21E201-571.
 *
 * <p>`backend/README.md` 는 이미 "각 기능의 application/domain 경계를 유지합니다.
 * Controller 에서 다른 기능의 Repository 를 직접 호출하지 않습니다" 라고 적고 있었지만,
 * 이 저장소에는 그것을 <b>재는 코드가 없었다.</b> 문서만 있고 검사가 없는 규칙은
 * 리뷰어가 매번 눈으로 보지 않으면 지켜지는지 아무도 모른다.
 *
 * <h2>🔴 여기서 "도메인이 JPA 를 import 하면 안 된다" 를 재지 않는다 — 의도적이다</h2>
 * 이 티켓의 "하지 않는 일" 절이 <b>도메인 모델과 JPA 엔티티를 분리하지 않기로</b> 이미
 * 정했다 — {@code @Entity} 를 도메인 엔티티로 겸용한다(매핑 코드가 2배가 되는 것을 피하려는
 * 선택). 실제로 {@code recommendation.domain.RecommendationJob} 등은 지금도
 * {@code jakarta.persistence} 를 그대로 쓴다. 여기서 "domain 은 JPA 를 import 하면 실패"
 * 를 걸면 그 결정 자체를 위반으로 규정하게 되어, 팀이 이미 정한 것과 이 테스트가 서로
 * 다른 말을 하게 된다. 그래서 이 규칙은 <b>일부러 안 걸었다</b> — 필요해지면 도메인·엔티티
 * 분리를 먼저 팀이 다시 정해야 한다.
 *
 * <h2>🔴 여기서 "도메인이 다른 기능의 도메인을 import 하면 안 된다" 도 안 걸었다</h2>
 * 지금도 {@code auth.domain} 이 {@code user.domain.AppUser} 를(계정과 회원이 나뉘어
 * 있어서 자연스러운 참조), {@code story.domain} 이 {@code moderation.domain} 을(기록에
 * 검수 상태가 붙는 게 당연해서) 그대로 쓴다. 걸면 지금 당장 둘이 실패하고, 예외를
 * 허용목록으로 만들면 그 목록 자체가 매번 늘어나는 별도 유지 부담이 된다. 이 경계를
 * 정말 지킬지, 어떤 조합만 허용할지는 <b>팀이 먼저 정할 일</b>이지 이 테스트가 조용히
 * 정할 일이 아니다.
 *
 * <p>아래 둘은 지금 저장소에 <b>위반이 0건</b>이라 안전하게 걸 수 있는 것만 남겼다.
 */
@AnalyzeClasses(packages = "com.gabolle.backend", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeringArchitectureTest {

	/**
	 * Controller 는 응용 계층을 거치지 않고 저장소·JPA 구현으로 바로 내려가지 않는다.
	 *
	 * <p>README 가 명시한 규칙 그대로다. 이걸 어기면 트랜잭션 경계·인가 검사·멱등 처리 같은
	 * 응용 계층의 책임을 컨트롤러가 대신 떠맡게 되고, {@link com.gabolle.backend.event
	 * .application.EventIngestService} 클래스 주석이 적어 둔 것과 같은 종류의 사고
	 * (다른 계층에 있어야 할 규칙이 흩어져서 한쪽만 지켜지는 것)가 반복된다.
	 */
	@ArchTest
	static final ArchRule presentationDoesNotReachIntoInfraOrRepository = noClasses()
			.that().resideInAPackage("..presentation..")
			.should().dependOnClassesThat().resideInAnyPackage("..infra..", "..repository..")
			.because("Controller 는 저장소·JPA 구현을 직접 부르지 않는다 — 응용 계층을 거친다 (backend/README.md)");

	/** 도메인은 표현 계층을 모른다 — 역방향 의존은 계층을 나눈 의미 자체를 없앤다. */
	@ArchTest
	static final ArchRule domainDoesNotDependOnPresentation = noClasses()
			.that().resideInAPackage("..domain..")
			.should().dependOnClassesThat().resideInAPackage("..presentation..")
			.because("도메인은 그 값을 누가 어떤 화면·API 로 보여주는지 몰라야 한다");
}
