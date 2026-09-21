package com.gabolle.backend.place;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import com.gabolle.backend.place.api.PlaceExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 애너테이션이 붙어 있다는 것만 확인한다. 슬라이스 컨텍스트에는 {@code AuthExceptionHandler} 가
 * 없어서 실제 advice 순서는 여기서 잴 수 없다 — 그건 배포에서 없는 UUID 조회가 404 로 오는지로 본다.
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
