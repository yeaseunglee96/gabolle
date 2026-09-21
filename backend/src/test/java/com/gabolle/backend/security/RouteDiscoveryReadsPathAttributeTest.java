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
 * 인가 정책 감사가 {@code path=} 로 쓴 매핑도 보는지 확인한다. {@code value()} 만 읽으면 {@code path=}
 * 로 쓴 경로가 메서드 경로 없는 것처럼 보여 클래스 경로로 오인되고, 그 클래스 경로가 이미 정책 표에
 * 있으면 정책을 한 줄도 안 적은 새 경로가 감사를 조용히 통과한다.
 *
 * <p>대역 클래스에 {@code @RestController} 를 붙이지 않는다. 붙이면 테스트 클래스도 같은 클래스
 * 경로에 있어 감사 대상에 들어가고, 이 대역의 경로들이 정책 표를 요구하며 열린 경로 수까지 흔든다.
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
	 * 클래스 경로는 {@code AnnotatedElementUtils.findMergedAnnotation} 으로 읽어 {@code @AliasFor} 가
	 * 이미 합쳐지므로 원래도 됐다. 읽는 방식을 바꾸는 사람이 그것을 모르고 깨지 않도록 남겨 둔다.
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
