package com.gabolle.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 계층 규칙을 리뷰가 아니라 빌드가 재게 한다. 아래 둘은 위반이 0건이라 안전하게 걸 수 있는 것만 남긴
 * 것이다.
 *
 * <p>"도메인이 JPA 를 import 하면 안 된다" 는 일부러 안 건다 — 팀이 {@code @Entity} 를 도메인 엔티티로
 * 겸용하기로 정했으므로, 걸면 그 결정 자체를 위반으로 규정하게 된다.
 *
 * <p>"도메인이 다른 기능의 도메인을 import 하면 안 된다" 도 안 건다 — 지금 당장 둘이 실패하고,
 * 허용목록을 두면 그 목록이 별도 유지 부담이 된다. 이 경계는 팀이 먼저 정할 일이다.
 */
@AnalyzeClasses(packages = "com.gabolle.backend", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeringArchitectureTest {

	/**
	 * Controller 는 응용 계층을 거치지 않고 저장소·JPA 구현으로 바로 내려가지 않는다. 어기면 트랜잭션
	 * 경계·인가 검사·멱등 처리 같은 응용 계층의 책임을 컨트롤러가 대신 떠맡게 된다.
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
