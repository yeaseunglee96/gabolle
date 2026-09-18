package com.gabolle.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 인가 정책 감사가 {@code path=} 로 쓴 매핑도 본다 — S15P21E201-836.
 *
 * <h2>🔴 무엇을 막는 검사인가</h2>
 * {@code @PostMapping(path = "/x")} 와 {@code @PostMapping("/x")} 는 스프링에게 완전히 같다
 * ({@code @AliasFor}). 그런데 애너테이션을 직접 읽는 감사에게는 달랐다 — {@code value()} 만
 * 읽었기 때문에 {@code path=} 로 쓴 경로는 <b>메서드 경로가 없는 것처럼 보여 클래스 경로로
 * 오인</b>됐다.
 *
 * <p>오인 자체가 문제가 아니다. 오인한 이름이 정책 표에 없으면 빨개지니까. 위험한 것은
 * <b>클래스 경로가 이미 표에 있을 때</b>다 — 그러면 정책을 한 줄도 안 적은 새 경로가 감사를
 * 조용히 통과한다. 이 저장소에서 {@code @RequestMapping("/api/v1/auth")} 처럼 표에 있는
 * 컨트롤러가 여럿이라 실제로 일어날 수 있는 일이었다.
 *
 * <p>{@code S15P21E201-833} 에서 실제로 밟았다. 애플 착지 경로를 {@code path=} 로 썼고 감사는
 * 그것을 {@code POST /api/v1/auth/oauth/apple} 로 봤다. 그때는 그 이름이 표에 없어 빨개졌지만,
 * 한 마디만 달랐으면 아무 일도 안 일어났을 것이다.
 *
 * <h2>왜 대역 클래스에 {@code @RestController} 를 안 붙이나</h2>
 * 붙이면 이 클래스가 <b>감사 대상에 들어간다.</b> 클래스 경로 스캔이 {@code com.gabolle.backend}
 * 아래의 {@code @RestController} 를 전부 찾고, 테스트 클래스도 같은 클래스 경로에 있다. 그러면
 * 이 대역의 다섯 경로가 정책 표를 요구하고 "로그인 없이 열린 경로 수" 까지 흔든다 —
 * <b>검사를 위해 감사 대상을 늘리는 것</b>이라 방향이 거꾸로다. 그래서 애너테이션만 달고
 * {@code RouteAuthorizationRegistryTest.routesOf} 를 직접 부른다.
 */
class RouteDiscoveryReadsPathAttributeTest {

	@Test
	@DisplayName("🔴 path= 로 쓴 다섯 매핑 전부 감사에 잡힌다 — value= 로 쓴 것과 같은 이름으로")
	void pathAttributeIsDiscoveredForEveryVerb() {
		assertThat(RouteAuthorizationRegistryTest.routesOf(PathStyleController.class))
				.containsExactlyInAnyOrder(
						"GET /api/v1/audit-fixture/get",
						"POST /api/v1/audit-fixture/post",
						"PUT /api/v1/audit-fixture/put",
						"PATCH /api/v1/audit-fixture/patch",
						"DELETE /api/v1/audit-fixture/delete");
	}

	@Test
	@DisplayName("value= 로 쓴 것과 결과가 같다 — 두 표기가 같은 경로라는 것이 이 검사의 요점")
	void valueAndPathProduceTheSameRoutes() {
		assertThat(RouteAuthorizationRegistryTest.routesOf(PathStyleController.class))
				.isEqualTo(RouteAuthorizationRegistryTest.routesOf(ValueStyleController.class));
	}

	/**
	 * 🔴 이쪽은 <b>원래도 잘 돌고 있었다.</b> 부수기 실험에서 이 검사만 초록으로 남았다 —
	 * 클래스 경로는 {@code AnnotatedElementUtils.findMergedAnnotation} 으로 읽고 그쪽은
	 * {@code @AliasFor} 를 실제로 합쳐 준다. 구멍은 순수 반사로 읽는 메서드 매핑 쪽에만 있었다.
	 *
	 * <p>그래도 이 검사를 남긴다. "클래스 레벨은 괜찮다" 는 사실이 코드 어디에도 안 적혀 있으면,
	 * 다음에 읽는 방식을 바꾸는 사람이 그것을 모르고 깰 수 있다.
	 */
	@Test
	@DisplayName("클래스 경로를 path= 로 써도 잡힌다 — 이쪽은 원래도 됐다(별칭을 합쳐 읽는다)")
	void classLevelPathAttributeIsDiscovered() {
		assertThat(RouteAuthorizationRegistryTest.routesOf(ClassLevelPathController.class))
				.containsExactly("GET /api/v1/audit-fixture/nested");
	}

	@RequestMapping("/api/v1/audit-fixture")
	private static final class PathStyleController {

		@GetMapping(path = "/get")
		void get() {
		}

		@PostMapping(path = "/post")
		void post() {
		}

		@PutMapping(path = "/put")
		void put() {
		}

		@PatchMapping(path = "/patch")
		void patch() {
		}

		@DeleteMapping(path = "/delete")
		void delete() {
		}
	}

	@RequestMapping("/api/v1/audit-fixture")
	private static final class ValueStyleController {

		@GetMapping("/get")
		void get() {
		}

		@PostMapping("/post")
		void post() {
		}

		@PutMapping("/put")
		void put() {
		}

		@PatchMapping("/patch")
		void patch() {
		}

		@DeleteMapping("/delete")
		void delete() {
		}
	}

	@RequestMapping(path = "/api/v1/audit-fixture")
	private static final class ClassLevelPathController {

		@GetMapping("/nested")
		void nested() {
		}
	}
}
