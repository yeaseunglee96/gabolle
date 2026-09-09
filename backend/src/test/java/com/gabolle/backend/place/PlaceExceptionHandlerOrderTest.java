package com.gabolle.backend.place;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import com.gabolle.backend.place.api.PlaceExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 장소 예외 처리기가 인증 모듈의 전역 처리기보다 먼저 잡히는가.
 *
 * <h2>🔴 왜 이런 모양의 테스트인가</h2>
 *
 * 인증 모듈의 {@code AuthExceptionHandler} 는 범위 제한이 없고 {@code Exception} 까지 잡는다.
 * Spring 은 적용 가능한 advice 를 순회하다가 그 예외를 처리할 메서드가 있는 <b>첫 번째</b> advice
 * 에서 멈추는데, 둘 다 순서가 없으면 승자가 컴포넌트 스캔 등록 순서로 정해진다. 그러면 장소 404 가
 * 인증 모듈의 {@code 500 INTERNAL_ERROR} 로 나간다.
 *
 * <p>이 상황은 <b>슬라이스 테스트로 재현할 수 없다.</b> {@code PlaceSliceApplication} 이
 * {@code common} 과 {@code place} 만 스캔해서 {@code AuthExceptionHandler} 가 컨텍스트에 아예
 * 없기 때문이다. 그래서 두 처리기가 함께 있는 상태를 실제로 만들려면 애플리케이션 전체를 띄워야
 * 하는데, 그러면 이 테스트가 남의 미완성 코드에 인질로 잡힌다 —
 * {@code RecommendationSliceApplication} 주석이 그 사고를 기록해 뒀다.
 *
 * <p>그래서 여기서는 <b>애너테이션이 붙어 있다는 것만</b> 못 박는다. 이것이 증명하는 것은 "우선순위가
 * 명시돼 있다" 이지 "실제 순서가 이렇다" 가 아니다. 그 한계를 알고 두는 테스트다 — 없는 것보다는
 * 낫다. 누군가 이 애너테이션을 지우면 빨개지고, 그것이 이 테스트의 전부다.
 *
 * <p>실제 순서는 배포에서 {@code GET /api/v1/places/<없는 UUID>} 가 404 로 오는지로 확인한다.
 */
class PlaceExceptionHandlerOrderTest {

	@Test
	@DisplayName("🔴 장소 예외 처리기에 최우선 순위가 명시돼 있다 — 지우면 404 가 500 이 된다")
	void placeHandlerDeclaresHighestPrecedence() {
		Order order = PlaceExceptionHandler.class.getAnnotation(Order.class);

		assertThat(order)
				.as("@Order 가 없으면 인증 모듈의 전역 처리기와 순서가 스캔 등록 순서로 정해진다")
				.isNotNull();
		assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
	}
}
